package io.github.bnymndev.durableagents;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Captures a generic type for replaying step results, e.g.
 * {@code new TypeRef<List<Ticket>>() {}}. Records and plain classes do not need this; pass the
 * {@link Class} instead.
 *
 * @param <T> the captured type
 */
public abstract class TypeRef<T> {

	private final Type type;

	protected TypeRef() {
		Type superclass = getClass().getGenericSuperclass();
		if (!(superclass instanceof ParameterizedType parameterized)) {
			throw new IllegalArgumentException("TypeRef must be created with a type argument, e.g. new TypeRef<List<Foo>>() {}");
		}
		this.type = parameterized.getActualTypeArguments()[0];
	}

	/** The captured type. */
	public Type type() {
		return this.type;
	}

}
