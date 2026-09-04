package io.github.bnymndev.durableagents.internal;

import java.lang.reflect.Type;
import java.time.Duration;

import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.ApprovalTimeoutPolicy;
import io.github.bnymndev.durableagents.StepKind;

/**
 * Everything the engine needs to know about one step call. Built by {@link DefaultSteps} and the
 * {@code @Step} interceptor; the Spring AI module builds specs for LLM and tool child steps.
 *
 * @param name step name; the key counter is per name
 * @param fixedKey when set, the key is {@code runId:fixedKey} without a counter (side effects)
 * @param type type to decode a replayed result into; {@code null} means "use the stored class name"
 * @param kind explicit kind, or {@code null} for STEP/CHILD depending on nesting
 * @param version step version
 * @param retry retry policy
 * @param timeout timeout or {@code null}
 * @param compensate compensation to run when the run fails later, or {@code null}
 * @param replayGuard whether the step takes part in strict-replay comparison
 * @param input encoded input to store with the step (prompt hash, tool arguments), or {@code null}
 * @param approval approval configuration, or {@code null} for a normal step
 */
public record StepSpec(String name, @Nullable String fixedKey, @Nullable Type type, @Nullable StepKind kind, int version,
		RetryPolicy retry, @Nullable Duration timeout, @Nullable Runnable compensate, boolean replayGuard,
		@Nullable String input, @Nullable Approval approval) {

	public record Approval(String role, Duration timeout, ApprovalTimeoutPolicy onTimeout, @Nullable String description) {
	}

	public static StepSpec named(String name) {
		if (name.isBlank() || name.indexOf(':') >= 0) {
			throw new IllegalArgumentException("Step name must not be blank or contain ':' but was '" + name + "'");
		}
		return new StepSpec(name, null, null, null, 1, RetryPolicy.NONE, null, null, true, null, null);
	}

	public StepSpec type(@Nullable Type t) {
		return new StepSpec(this.name, this.fixedKey, t, this.kind, this.version, this.retry, this.timeout, this.compensate,
				this.replayGuard, this.input, this.approval);
	}

	public StepSpec kind(@Nullable StepKind k) {
		return new StepSpec(this.name, this.fixedKey, this.type, k, this.version, this.retry, this.timeout, this.compensate,
				this.replayGuard, this.input, this.approval);
	}

	public StepSpec version(int v) {
		return new StepSpec(this.name, this.fixedKey, this.type, this.kind, v, this.retry, this.timeout, this.compensate,
				this.replayGuard, this.input, this.approval);
	}

	public StepSpec retry(RetryPolicy r) {
		return new StepSpec(this.name, this.fixedKey, this.type, this.kind, this.version, r, this.timeout, this.compensate,
				this.replayGuard, this.input, this.approval);
	}

	public StepSpec timeout(@Nullable Duration t) {
		return new StepSpec(this.name, this.fixedKey, this.type, this.kind, this.version, this.retry, t, this.compensate,
				this.replayGuard, this.input, this.approval);
	}

	public StepSpec compensate(@Nullable Runnable undo) {
		return new StepSpec(this.name, this.fixedKey, this.type, this.kind, this.version, this.retry, this.timeout, undo,
				this.replayGuard, this.input, this.approval);
	}

	public StepSpec replayGuard(boolean guard) {
		return new StepSpec(this.name, this.fixedKey, this.type, this.kind, this.version, this.retry, this.timeout,
				this.compensate, guard, this.input, this.approval);
	}

	public StepSpec input(@Nullable String encodedInput) {
		return new StepSpec(this.name, this.fixedKey, this.type, this.kind, this.version, this.retry, this.timeout,
				this.compensate, this.replayGuard, encodedInput, this.approval);
	}

	public StepSpec fixedKey(@Nullable String key) {
		return new StepSpec(this.name, key, this.type, this.kind, this.version, this.retry, this.timeout, this.compensate,
				this.replayGuard, this.input, this.approval);
	}

	public StepSpec approval(@Nullable Approval a) {
		return new StepSpec(this.name, this.fixedKey, this.type, this.kind, this.version, this.retry, this.timeout,
				this.compensate, this.replayGuard, this.input, a);
	}

}
