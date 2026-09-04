package io.github.bnymndev.durableagents;

/**
 * Control-flow exception thrown by {@link Steps#approval} to suspend the run until a human decides.
 * Never catch it in agent code; let it propagate to the engine.
 */
public final class RunSuspended extends RuntimeException {

	private final RunId runId;

	private final String stepKey;

	public RunSuspended(RunId runId, String stepKey, String reason) {
		super(reason, null, false, false);
		this.runId = runId;
		this.stepKey = stepKey;
	}

	public RunId runId() {
		return this.runId;
	}

	public String stepKey() {
		return this.stepKey;
	}

}
