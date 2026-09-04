package io.github.bnymndev.durableagents;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

/**
 * Marks a Spring bean implementing {@link Agent} as a durable agent.
 *
 * <p>The bean is a plain Spring component: it is never proxied by this library. Durability goes
 * through the {@link Steps} object passed to {@link Agent#run(Object, Steps)}. Declaring
 * {@link Step @Step} methods on a class annotated with {@code @DurableAgent} fails application
 * startup, because Spring AOP cannot intercept self-invocations.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface DurableAgent {

	/**
	 * The agent name, used as {@code agent_name} in the store and in metrics. Defaults to the
	 * decapitalized simple class name.
	 */
	@AliasFor(annotation = Component.class, attribute = "value")
	String value() default "";

	/**
	 * The agent version, recorded with every run. Bump it when the step sequence changes so that
	 * runs started under an older version can be told apart.
	 */
	int version() default 1;

}
