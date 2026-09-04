package io.github.bnymndev.durableagents.internal;

import org.aopalliance.aop.Advice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;

import io.github.bnymndev.durableagents.Step;

/** Spring AOP advisor applying {@link StepMethodInterceptor} to every bean method annotated with {@code @Step}. */
public final class StepAdvisor extends AbstractPointcutAdvisor {

	private final Pointcut pointcut = AnnotationMatchingPointcut.forMethodAnnotation(Step.class);

	private final StepMethodInterceptor advice;

	public StepAdvisor(StepMethodInterceptor advice) {
		this.advice = advice;
	}

	@Override
	public Pointcut getPointcut() {
		return this.pointcut;
	}

	@Override
	public Advice getAdvice() {
		return this.advice;
	}

}
