package io.github.bnymndev.durableagents;

/** What the engine does with an approval nobody decided before it expired. */
public enum ApprovalTimeoutPolicy {

	/** Fail the run with an {@link ApprovalRejected}. */
	FAIL,

	/** Treat the timeout as an approval and execute the step. */
	APPROVE,

	/** Treat the timeout as a rejection: the step and the run fail. */
	REJECT

}
