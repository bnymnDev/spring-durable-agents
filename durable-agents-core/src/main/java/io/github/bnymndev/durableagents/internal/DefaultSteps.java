package io.github.bnymndev.durableagents.internal;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;

import io.github.bnymndev.durableagents.ApprovalDecision;
import io.github.bnymndev.durableagents.ApprovalRejected;
import io.github.bnymndev.durableagents.ApprovalStepBuilder;
import io.github.bnymndev.durableagents.ApprovalTimeoutPolicy;
import io.github.bnymndev.durableagents.NonDeterministicReplay;
import io.github.bnymndev.durableagents.RunCancelled;
import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunSuspended;
import io.github.bnymndev.durableagents.StepBuilder;
import io.github.bnymndev.durableagents.StepContext;
import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.StepStatus;
import io.github.bnymndev.durableagents.StepTimeout;
import io.github.bnymndev.durableagents.Steps;
import io.github.bnymndev.durableagents.TypeRef;
import io.github.bnymndev.durableagents.event.ApprovalDecidedEvent;
import io.github.bnymndev.durableagents.event.ApprovalRequestedEvent;
import io.github.bnymndev.durableagents.event.StepCompletedEvent;
import io.github.bnymndev.durableagents.spi.ApprovalRecord;
import io.github.bnymndev.durableagents.spi.StepExecution;
import io.github.bnymndev.durableagents.spi.StepListener;
import io.github.bnymndev.durableagents.spi.StepRecord;

/**
 * The {@link Steps} implementation for one execution of a run. Not thread-safe by design: a run
 * executes on one thread at a time, and a timed-out step hands over to a worker thread that the
 * caller waits for.
 */
public final class DefaultSteps implements Steps {

	private static final Log logger = LogFactory.getLog(DefaultSteps.class);

	private final RunId runId;

	private final String agentName;

	private final @Nullable String correlationId;

	private final RunContext ctx;

	private final ActiveRun activeRun;

	private final Map<String, Integer> counters = new HashMap<>();

	private final Map<String, StepRecord> history = new HashMap<>();

	private final List<String> recordedTopLevel = new ArrayList<>();

	private final Deque<Frame> stack = new ArrayDeque<>();

	private final List<Compensation> compensations = new ArrayList<>();

	private int replayPosition;

	private int nextSequence;

	DefaultSteps(RunId runId, String agentName, @Nullable String correlationId, RunContext ctx, ActiveRun activeRun) {
		this.runId = runId;
		this.agentName = agentName;
		this.correlationId = correlationId;
		this.ctx = ctx;
		this.activeRun = activeRun;
		List<StepRecord> recorded = ctx.stepStore().findByRun(runId);
		for (StepRecord step : recorded) {
			this.history.put(step.stepKey(), step);
			this.nextSequence = Math.max(this.nextSequence, step.sequence() + 1);
			if (step.parentKey() == null && step.kind() != StepKind.COMPENSATION && step.kind() != StepKind.SIDE_EFFECT) {
				this.recordedTopLevel.add(step.stepName());
			}
		}
	}

	// ---------------------------------------------------------------- Steps API

	@Override
	public RunId runId() {
		return this.runId;
	}

	@Override
	public <T extends @Nullable Object> T run(String name, Supplier<T> supplier) {
		return execute(StepSpec.named(name), supplier);
	}

	@Override
	public <T extends @Nullable Object> T run(String name, Class<T> type, Supplier<T> supplier) {
		return execute(StepSpec.named(name).type(type), supplier);
	}

	@Override
	public <T extends @Nullable Object> T run(String name, TypeRef<T> type, Supplier<T> supplier) {
		return execute(StepSpec.named(name).type(type.type()), supplier);
	}

	@Override
	public void run(String name, Runnable action) {
		execute(StepSpec.named(name).type(Void.class), () -> {
			action.run();
			return null;
		});
	}

	@Override
	public <T extends @Nullable Object> T llm(String name, Class<T> type, Supplier<T> supplier) {
		return execute(StepSpec.named(name).type(type).kind(StepKind.LLM), supplier);
	}

	@Override
	public <T extends @Nullable Object> T llm(String name, TypeRef<T> type, Supplier<T> supplier) {
		return execute(StepSpec.named(name).type(type.type()).kind(StepKind.LLM), supplier);
	}

	@Override
	public <T extends @Nullable Object> T sideEffect(String key, Class<T> type, Supplier<T> supplier) {
		return execute(sideEffectSpec(key).type(type), supplier);
	}

	@Override
	public <T extends @Nullable Object> T sideEffect(String key, Supplier<T> supplier) {
		return execute(sideEffectSpec(key), supplier);
	}

	private static StepSpec sideEffectSpec(String key) {
		if (key.isBlank()) {
			throw new IllegalArgumentException("sideEffect key must not be blank");
		}
		return new StepSpec("sideEffect", "sideEffect:" + key, null, StepKind.SIDE_EFFECT, 1, RetryPolicy.NONE, null, null,
				false, null, null);
	}

	@Override
	public StepBuilder retry(int maxAttempts, Duration backoff) {
		return new Builder().retry(maxAttempts, backoff);
	}

	@Override
	public StepBuilder timeout(Duration timeout) {
		return new Builder().timeout(timeout);
	}

	@Override
	public StepBuilder version(int version) {
		return new Builder().version(version);
	}

	@Override
	public StepBuilder compensate(Runnable undo) {
		return new Builder().compensate(undo);
	}

	@Override
	public ApprovalStepBuilder approval(String role, Duration timeout) {
		return new ApprovalBuilder(role, timeout);
	}

	@Override
	public ApprovalStepBuilder approval(String role) {
		return new ApprovalBuilder(role, this.ctx.settings().defaultApprovalTimeout());
	}

	@Override
	public void heartbeat() {
		this.activeRun.heartbeat();
	}

	// ---------------------------------------------------------------- engine-facing API

	public String agentName() {
		return this.agentName;
	}

	/** The context of the innermost step executing right now, if any. */
	public Optional<StepContext> currentContext() {
		Frame frame = this.stack.peek();
		return Optional.ofNullable(frame).map(FrameContext::new);
	}

	/** The key of the innermost step executing right now, if any. */
	public Optional<String> currentStepKey() {
		Frame frame = this.stack.peek();
		return Optional.ofNullable(frame).map((f) -> f.key);
	}

	/** Attaches LLM usage to the step executing right now (used by the Spring AI advisor). */
	public void recordLlmUsage(@Nullable String model, @Nullable Long tokensIn, @Nullable Long tokensOut) {
		Frame frame = this.stack.peek();
		if (frame == null) {
			return;
		}
		if (model != null) {
			frame.model = model;
		}
		if (tokensIn != null) {
			frame.tokensIn = (frame.tokensIn == null ? 0 : frame.tokensIn) + tokensIn;
		}
		if (tokensOut != null) {
			frame.tokensOut = (frame.tokensOut == null ? 0 : frame.tokensOut) + tokensOut;
		}
	}

	List<Compensation> compensations() {
		return this.compensations;
	}

	/** Executes one step according to {@code spec}. This is the single entry point for all step kinds. */
	public <T extends @Nullable Object> T execute(StepSpec spec, Supplier<T> supplier) {
		this.activeRun.checkCancelled();
		Frame parentFrame = this.stack.peek();
		String parentKey = (parentFrame != null) ? parentFrame.key : null;
		String key = (spec.fixedKey() != null) ? this.runId + ":" + spec.fixedKey() : nextKey(parentKey, spec.name());
		StepKind kind = (spec.kind() != null) ? spec.kind() : (parentKey == null ? StepKind.STEP : StepKind.CHILD);
		if (parentKey == null && spec.replayGuard() && this.ctx.settings().strictReplay()) {
			guardReplay(spec.name());
		}
		StepRecord existing = this.history.get(key);
		if (existing != null && existing.status() == StepStatus.COMPLETED && existing.stepVersion() == spec.version()) {
			T value = decode(existing, spec.type());
			registerCompensation(spec, key);
			notifyReplayed(existing);
			return value;
		}
		if (spec.approval() != null) {
			return executeApproval(spec, key, parentKey, existing, supplier);
		}
		int sequence = (existing != null) ? existing.sequence() : this.nextSequence++;
		int attempts = (existing != null) ? existing.attempt() : 0;
		return attempt(spec, key, parentKey, kind, sequence, attempts, supplier);
	}

	private <T extends @Nullable Object> T attempt(StepSpec spec, String key, @Nullable String parentKey, StepKind kind,
			int sequence, int attemptsSoFar, Supplier<T> supplier) {
		int attempt = attemptsSoFar;
		while (true) {
			attempt++;
			Instant started = this.ctx.clock().instant();
			Frame frame = new Frame(key, spec.name(), attempt);
			StepExecution exec = new StepExecution(this.runId, this.agentName, key, spec.name(), kind, parentKey, attempt,
					started, null, null, null);
			this.stack.push(frame);
			for (StepListener listener : this.ctx.listeners()) {
				listener.onStepStarted(exec);
			}
			try {
				T result = (spec.timeout() != null) ? runWithTimeout(supplier, spec.timeout(), key) : supplier.get();
				Instant finished = this.ctx.clock().instant();
				StepRecord record = new StepRecord(this.runId, key, spec.name(), spec.version(), sequence, StepStatus.COMPLETED,
						attempt, spec.input(), this.ctx.codec().encode(result),
						(result != null) ? result.getClass().getName() : null, null, kind, parentKey, frame.tokensIn,
						frame.tokensOut, frame.model, started, finished);
				this.ctx.stepStore().save(record);
				this.history.put(key, record);
				registerCompensation(spec, key);
				StepExecution done = exec.withUsage(frame.model, frame.tokensIn, frame.tokensOut);
				for (StepListener listener : this.ctx.listeners()) {
					listener.onStepFinished(done, Duration.between(started, finished), null);
				}
				this.ctx.publisher().publishEvent(new StepCompletedEvent(this.runId, this.agentName, key, spec.name(), kind,
						StepStatus.COMPLETED));
				return result;
			}
			catch (RunSuspended | RunCancelled | NonDeterministicReplay controlFlow) {
				throw controlFlow;
			}
			catch (Error crash) {
				// Errors are treated like a process crash: nothing is recorded, the run stays RUNNING
				// with an expired lease and is resumed by the reaper or an explicit resume().
				throw crash;
			}
			catch (Throwable error) {
				Instant finished = this.ctx.clock().instant();
				Duration duration = Duration.between(started, finished);
				for (StepListener listener : this.ctx.listeners()) {
					listener.onStepFinished(exec, duration, error);
				}
				StepRecord failed = new StepRecord(this.runId, key, spec.name(), spec.version(), sequence, StepStatus.FAILED,
						attempt, spec.input(), null, null, describe(error), kind, parentKey, frame.tokensIn, frame.tokensOut,
						frame.model, started, finished);
				this.ctx.stepStore().save(failed);
				this.history.put(key, failed);
				if (spec.retry().shouldRetry(error, attempt)) {
					Duration delay = spec.retry().delayBefore(attempt + 1);
					logger.debug("Step " + key + " failed on attempt " + attempt + ", retrying in " + delay + ": " + error);
					sleep(delay);
					continue;
				}
				this.ctx.publisher().publishEvent(new StepCompletedEvent(this.runId, this.agentName, key, spec.name(), kind,
						StepStatus.FAILED));
				throw sneaky(error);
			}
			finally {
				this.stack.pop();
			}
		}
	}

	private <T extends @Nullable Object> T executeApproval(StepSpec spec, String key, @Nullable String parentKey,
			@Nullable StepRecord existing, Supplier<T> supplier) {
		StepSpec.Approval approval = spec.approval();
		if (approval == null) {
			throw new IllegalStateException("not an approval step");
		}
		Instant now = this.ctx.clock().instant();
		ApprovalRecord request = (existing != null) ? this.ctx.approvalStore().findByStep(this.runId, key).orElse(null) : null;
		if (existing == null || request == null) {
			int sequence = (existing != null) ? existing.sequence() : this.nextSequence++;
			StepRecord pending = new StepRecord(this.runId, key, spec.name(), spec.version(), sequence,
					StepStatus.PENDING_APPROVAL, (existing != null) ? existing.attempt() : 0, spec.input(), null, null, null,
					StepKind.APPROVAL, parentKey, null, null, null, now, null);
			this.ctx.stepStore().save(pending);
			this.history.put(key, pending);
			ApprovalRecord created = new ApprovalRecord(RunId.next().value(), this.runId, key, approval.role(),
					approval.description(), now, now.plus(approval.timeout()), ApprovalDecision.PENDING, null, null, null);
			this.ctx.approvalStore().insert(created);
			this.ctx.publisher().publishEvent(new ApprovalRequestedEvent(this.runId, this.agentName, created.id(), key,
					spec.name(), approval.role(), approval.description(), created.expiresAt(), this.correlationId));
			throw new RunSuspended(this.runId, key, "Waiting for approval by " + approval.role());
		}
		ApprovalDecision decision = request.decision();
		if (decision == ApprovalDecision.PENDING) {
			if (request.expiresAt().isAfter(now)) {
				throw new RunSuspended(this.runId, key, "Waiting for approval by " + approval.role());
			}
			request = request.decided(ApprovalDecision.EXPIRED, now, null, "timeout");
			this.ctx.approvalStore().update(request);
			this.ctx.publisher().publishEvent(new ApprovalDecidedEvent(this.runId, this.agentName, request.id(), key,
					ApprovalDecision.EXPIRED, null, "timeout"));
			decision = ApprovalDecision.EXPIRED;
		}
		boolean proceed = switch (decision) {
			case APPROVED -> true;
			case EXPIRED -> approval.onTimeout() == ApprovalTimeoutPolicy.APPROVE;
			case REJECTED -> false;
			case PENDING -> throw new IllegalStateException("unreachable");
		};
		if (!proceed) {
			ApprovalRejected rejected = new ApprovalRejected(key, decision, request.decidedBy(), request.comment());
			StepRecord failed = new StepRecord(this.runId, key, spec.name(), spec.version(), existing.sequence(),
					StepStatus.FAILED, existing.attempt(), spec.input(), null, null, rejected.getMessage(), StepKind.APPROVAL,
					parentKey, null, null, null, existing.startedAt(), now);
			this.ctx.stepStore().save(failed);
			this.history.put(key, failed);
			this.ctx.publisher().publishEvent(new StepCompletedEvent(this.runId, this.agentName, key, spec.name(),
					StepKind.APPROVAL, StepStatus.FAILED));
			throw rejected;
		}
		return attempt(spec, key, parentKey, StepKind.APPROVAL, existing.sequence(), existing.attempt(), supplier);
	}

	/** Runs the registered compensations in reverse order. Each is a durable step; failures are logged. */
	void runCompensations() {
		for (int i = this.compensations.size() - 1; i >= 0; i--) {
			Compensation compensation = this.compensations.get(i);
			StepSpec spec = new StepSpec("compensate:" + compensation.stepName(), null, Void.class, StepKind.COMPENSATION, 1,
					RetryPolicy.NONE, null, null, false, null, null);
			try {
				execute(spec, () -> {
					compensation.undo().run();
					return null;
				});
			}
			catch (RuntimeException ex) {
				logger.warn("Compensation of step " + compensation.stepKey() + " in run " + this.runId + " failed", ex);
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * Top-level keys are {@code runId:name:n}; keys of nested steps are scoped by their parent,
	 * {@code parentKey/name:n}, so that children of a replayed parent never collide with children of
	 * a later step.
	 */
	private String nextKey(@Nullable String parentKey, String name) {
		String scope = (parentKey == null) ? this.runId + ":" + name : parentKey + "/" + name;
		int n = this.counters.merge(scope, 1, Integer::sum) - 1;
		return scope + ":" + n;
	}

	private void guardReplay(String name) {
		if (this.replayPosition < this.recordedTopLevel.size()) {
			String recorded = this.recordedTopLevel.get(this.replayPosition);
			if (!recorded.equals(name)) {
				throw new NonDeterministicReplay(this.runId, this.replayPosition, recorded, name, this.recordedTopLevel);
			}
		}
		this.replayPosition++;
	}

	private void registerCompensation(StepSpec spec, String key) {
		if (spec.compensate() != null) {
			this.compensations.add(new Compensation(spec.name(), key, spec.compensate()));
		}
	}

	private void notifyReplayed(StepRecord record) {
		StepExecution exec = new StepExecution(this.runId, this.agentName, record.stepKey(), record.stepName(), record.kind(),
				record.parentKey(), record.attempt(), (record.startedAt() != null) ? record.startedAt() : Instant.EPOCH,
				record.model(), record.tokensIn(), record.tokensOut());
		for (StepListener listener : this.ctx.listeners()) {
			listener.onStepReplayed(exec);
		}
	}

	@SuppressWarnings("unchecked")
	private <T extends @Nullable Object> T decode(StepRecord record, @Nullable Type type) {
		if (record.output() == null) {
			return null;
		}
		Type target = type;
		if (target == null || target == Object.class) {
			target = Object.class;
			if (record.outputType() != null) {
				try {
					target = ClassUtils.forName(record.outputType(), DefaultSteps.class.getClassLoader());
				}
				catch (ClassNotFoundException | LinkageError ex) {
					logger.debug("Stored type " + record.outputType() + " of step " + record.stepKey() + " not loadable, decoding as Object");
				}
			}
		}
		return (T) this.ctx.codec().decode(record.output(), target);
	}

	private <T extends @Nullable Object> T runWithTimeout(Supplier<T> supplier, Duration timeout, String key) {
		CompletableFuture<T> future = new CompletableFuture<>();
		Thread worker = Thread.ofVirtual().name("durable-step-" + key).unstarted(() -> {
			try {
				future.complete(StepsHolder.withSteps(this, supplier));
			}
			catch (Throwable ex) {
				future.completeExceptionally(ex);
			}
		});
		worker.start();
		try {
			return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			worker.interrupt();
			throw new StepTimeout(key, timeout);
		}
		catch (ExecutionException ex) {
			throw sneaky(ex.getCause() != null ? ex.getCause() : ex);
		}
		catch (InterruptedException ex) {
			worker.interrupt();
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for step " + key, ex);
		}
	}

	private static void sleep(Duration delay) {
		if (delay.isZero() || delay.isNegative()) {
			return;
		}
		try {
			Thread.sleep(delay);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for retry", ex);
		}
	}

	static String describe(Throwable error) {
		String message = error.getMessage();
		String text = error.getClass().getName() + ((message != null) ? ": " + message : "");
		return (text.length() > 4000) ? text.substring(0, 4000) : text;
	}

	@SuppressWarnings("unchecked")
	static <E extends Throwable> RuntimeException sneaky(Throwable t) throws E {
		throw (E) t;
	}

	// ---------------------------------------------------------------- inner types

	private static final class Frame {

		final String key;

		final String name;

		final int attempt;

		@Nullable String model;

		@Nullable Long tokensIn;

		@Nullable Long tokensOut;

		Frame(String key, String name, int attempt) {
			this.key = key;
			this.name = name;
			this.attempt = attempt;
		}

	}

	record Compensation(String stepName, String stepKey, Runnable undo) {
	}

	private final class FrameContext implements StepContext {

		private final Frame frame;

		FrameContext(Frame frame) {
			this.frame = frame;
		}

		@Override
		public RunId runId() {
			return DefaultSteps.this.runId;
		}

		@Override
		public String stepKey() {
			return this.frame.key;
		}

		@Override
		public String stepName() {
			return this.frame.name;
		}

		@Override
		public int attempt() {
			return this.frame.attempt;
		}

		@Override
		public void heartbeat() {
			DefaultSteps.this.heartbeat();
		}

		@Override
		public <T extends @Nullable Object> T sideEffect(String key, Class<T> type, Supplier<T> supplier) {
			return DefaultSteps.this.sideEffect(key, type, supplier);
		}

	}

	private final class Builder implements StepBuilder {

		private StepSpec spec = StepSpec.named("pending");

		@Override
		public StepBuilder retry(int maxAttempts, Duration backoff) {
			this.spec = this.spec.retry(RetryPolicy.of(maxAttempts, backoff));
			return this;
		}

		@Override
		public StepBuilder retry(int maxAttempts, Duration backoff, double multiplier) {
			this.spec = this.spec.retry(RetryPolicy.of(maxAttempts, backoff).withMultiplier(multiplier));
			return this;
		}

		@Override
		public StepBuilder retryOn(Class<? extends Throwable> type) {
			this.spec = this.spec.retry(this.spec.retry().retryingOn(type));
			return this;
		}

		@Override
		public StepBuilder abortOn(Class<? extends Throwable> type) {
			this.spec = this.spec.retry(this.spec.retry().abortingOn(type));
			return this;
		}

		@Override
		public StepBuilder timeout(Duration timeout) {
			this.spec = this.spec.timeout(timeout);
			return this;
		}

		@Override
		public StepBuilder version(int version) {
			this.spec = this.spec.version(version);
			return this;
		}

		@Override
		public StepBuilder compensate(Runnable undo) {
			this.spec = this.spec.compensate(undo);
			return this;
		}

		private StepSpec named(String name) {
			StepSpec base = StepSpec.named(name);
			return new StepSpec(base.name(), null, this.spec.type(), null, this.spec.version(), this.spec.retry(),
					this.spec.timeout(), this.spec.compensate(), true, null, null);
		}

		@Override
		public <T extends @Nullable Object> T run(String name, Supplier<T> supplier) {
			return execute(named(name), supplier);
		}

		@Override
		public <T extends @Nullable Object> T run(String name, Class<T> type, Supplier<T> supplier) {
			return execute(named(name).type(type), supplier);
		}

		@Override
		public <T extends @Nullable Object> T run(String name, TypeRef<T> type, Supplier<T> supplier) {
			return execute(named(name).type(type.type()), supplier);
		}

		@Override
		public void run(String name, Runnable action) {
			execute(named(name).type(Void.class), () -> {
				action.run();
				return null;
			});
		}

	}

	private final class ApprovalBuilder implements ApprovalStepBuilder {

		private final String role;

		private final Duration timeout;

		private ApprovalTimeoutPolicy onTimeout = ApprovalTimeoutPolicy.FAIL;

		private @Nullable String description;

		ApprovalBuilder(String role, Duration timeout) {
			if (role.isBlank()) {
				throw new IllegalArgumentException("approval role must not be blank");
			}
			this.role = role;
			this.timeout = timeout;
		}

		@Override
		public ApprovalStepBuilder onTimeout(ApprovalTimeoutPolicy policy) {
			this.onTimeout = policy;
			return this;
		}

		@Override
		public ApprovalStepBuilder description(String text) {
			this.description = text;
			return this;
		}

		private StepSpec named(String name) {
			return StepSpec.named(name).kind(StepKind.APPROVAL)
				.approval(new StepSpec.Approval(this.role, this.timeout, this.onTimeout, this.description));
		}

		@Override
		public <T extends @Nullable Object> T run(String name, Supplier<T> supplier) {
			return execute(named(name), supplier);
		}

		@Override
		public <T extends @Nullable Object> T run(String name, Class<T> type, Supplier<T> supplier) {
			return execute(named(name).type(type), supplier);
		}

		@Override
		public <T extends @Nullable Object> T run(String name, TypeRef<T> type, Supplier<T> supplier) {
			return execute(named(name).type(type.type()), supplier);
		}

		@Override
		public void run(String name, Runnable action) {
			execute(named(name).type(Void.class), () -> {
				action.run();
				return null;
			});
		}

	}

}
