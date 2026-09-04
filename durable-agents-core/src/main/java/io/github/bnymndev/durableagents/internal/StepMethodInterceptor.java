package io.github.bnymndev.durableagents.internal;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

import io.github.bnymndev.durableagents.Step;
import io.github.bnymndev.durableagents.StepContext;

/**
 * Turns a call to a {@code @Step} method into a durable step when a run is active on the current
 * thread. Without an active run the method executes normally.
 */
public final class StepMethodInterceptor implements MethodInterceptor {

	private final StepBeanNames beanNames;

	public StepMethodInterceptor(StepBeanNames beanNames) {
		this.beanNames = beanNames;
	}

	@Override
	public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
		DefaultSteps steps = StepsHolder.current();
		Object target = invocation.getThis();
		if (steps == null || target == null) {
			return invocation.proceed();
		}
		Class<?> targetClass = AopUtils.getTargetClass(target);
		Method method = AopUtils.getMostSpecificMethod(invocation.getMethod(), targetClass);
		Step step = AnnotatedElementUtils.findMergedAnnotation(method, Step.class);
		if (step == null) {
			return invocation.proceed();
		}
		String name = step.name().isEmpty() ? this.beanNames.nameOf(targetClass) + "." + method.getName() : step.name();
		StepSpec spec = StepSpec.named(name)
			.type(method.getReturnType() == void.class ? Void.class : method.getGenericReturnType())
			.version(step.version())
			.retry(RetryPolicy.from(step.retry()))
			.timeout(step.timeout().isEmpty() ? null : Duration.parse(step.timeout()));
		Object[] args = invocation.getArguments();
		if (!step.compensate().isEmpty()) {
			Method undo = findCompensation(targetClass, method, step.compensate());
			Object[] snapshot = args.clone();
			spec = spec.compensate(() -> ReflectionUtils.invokeMethod(undo, target, snapshot));
		}
		int contextIndex = contextParameter(method);
		Supplier<@Nullable Object> body = () -> {
			if (contextIndex >= 0) {
				args[contextIndex] = steps.currentContext().orElseThrow();
			}
			try {
				return invocation.proceed();
			}
			catch (RuntimeException | Error ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw DefaultSteps.sneaky(ex);
			}
		};
		return steps.execute(spec, body);
	}

	private static int contextParameter(Method method) {
		Class<?>[] types = method.getParameterTypes();
		for (int i = 0; i < types.length; i++) {
			if (StepContext.class.isAssignableFrom(types[i])) {
				return i;
			}
		}
		return -1;
	}

	private static Method findCompensation(Class<?> targetClass, Method stepMethod, String compensate) {
		Method undo = ReflectionUtils.findMethod(targetClass, compensate, stepMethod.getParameterTypes());
		if (undo == null) {
			throw new IllegalStateException("@Step(compensate = \"" + compensate + "\") on " + targetClass.getSimpleName() + "."
					+ stepMethod.getName() + " names no method with the same parameters");
		}
		ReflectionUtils.makeAccessible(undo);
		return undo;
	}

}
