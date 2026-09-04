package io.github.bnymndev.durableagents.event;

import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.ApprovalDecision;
import io.github.bnymndev.durableagents.RunId;

/** An approval was approved, rejected or expired. */
public record ApprovalDecidedEvent(RunId runId, String agentName, String approvalId, String stepKey,
		ApprovalDecision decision, @Nullable String decidedBy, @Nullable String comment) implements RunEvent {
}
