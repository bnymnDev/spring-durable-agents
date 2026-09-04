package io.github.bnymndev.durableagents.spi;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.StepKind;

/** Read-only view of a step execution handed to {@link StepListener}s. */
public record StepExecution(RunId runId, String agentName, String stepKey, String stepName, StepKind kind,
		@Nullable String parentKey, int attempt, Instant startedAt, @Nullable String model, @Nullable Long tokensIn,
		@Nullable Long tokensOut) {

	public StepExecution withUsage(@Nullable String model, @Nullable Long tokensIn, @Nullable Long tokensOut) {
		return new StepExecution(this.runId, this.agentName, this.stepKey, this.stepName, this.kind, this.parentKey,
				this.attempt, this.startedAt, model, tokensIn, tokensOut);
	}

}
