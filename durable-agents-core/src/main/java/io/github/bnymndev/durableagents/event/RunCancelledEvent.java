package io.github.bnymndev.durableagents.event;

import io.github.bnymndev.durableagents.RunId;

/** A run was cancelled. */
public record RunCancelledEvent(RunId runId, String agentName, String reason) implements RunEvent {
}
