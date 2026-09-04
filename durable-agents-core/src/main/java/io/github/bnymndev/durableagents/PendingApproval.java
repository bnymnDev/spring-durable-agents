package io.github.bnymndev.durableagents;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** An approval waiting for a decision. */
public record PendingApproval(String id, RunId runId, String agentName, String stepKey, String stepName, String requiredRole,
		@Nullable String description, Instant requestedAt, Instant expiresAt, @Nullable String correlationId) {
}
