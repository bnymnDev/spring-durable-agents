package io.github.bnymndev.durableagents.internal;

import java.beans.Introspector;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Maps the target class of a bean with {@code @Step} methods to its bean name, for default step names. */
public final class StepBeanNames {

	private final Map<Class<?>, String> names = new ConcurrentHashMap<>();

	void register(Class<?> targetClass, String beanName) {
		this.names.putIfAbsent(targetClass, beanName);
	}

	public String nameOf(Class<?> targetClass) {
		String name = this.names.get(targetClass);
		if (name == null || name.equals(targetClass.getName())) {
			// not registered, or registered under its fully qualified class name (e.g. via @Import)
			return Introspector.decapitalize(targetClass.getSimpleName());
		}
		return name;
	}

}
