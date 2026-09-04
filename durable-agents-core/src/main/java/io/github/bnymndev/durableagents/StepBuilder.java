package io.github.bnymndev.durableagents;

import java.time.Duration;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/** Fluent configuration of a single step; obtained from {@link Steps#retry}, {@link Steps#timeout} and friends. */
public interface StepBuilder {

	StepBuilder retry(int maxAttempts, Duration backoff);

	StepBuilder retry(int maxAttempts, Duration backoff, double multiplier);

	StepBuilder retryOn(Class<? extends Throwable> type);

	StepBuilder abortOn(Class<? extends Throwable> type);

	StepBuilder timeout(Duration timeout);

	StepBuilder version(int version);

	StepBuilder compensate(Runnable undo);

	<T extends @Nullable Object> T run(String name, Supplier<T> supplier);

	<T extends @Nullable Object> T run(String name, Class<T> type, Supplier<T> supplier);

	<T extends @Nullable Object> T run(String name, TypeRef<T> type, Supplier<T> supplier);

	void run(String name, Runnable action);

}
