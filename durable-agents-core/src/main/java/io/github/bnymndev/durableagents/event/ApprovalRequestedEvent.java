package io.github.bnymndev.durableagents.event;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.RunId;

/**
 * A step needs a human decision. Listen to it to notify approvers (Slack, e-mail, a ticket); the
 * approval module republishes it as a Spring Modulith externalized event.
 */
public record ApprovalRequestedEvent(RunId runId, String agentName, String approvalId, String stepKey, String stepName,
		String requiredRole, @Nullable String description, Instant expiresAt, @Nullable String correlationId)
		implements RunEvent {
}
