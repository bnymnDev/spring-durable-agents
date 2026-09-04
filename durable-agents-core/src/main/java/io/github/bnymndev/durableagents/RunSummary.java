package io.github.bnymndev.durableagents;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** One row of {@link AgentRuns#find(RunQuery)}. */
public record RunSummary(RunId id, String agentName, int agentVersion, RunStatus status, Instant createdAt,
		Instant updatedAt, @Nullable String error, @Nullable String correlationId) {
}
