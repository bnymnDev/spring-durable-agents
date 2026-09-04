package io.github.bnymndev.durableagents.spi;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.StepStatus;

/** Persistent state of a step; one row of {@code agent_step}. Unique per {@code (runId, stepKey)}. */
public record StepRecord(RunId runId, String stepKey, String stepName, int stepVersion, int sequence, StepStatus status,
		int attempt, @Nullable String input, @Nullable String output, @Nullable String outputType, @Nullable String error,
		StepKind kind, @Nullable String parentKey, @Nullable Long tokensIn, @Nullable Long tokensOut, @Nullable String model,
		@Nullable Instant startedAt, @Nullable Instant finishedAt) {

	public StepRecord withLlmUsage(@Nullable String model, @Nullable Long tokensIn, @Nullable Long tokensOut) {
		return new StepRecord(this.runId, this.stepKey, this.stepName, this.stepVersion, this.sequence, this.status,
				this.attempt, this.input, this.output, this.outputType, this.error, this.kind, this.parentKey, tokensIn,
				tokensOut, model, this.startedAt, this.finishedAt);
	}

}
