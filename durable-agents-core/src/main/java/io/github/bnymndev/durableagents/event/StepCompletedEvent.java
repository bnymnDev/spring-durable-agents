package io.github.bnymndev.durableagents.event;

import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.StepStatus;

/** A step reached a final status. Published after the store was updated. */
public record StepCompletedEvent(RunId runId, String agentName, String stepKey, String stepName, StepKind kind,
		StepStatus status) implements RunEvent {
}
