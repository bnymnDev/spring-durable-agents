package io.github.bnymndev.durableagents.spi;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.ApprovalDecision;
import io.github.bnymndev.durableagents.RunId;

/** Persistent state of an approval request; one row of {@code agent_approval}. */
public record ApprovalRecord(String id, RunId runId, String stepKey, String requiredRole, @Nullable String description,
		Instant requestedAt, Instant expiresAt, ApprovalDecision decision, @Nullable Instant decidedAt,
		@Nullable String decidedBy, @Nullable String comment) {

	public ApprovalRecord decided(ApprovalDecision newDecision, Instant at, @Nullable String by, @Nullable String newComment) {
		return new ApprovalRecord(this.id, this.runId, this.stepKey, this.requiredRole, this.description, this.requestedAt,
				this.expiresAt, newDecision, at, by, newComment);
	}

}
