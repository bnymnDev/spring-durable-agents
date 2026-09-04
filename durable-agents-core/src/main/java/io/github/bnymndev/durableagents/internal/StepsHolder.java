package io.github.bnymndev.durableagents.internal;

import org.jspecify.annotations.Nullable;

/**
 * Holds the {@link DefaultSteps} of the run executing on the current thread. A plain
 * {@code ThreadLocal}: {@code ScopedValue} is still preview on the Java 21 baseline (see ADR-002).
 * Propagation into threads spawned inside a step goes through Micrometer context-propagation
 * ({@link StepsThreadLocalAccessor}) when it is on the classpath.
 */
public final class StepsHolder {

	private static final ThreadLocal<@Nullable DefaultSteps> CURRENT = new ThreadLocal<>();

	private StepsHolder() {
	}

	public static @Nullable DefaultSteps current() {
		return CURRENT.get();
	}

	public static boolean isActive() {
		return CURRENT.get() != null;
	}

	static void set(@Nullable DefaultSteps steps) {
		if (steps == null) {
			CURRENT.remove();
		}
		else {
			CURRENT.set(steps);
		}
	}

	static void clear() {
		CURRENT.remove();
	}

	/** Runs {@code action} with {@code steps} bound to the current thread, restoring the previous binding afterwards. */
	static <T> T withSteps(@Nullable DefaultSteps steps, java.util.function.Supplier<T> action) {
		DefaultSteps previous = CURRENT.get();
		set(steps);
		try {
			return action.get();
		}
		finally {
			set(previous);
		}
	}

}
