package io.github.bnymndev.durableagents.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;

import io.github.bnymndev.durableagents.Agent;
import io.github.bnymndev.durableagents.AgentRuns;
import io.github.bnymndev.durableagents.Page;
import io.github.bnymndev.durableagents.RunCancelled;
import io.github.bnymndev.durableagents.RunHandle;
import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunQuery;
import io.github.bnymndev.durableagents.RunResult;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.RunSummary;
import io.github.bnymndev.durableagents.RunSuspended;
import io.github.bnymndev.durableagents.event.RunCancelledEvent;
import io.github.bnymndev.durableagents.event.RunCompletedEvent;
import io.github.bnymndev.durableagents.event.RunFailedEvent;
import io.github.bnymndev.durableagents.event.RunStartedEvent;
import io.github.bnymndev.durableagents.event.RunSuspendedEvent;
import io.github.bnymndev.durableagents.spi.AgentRunStore;
import io.github.bnymndev.durableagents.spi.ApprovalStore;
import io.github.bnymndev.durableagents.spi.RunListener;
import io.github.bnymndev.durableagents.spi.RunRecord;
import io.github.bnymndev.durableagents.spi.StepCodec;
import io.github.bnymndev.durableagents.spi.StepListener;
import io.github.bnymndev.durableagents.spi.StepStore;

/**
 * Starts and resumes runs. Each execution happens on the configured executor (virtual threads by
 * default) under a lease that is renewed by a heartbeat until the execution ends.
 */
public final class RunEngine implements AgentRuns, AutoCloseable {

	private static final Log logger = LogFactory.getLog(RunEngine.class);

	private final AgentRegistry registry;

	private final AgentRunStore runs;

	private final RunContext ctx;

	private final List<RunListener> runListeners;

	private final ScheduledExecutorService heartbeats;

	private final Map<RunId, ActiveRun> active = new ConcurrentHashMap<>();

	public RunEngine(AgentRegistry registry, AgentRunStore runs, StepStore steps, ApprovalStore approvals, StepCodec codec,
			ApplicationEventPublisher publisher, List<StepListener> stepListeners, List<RunListener> runListeners,
			Executor executor, EngineSettings settings, Clock clock) {
		this.registry = registry;
		this.runs = runs;
		this.runListeners = List.copyOf(runListeners);
		this.ctx = new RunContext(steps, approvals, codec, publisher, List.copyOf(stepListeners), executor, settings, clock);
		this.heartbeats = Executors.newSingleThreadScheduledExecutor((r) -> {
			Thread t = Thread.ofPlatform().name("durable-agents-heartbeat").daemon(true).unstarted(r);
			return t;
		});
	}

	// ---------------------------------------------------------------- AgentRuns

	@Override
	public <I> RunHandle start(Class<? extends Agent<I, ?>> agent, I input) {
		return start(agent, input, null);
	}

	@Override
	public <I> RunHandle start(Class<? extends Agent<I, ?>> agent, I input, @Nullable String correlationId) {
		return start(this.registry.require(agent), input, correlationId);
	}

	@Override
	public RunHandle start(String agentName, Object input, @Nullable String correlationId) {
		return start(this.registry.require(agentName), input, correlationId);
	}

	private RunHandle start(AgentDefinition def, Object input, @Nullable String correlationId) {
		Instant now = this.ctx.clock().instant();
		RunId id = RunId.next();
		String encodedInput = this.ctx.codec().encode(input);
		RunRecord run = new RunRecord(id, def.name(), def.version(), RunStatus.RUNNING, encodedInput, null, null, now, now,
				this.ctx.settings().instanceId(), now.plus(this.ctx.settings().leaseDuration()), correlationId);
		this.runs.insert(run);
		return submit(run, def, false);
	}

	@Override
	public RunHandle resume(RunId id) {
		RunRecord run = this.runs.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown run " + id));
		Instant now = this.ctx.clock().instant();
		if (run.status().isFinal() && run.status() != RunStatus.FAILED) {
			throw new IllegalStateException("Run " + id + " is " + run.status() + " and cannot be resumed");
		}
		if (run.status() == RunStatus.RUNNING && !run.leaseExpired(now) && this.active.containsKey(id)) {
			throw new IllegalStateException("Run " + id + " is executing on this instance");
		}
		Instant until = now.plus(this.ctx.settings().leaseDuration());
		if (!this.runs.tryAcquireLease(id, this.ctx.settings().instanceId(), until, now)) {
			throw new IllegalStateException("Run " + id + " is executing on another instance");
		}
		RunRecord acquired = run.withStatus(RunStatus.RUNNING, now).withLease(this.ctx.settings().instanceId(), until, now);
		this.runs.update(acquired);
		AgentDefinition def = this.registry.require(run.agentName());
		return submit(acquired, def, true);
	}

	@Override
	public RunStatus status(RunId id) {
		return this.runs.find(id).map(RunRecord::status)
			.orElseThrow(() -> new IllegalArgumentException("Unknown run " + id));
	}

	@Override
	public Optional<RunSummary> summary(RunId id) {
		return this.runs.find(id).map(RunEngine::toSummary);
	}

	@Override
	public <O> Optional<O> output(RunId id, Class<O> type) {
		return this.runs.find(id)
			.filter((run) -> run.status() == RunStatus.COMPLETED)
			.map((run) -> this.ctx.codec().<O>decode(run.output(), type));
	}

	@Override
	public void cancel(RunId id, String reason) {
		ActiveRun activeRun = this.active.get(id);
		if (activeRun != null) {
			activeRun.cancel(reason);
			return;
		}
		RunRecord run = this.runs.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown run " + id));
		if (run.status().isFinal()) {
			return;
		}
		Instant now = this.ctx.clock().instant();
		this.runs.update(run.withError(RunStatus.CANCELLED, reason, now));
		this.ctx.publisher().publishEvent(new RunCancelledEvent(id, run.agentName(), reason));
	}

	@Override
	public Page<RunSummary> find(RunQuery query) {
		return this.runs.find(query);
	}

	/** Ids of the runs executing on this instance right now. */
	public java.util.Set<RunId> activeRuns() {
		return java.util.Set.copyOf(this.active.keySet());
	}

	public static RunSummary toSummary(RunRecord run) {
		return new RunSummary(run.id(), run.agentName(), run.agentVersion(), run.status(), run.createdAt(), run.updatedAt(),
				run.error(), run.correlationId());
	}

	// ---------------------------------------------------------------- execution

	private RunHandle submit(RunRecord run, AgentDefinition def, boolean resumed) {
		ActiveRun activeRun = new ActiveRun(run.id(), this.runs, this.ctx.settings(), this.ctx.clock());
		this.active.put(run.id(), activeRun);
		CompletableFuture<RunResult> completion = new CompletableFuture<>();
		Runnable task = () -> {
			try {
				completion.complete(execute(run, def, activeRun, resumed));
			}
			catch (Throwable ex) {
				completion.completeExceptionally(ex);
			}
		};
		try {
			this.ctx.executor().execute(task);
		}
		catch (RuntimeException ex) {
			this.active.remove(run.id());
			throw ex;
		}
		return new RunHandle(run.id(), completion);
	}

	private RunResult execute(RunRecord run, AgentDefinition def, ActiveRun activeRun, boolean resumed) {
		RunId id = run.id();
		Instant started = this.ctx.clock().instant();
		long heartbeatMillis = Math.max(100, this.ctx.settings().leaseDuration().toMillis() / 3);
		activeRun.heartbeatTask(this.heartbeats.scheduleAtFixedRate(activeRun::heartbeat, heartbeatMillis, heartbeatMillis,
				TimeUnit.MILLISECONDS));
		DefaultSteps steps = new DefaultSteps(id, def.name(), run.correlationId(), this.ctx, activeRun);
		StepsHolder.set(steps);
		logger.info("Run " + id + " of agent '" + def.name() + "' " + (resumed ? "resumed" : "started"));
		this.ctx.publisher().publishEvent(new RunStartedEvent(id, def.name(), resumed));
		this.runListeners.forEach((l) -> l.onRunStarted(id, def.name(), resumed));
		RunResult result;
		Throwable failure = null;
		try {
			Object input = this.ctx.codec().decode(run.input(), def.inputType());
			Object output = def.agent().run(input, steps);
			result = complete(run, output);
		}
		catch (RunSuspended suspended) {
			result = suspend(run, suspended);
		}
		catch (RunCancelled cancelled) {
			result = cancelled(run, activeRun, cancelled);
		}
		catch (Error crash) {
			failure = crash;
			result = crashed(run, crash);
		}
		catch (Throwable error) {
			failure = error;
			result = fail(run, steps, error);
		}
		finally {
			activeRun.stopHeartbeat();
			this.active.remove(id);
			StepsHolder.clear();
		}
		Duration duration = Duration.between(started, this.ctx.clock().instant());
		for (RunListener listener : this.runListeners) {
			listener.onRunFinished(id, def.name(), result.status(), duration, failure);
		}
		return result;
	}

	private RunResult complete(RunRecord run, @Nullable Object output) {
		logger.info("Run " + run.id() + " of agent '" + run.agentName() + "' completed");
		Instant now = this.ctx.clock().instant();
		this.runs.update(run.withOutput(this.ctx.codec().encode(output), now));
		this.ctx.publisher().publishEvent(new RunCompletedEvent(run.id(), run.agentName(), output));
		return new RunResult(run.id(), RunStatus.COMPLETED, output, null);
	}

	private RunResult suspend(RunRecord run, RunSuspended suspended) {
		logger.info("Run " + run.id() + " suspended at " + suspended.stepKey() + ": " + suspended.getMessage());
		Instant now = this.ctx.clock().instant();
		this.runs.update(run.withError(RunStatus.SUSPENDED, null, now).withLease(null, null, now));
		this.ctx.publisher().publishEvent(new RunSuspendedEvent(run.id(), run.agentName(), suspended.stepKey(),
				String.valueOf(suspended.getMessage())));
		return new RunResult(run.id(), RunStatus.SUSPENDED, null, suspended.getMessage());
	}

	private RunResult cancelled(RunRecord run, ActiveRun activeRun, RunCancelled cancelled) {
		if (activeRun.leaseLost()) {
			logger.info("Run " + run.id() + " stopped on this instance: " + cancelled.getMessage());
			return new RunResult(run.id(), RunStatus.RUNNING, null, cancelled.getMessage());
		}
		Instant now = this.ctx.clock().instant();
		this.runs.update(run.withError(RunStatus.CANCELLED, cancelled.getMessage(), now));
		this.ctx.publisher().publishEvent(new RunCancelledEvent(run.id(), run.agentName(), String.valueOf(cancelled.getMessage())));
		return new RunResult(run.id(), RunStatus.CANCELLED, null, cancelled.getMessage());
	}

	private RunResult fail(RunRecord run, DefaultSteps steps, Throwable error) {
		logger.warn("Run " + run.id() + " of agent '" + run.agentName() + "' failed", error);
		try {
			steps.runCompensations();
		}
		catch (RuntimeException ex) {
			logger.warn("Compensation of run " + run.id() + " failed", ex);
		}
		Instant now = this.ctx.clock().instant();
		String message = DefaultSteps.describe(error);
		this.runs.update(run.withError(RunStatus.FAILED, message, now));
		this.ctx.publisher().publishEvent(new RunFailedEvent(run.id(), run.agentName(), message));
		return new RunResult(run.id(), RunStatus.FAILED, null, message);
	}

	private RunResult crashed(RunRecord run, Error crash) {
		// Treat like a process crash: leave RUNNING, release the lease so the reaper (or resume()) picks it up.
		logger.error("Run " + run.id() + " crashed; leaving it RUNNING with an expired lease for resume", crash);
		Instant now = this.ctx.clock().instant();
		this.runs.find(run.id()).ifPresent((current) -> this.runs.update(current.withLease(null, now, now)));
		return new RunResult(run.id(), RunStatus.RUNNING, null, DefaultSteps.describe(crash));
	}

	@Override
	public void close() {
		this.heartbeats.shutdownNow();
	}

}
