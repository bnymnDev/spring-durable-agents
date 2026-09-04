package io.github.bnymndev.durableagents.spi;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

/**
 * Observes step executions in the executing thread. Register beans of this type to add metrics,
 * tracing or logging. Listeners run inside the step boundary; an exception thrown by a listener
 * propagates like a failure of the step itself.
 */
public interface StepListener {

	default void onStepStarted(StepExecution step) {
	}

	/** Called after the step finished. {@code error} is {@code null} on success. */
	default void onStepFinished(StepExecution step, Duration duration, @Nullable Throwable error) {
	}

	/** Called when a completed step is replayed from the store instead of executed. */
	default void onStepReplayed(StepExecution step) {
	}

}
