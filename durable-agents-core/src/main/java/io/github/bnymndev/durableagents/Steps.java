package io.github.bnymndev.durableagents;

import java.time.Duration;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * The durable step API handed to {@link Agent#run(Object, Steps)}.
 *
 * <p>Every operation computes a deterministic step key {@code runId:name:n}, where {@code n} is the
 * number of previous calls with the same name in this run (a step started inside another step is
 * keyed below its parent: {@code parentKey/name:n}). A completed key is replayed from the
 * store: the supplier is not executed and the stored value is returned. This is what makes a run
 * resumable, and it is why the code between steps has to be deterministic.
 */
public interface Steps {

	/** The id of the current run. */
	RunId runId();

	/** Runs a step whose result is a record or plain class. On replay the stored value is returned. */
	<T extends @Nullable Object> T run(String name, Supplier<T> supplier);

	/** Runs a step and replays the result as {@code type}. */
	<T extends @Nullable Object> T run(String name, Class<T> type, Supplier<T> supplier);

	/** Runs a step and replays the result as the generic type captured by {@code type}. */
	<T extends @Nullable Object> T run(String name, TypeRef<T> type, Supplier<T> supplier);

	/** Runs a step without a result. */
	void run(String name, Runnable action);

	/**
	 * Runs a step of kind {@code LLM}. Chat calls made inside the supplier through a
	 * {@code ChatClient} carrying the durable advisor are recorded as child steps with model and
	 * token usage.
	 */
	<T extends @Nullable Object> T llm(String name, Class<T> type, Supplier<T> supplier);

	/** Same as {@link #llm(String, Class, Supplier)} for a generic result type. */
	<T extends @Nullable Object> T llm(String name, TypeRef<T> type, Supplier<T> supplier);

	/**
	 * Returns a durable ad-hoc value without a named step. The key is not counted: calling
	 * {@code sideEffect("payment:" + orderId, ...)} twice returns the first value. Use it for
	 * idempotency keys, generated ids and timestamps.
	 */
	<T extends @Nullable Object> T sideEffect(String key, Class<T> type, Supplier<T> supplier);

	/** Same as {@link #sideEffect(String, Class, Supplier)} with the type inferred from the value. */
	<T extends @Nullable Object> T sideEffect(String key, Supplier<T> supplier);

	/** Starts a step with a retry policy: {@code steps.retry(3, Duration.ofSeconds(2)).run(...)}. */
	StepBuilder retry(int maxAttempts, Duration backoff);

	/** Starts a step with a timeout. */
	StepBuilder timeout(Duration timeout);

	/** Starts a step with an explicit version; a stored result of another version is recomputed. */
	StepBuilder version(int version);

	/** Starts a step with a compensation that runs, in reverse order, when the run fails later. */
	StepBuilder compensate(Runnable undo);

	/**
	 * Starts a step that needs a human decision before it executes. The first call persists the
	 * step as {@code PENDING_APPROVAL} and suspends the run by throwing {@link RunSuspended}. Once a
	 * user with {@code role} approves, the run is resumed and the supplier executes.
	 * <p>Do not catch {@link RunSuspended}.
	 */
	ApprovalStepBuilder approval(String role, Duration timeout);

	/** Extends the lease of the current run. Call it from inside long-running steps. */
	void heartbeat();

}
