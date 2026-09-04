package io.github.bnymndev.durableagents.event;

import io.github.bnymndev.durableagents.RunId;

/** A run was started (or resumed, see {@code resumed}). */
public record RunStartedEvent(RunId runId, String agentName, boolean resumed) implements RunEvent {
}
