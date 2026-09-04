package io.github.bnymndev.durableagents.event;

import io.github.bnymndev.durableagents.RunId;

/** A run failed after all retries and compensations. */
public record RunFailedEvent(RunId runId, String agentName, String error) implements RunEvent {
}
