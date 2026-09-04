package io.github.bnymndev.durableagents.spi;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunStatus;

/** Persistent state of a run; one row of {@code agent_run}. */
public record RunRecord(RunId id, String agentName, int agentVersion, RunStatus status, @Nullable String input,
		@Nullable String output, @Nullable String error, Instant createdAt, Instant updatedAt, @Nullable String ownerInstance,
		@Nullable Instant leaseUntil, @Nullable String correlationId) {

	public RunRecord withStatus(RunStatus newStatus, Instant now) {
		return new RunRecord(this.id, this.agentName, this.agentVersion, newStatus, this.input, this.output, this.error,
				this.createdAt, now, this.ownerInstance, this.leaseUntil, this.correlationId);
	}

	public RunRecord withOutput(@Nullable String newOutput, Instant now) {
		return new RunRecord(this.id, this.agentName, this.agentVersion, RunStatus.COMPLETED, this.input, newOutput, null,
				this.createdAt, now, null, null, this.correlationId);
	}

	public RunRecord withError(RunStatus newStatus, @Nullable String newError, Instant now) {
		return new RunRecord(this.id, this.agentName, this.agentVersion, newStatus, this.input, this.output, newError,
				this.createdAt, now, null, null, this.correlationId);
	}

	public RunRecord withLease(@Nullable String owner, @Nullable Instant until, Instant now) {
		return new RunRecord(this.id, this.agentName, this.agentVersion, this.status, this.input, this.output, this.error,
				this.createdAt, now, owner, until, this.correlationId);
	}

	public boolean leaseExpired(Instant now) {
		return this.leaseUntil == null || !this.leaseUntil.isAfter(now);
	}

}
