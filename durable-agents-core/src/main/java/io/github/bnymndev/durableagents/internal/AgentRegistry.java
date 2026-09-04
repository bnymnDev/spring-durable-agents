package io.github.bnymndev.durableagents.internal;

import java.beans.Introspector;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;

import io.github.bnymndev.durableagents.Agent;
import io.github.bnymndev.durableagents.DurableAgent;

/** Finds {@code @DurableAgent} beans and resolves their name, version and input type. */
public final class AgentRegistry {

	private final ListableBeanFactory beanFactory;

	private volatile @Nullable Map<String, AgentDefinition> byName;

	public AgentRegistry(ListableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	public Optional<AgentDefinition> byName(String name) {
		return Optional.ofNullable(definitions().get(name));
	}

	public AgentDefinition require(String name) {
		return byName(name).orElseThrow(() -> new IllegalArgumentException(
				"No @DurableAgent named '" + name + "'. Known agents: " + definitions().keySet()));
	}

	public AgentDefinition require(Class<?> agentClass) {
		return definitions().values().stream()
			.filter((def) -> agentClass.isAssignableFrom(def.agentClass()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No @DurableAgent bean of type " + agentClass.getName()
					+ ". Known agents: " + definitions().keySet()));
	}

	public Map<String, AgentDefinition> definitions() {
		Map<String, AgentDefinition> result = this.byName;
		if (result == null) {
			synchronized (this) {
				result = this.byName;
				if (result == null) {
					result = load();
					this.byName = result;
				}
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private Map<String, AgentDefinition> load() {
		Map<String, AgentDefinition> result = new LinkedHashMap<>();
		Map<String, Object> beans = this.beanFactory.getBeansWithAnnotation(DurableAgent.class);
		beans.forEach((beanName, bean) -> {
			Class<?> targetClass = AopUtils.getTargetClass(bean);
			if (!(bean instanceof Agent)) {
				throw new IllegalStateException("Bean '" + beanName + "' is annotated with @DurableAgent but does not implement "
						+ Agent.class.getName());
			}
			DurableAgent annotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, DurableAgent.class);
			String name = (annotation != null && !annotation.value().isEmpty()) ? annotation.value()
					: Introspector.decapitalize(targetClass.getSimpleName());
			int version = (annotation != null) ? annotation.version() : 1;
			Type inputType = resolveInputType(targetClass);
			AgentDefinition previous = result.put(name,
					new AgentDefinition(name, version, (Agent<Object, Object>) bean, targetClass, inputType));
			if (previous != null) {
				throw new IllegalStateException("Two @DurableAgent beans share the name '" + name + "': "
						+ previous.agentClass().getName() + " and " + targetClass.getName());
			}
		});
		return Collections.unmodifiableMap(result);
	}

	static Type resolveInputType(Class<?> agentClass) {
		ResolvableType generic = ResolvableType.forClass(Agent.class, agentClass).getGeneric(0);
		return generic.resolve() != null ? generic.getType() : Object.class;
	}

}
