package io.github.bnymndev.durableagents;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * Context of the step currently executing. Declare it as a parameter of a {@link Step @Step} method
 * and the engine injects it.
 */
public interface StepContext {

	RunId runId();

	String stepKey();

	String stepName();

	/** The current attempt, starting at 1. */
	int attempt();

	/** Extends the lease of the current run. */
	void heartbeat();

	/** Durable ad-hoc value, see {@link Steps#sideEffect(String, Class, Supplier)}. */
	<T extends @Nullable Object> T sideEffect(String key, Class<T> type, Supplier<T> supplier);

}
