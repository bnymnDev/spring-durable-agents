package io.github.bnymndev.durableagents.spi;

import java.lang.reflect.Type;

import org.jspecify.annotations.Nullable;

/**
 * Serializes step inputs and outputs. The starter provides a Jackson implementation; the core has no
 * serialization dependency of its own.
 */
public interface StepCodec {

	@Nullable String encode(@Nullable Object value);

	<T extends @Nullable Object> @Nullable T decode(@Nullable String encoded, Type type);

}
