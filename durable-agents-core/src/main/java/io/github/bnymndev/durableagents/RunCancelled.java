package io.github.bnymndev.durableagents;

/** Thrown at the next step boundary after {@link AgentRuns#cancel(RunId, String)} was called. Do not catch it. */
public final class RunCancelled extends RuntimeException {

	private final RunId runId;

	public RunCancelled(RunId runId, String reason) {
		super(reason, null, false, false);
		this.runId = runId;
	}

	public RunId runId() {
		return this.runId;
	}

}
