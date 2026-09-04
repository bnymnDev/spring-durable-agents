package io.github.bnymndev.durableagents.event;

import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.RunId;

/** A run completed; {@code output} is the decoded output. */
public record RunCompletedEvent(RunId runId, String agentName, @Nullable Object output) implements RunEvent {
}
