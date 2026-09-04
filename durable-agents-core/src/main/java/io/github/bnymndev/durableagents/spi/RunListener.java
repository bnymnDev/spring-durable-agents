package io.github.bnymndev.durableagents.spi;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunStatus;

/** Observes run executions in the executing thread. */
public interface RunListener {

	default void onRunStarted(RunId runId, String agentName, boolean resumed) {
	}

	default void onRunFinished(RunId runId, String agentName, RunStatus status, Duration duration, @Nullable Throwable error) {
	}

}
