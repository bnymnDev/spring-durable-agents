package io.github.bnymndev.durableagents;

/** Lifecycle of a run. */
public enum RunStatus {

	RUNNING, SUSPENDED, COMPLETED, FAILED, CANCELLED;

	/** Whether the run has reached a final state. */
	public boolean isFinal() {
		return this == COMPLETED || this == FAILED || this == CANCELLED;
	}

}
