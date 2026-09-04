package io.github.bnymndev.durableagents;

import org.jspecify.annotations.Nullable;

/** Thrown from an approval step when the request was rejected or expired with a failing policy. */
public class ApprovalRejected extends RuntimeException {

	private final String stepKey;

	private final ApprovalDecision decision;

	private final @Nullable String decidedBy;

	public ApprovalRejected(String stepKey, ApprovalDecision decision, @Nullable String decidedBy, @Nullable String comment) {
		super(message(stepKey, decision, decidedBy, comment));
		this.stepKey = stepKey;
		this.decision = decision;
		this.decidedBy = decidedBy;
	}

	private static String message(String stepKey, ApprovalDecision decision, @Nullable String by, @Nullable String comment) {
		StringBuilder sb = new StringBuilder("Approval for step ").append(stepKey).append(' ').append(decision.name().toLowerCase());
		if (by != null) {
			sb.append(" by ").append(by);
		}
		if (comment != null && !comment.isBlank()) {
			sb.append(": ").append(comment);
		}
		return sb.toString();
	}

	public String stepKey() {
		return this.stepKey;
	}

	public ApprovalDecision decision() {
		return this.decision;
	}

	public @Nullable String decidedBy() {
		return this.decidedBy;
	}

}
