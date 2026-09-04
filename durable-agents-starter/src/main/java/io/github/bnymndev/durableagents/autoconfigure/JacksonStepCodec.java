package io.github.bnymndev.durableagents.autoconfigure;

import java.lang.reflect.Type;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

import io.github.bnymndev.durableagents.spi.StepCodec;

/** {@link StepCodec} on Jackson 3, using the application's {@code JsonMapper} when there is one. */
public final class JacksonStepCodec implements StepCodec {

	private final ObjectMapper mapper;

	public JacksonStepCodec(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public @Nullable String encode(@Nullable Object value) {
		return (value == null) ? null : this.mapper.writeValueAsString(value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends @Nullable Object> @Nullable T decode(@Nullable String encoded, Type type) {
		if (encoded == null || type == Void.class || type == void.class) {
			return null;
		}
		return (T) this.mapper.readValue(encoded, this.mapper.constructType(type));
	}

}
