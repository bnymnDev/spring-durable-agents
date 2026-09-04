package io.github.bnymndev.durableagents;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a method of a Spring bean as a durable step.
 *
 * <p>Same rule as {@code @Transactional}: the annotation only works when the call crosses a bean
 * boundary, because it is implemented with ordinary Spring AOP. Annotate methods on a bean
 * <em>other than</em> the {@link DurableAgent @DurableAgent}; a {@code @Step} method declared on
 * the agent class itself fails application startup.
 *
 * <p>When a {@code @Step} method is called while a run is active, the call is recorded as a step
 * named {@code beanName.methodName} (or {@link #name()}); when no run is active the method executes
 * normally, so step beans stay usable outside agents and in plain unit tests. The method's generic
 * return type is used to replay the stored result, so {@code List<Foo>} round-trips correctly.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Step {

	/** The step name. Defaults to {@code beanName.methodName}. */
	String name() default "";

	/**
	 * The step version. Bump it when the serialized shape of the result changes; a stored result
	 * with a different version is not replayed but recomputed.
	 */
	int version() default 1;

	/** Retry policy. By default a step is attempted once. */
	Retry retry() default @Retry(maxAttempts = 1);

	/**
	 * Timeout as an ISO-8601 duration, e.g. {@code PT30S}. The step runs on its own virtual thread
	 * and is interrupted when the timeout elapses; the step is then recorded as {@code FAILED}.
	 * Empty means no timeout.
	 */
	String timeout() default "";

	/**
	 * Name of a method on the same bean that compensates this step when the run fails later. The
	 * compensating method must accept the same parameters as the step method. Compensations run in
	 * reverse order of the completed steps and are themselves recorded as steps.
	 */
	String compensate() default "";

}
