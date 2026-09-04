package io.github.bnymndev.durableagents.event;

import io.github.bnymndev.durableagents.RunId;

/** A run was suspended at {@code stepKey}, waiting for an approval. */
public record RunSuspendedEvent(RunId runId, String agentName, String stepKey, String reason) implements RunEvent {
}
