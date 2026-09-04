package io.github.bnymndev.durableagents.internal;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import org.jspecify.annotations.Nullable;

/**
 * Micrometer context-propagation accessor for {@link StepsHolder}. Registering it makes the active
 * run visible in threads started through a context-propagating executor (e.g. Spring's
 * {@code TaskDecorator} or {@code ContextSnapshot.wrap}). Only loaded when
 * {@code io.micrometer:context-propagation} is on the classpath.
 */
public final class StepsThreadLocalAccessor implements ThreadLocalAccessor<DefaultSteps> {

	public static final String KEY = "io.github.bnymndev.durableagents.steps";

	public static void register() {
		ContextRegistry.getInstance().registerThreadLocalAccessor(new StepsThreadLocalAccessor());
	}

	@Override
	public Object key() {
		return KEY;
	}

	@Override
	public @Nullable DefaultSteps getValue() {
		return StepsHolder.current();
	}

	@Override
	public void setValue(DefaultSteps value) {
		StepsHolder.set(value);
	}

	@Override
	public void setValue() {
		StepsHolder.clear();
	}

}
