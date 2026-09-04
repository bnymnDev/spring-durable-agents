package io.github.bnymndev.durableagents;

import org.jspecify.annotations.Nullable;

/**
 * Outcome of one execution of a run on this instance.
 *
 * @param runId the run
 * @param status the status after this execution: {@code COMPLETED}, {@code FAILED}, {@code CANCELLED},
 * {@code SUSPENDED}, or {@code RUNNING} when the execution crashed and the run is up for a resume
 * @param output the decoded output when completed
 * @param error the error message when failed
 */
public record RunResult(RunId runId, RunStatus status, @Nullable Object output, @Nullable String error) {

	public boolean completed() {
		return this.status == RunStatus.COMPLETED;
	}

	/** The output cast to {@code type}; fails when the run did not complete. */
	public <O> O outputAs(Class<O> type) {
		if (this.status != RunStatus.COMPLETED) {
			throw new IllegalStateException("Run " + this.runId + " is " + this.status + (this.error != null ? ": " + this.error : ""));
		}
		return type.cast(this.output);
	}

}
