package io.github.bnymndev.durableagents.approval;

import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.springframework.modulith.events.Externalized;

/**
 * Spring Modulith externalized event: published for every approval request so that a Kafka, AMQP,
 * SQS or JMS notifier can pick it up without touching the engine. The routing target is
 * {@code durable-agents.approvals} with the required role as key.
 */
@Externalized("durable-agents.approvals::#{#this.requiredRole()}")
public record ApprovalRequested(String approvalId, String runId, String agentName, String stepName, String requiredRole,
		@Nullable String description, Instant expiresAt, @Nullable String correlationId, String decideUrl) {
}
