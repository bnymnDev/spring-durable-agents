package io.github.bnymndev.durableagents;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/** Starts, resumes, cancels and queries runs. Inject it anywhere: controllers, listeners, schedulers. */
public interface AgentRuns {

	/** Starts a run of the agent bean of type {@code agent}. Returns immediately; the run executes on a virtual thread. */
	<I> RunHandle start(Class<? extends Agent<I, ?>> agent, I input);

	/** Starts a run with a correlation id (an order number, a ticket id) that can be queried later. */
	<I> RunHandle start(Class<? extends Agent<I, ?>> agent, I input, @Nullable String correlationId);

	/** Starts a run by agent name, e.g. from a message listener that only knows the name. */
	RunHandle start(String agentName, Object input, @Nullable String correlationId);

	/**
	 * Resumes a run that is suspended, failed, or running under an expired lease. Completed steps
	 * are replayed from the store; execution continues at the first step without a result.
	 */
	RunHandle resume(RunId id);

	RunStatus status(RunId id);

	Optional<RunSummary> summary(RunId id);

	/** Returns the decoded output of a completed run. */
	<O> Optional<O> output(RunId id, Class<O> type);

	/**
	 * Cancels a run. A suspended run is cancelled immediately; a running one at its next step
	 * boundary. Completed steps are not compensated.
	 */
	void cancel(RunId id, String reason);

	Page<RunSummary> find(RunQuery query);

}
