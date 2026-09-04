package io.github.bnymndev.durableagents;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Retry policy for a {@link Step @Step}. Attempts are persisted per step, so a retry survives a
 * restart in between. The programmatic equivalent is {@link Steps#retry(int, java.time.Duration)}.
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Retry {

	/** Total number of attempts including the first one. */
	int maxAttempts() default 3;

	/** Delay before the second attempt, in milliseconds. */
	long backoff() default 500;

	/** Multiplier applied to the delay after each failed attempt. */
	double multiplier() default 2.0;

	/** Upper bound for the delay in milliseconds; {@code 0} means unbounded. */
	long maxBackoff() default 0;

	/** Exception types that trigger a retry. Empty means every exception. */
	Class<? extends Throwable>[] retryOn() default {};

	/** Exception types that abort immediately, even when {@link #retryOn()} matches. */
	Class<? extends Throwable>[] abortOn() default {};

}
