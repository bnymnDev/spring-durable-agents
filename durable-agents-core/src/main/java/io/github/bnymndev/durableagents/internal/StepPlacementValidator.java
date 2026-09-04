package io.github.bnymndev.durableagents.internal;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import io.github.bnymndev.durableagents.DurableAgent;
import io.github.bnymndev.durableagents.Step;

/**
 * Fails application startup when a {@code @DurableAgent} class declares {@code @Step} methods, or
 * when a {@code @Step} method cannot be proxied. Also records the bean names of step beans.
 */
public final class StepPlacementValidator implements BeanPostProcessor {

	private final StepBeanNames beanNames;

	public StepPlacementValidator(StepBeanNames beanNames) {
		this.beanNames = beanNames;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		Class<?> targetClass = ClassUtils.getUserClass(bean);
		List<Method> stepMethods = new ArrayList<>();
		ReflectionUtils.doWithMethods(targetClass, (method) -> {
			if (AnnotatedElementUtils.hasAnnotation(method, Step.class)) {
				stepMethods.add(method);
			}
		}, ReflectionUtils.USER_DECLARED_METHODS);
		if (stepMethods.isEmpty()) {
			return bean;
		}
		if (AnnotatedElementUtils.hasAnnotation(targetClass, DurableAgent.class)) {
			throw new BeanCreationException(beanName, "@Step methods are not allowed on the @DurableAgent class "
					+ targetClass.getName() + " (found: " + names(stepMethods) + "). Spring AOP cannot intercept calls a bean "
					+ "makes to itself, so these steps would silently not be recorded. Move them to a separate @Component and "
					+ "call that bean from run(), or use steps.run(\"name\", () -> ...) inside the agent.");
		}
		for (Method method : stepMethods) {
			int mod = method.getModifiers();
			if (Modifier.isPrivate(mod) || Modifier.isStatic(mod) || Modifier.isFinal(mod)) {
				throw new BeanCreationException(beanName, "@Step method " + targetClass.getSimpleName() + "." + method.getName()
						+ " must be public (or package/protected), non-static and non-final so that Spring AOP can intercept it.");
			}
		}
		if (Modifier.isFinal(targetClass.getModifiers()) && targetClass.getInterfaces().length == 0) {
			throw new BeanCreationException(beanName, "Class " + targetClass.getName()
					+ " declares @Step methods but is final and implements no interface, so it cannot be proxied.");
		}
		this.beanNames.register(targetClass, beanName);
		return bean;
	}

	private static String names(List<Method> methods) {
		return methods.stream().map(Method::getName).sorted().toList().toString();
	}

}
