package io.github.bnymndev.durableagents.internal;

import java.time.Duration;
import java.util.List;

import io.github.bnymndev.durableagents.Retry;

/** Retry policy of a step. */
public record RetryPolicy(int maxAttempts, Duration backoff, double multiplier, Duration maxBackoff,
		List<Class<? extends Throwable>> retryOn, List<Class<? extends Throwable>> abortOn) {

	public static final RetryPolicy NONE = new RetryPolicy(1, Duration.ZERO, 1.0, Duration.ZERO, List.of(), List.of());

	public RetryPolicy {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts must be >= 1");
		}
	}

	public static RetryPolicy of(int maxAttempts, Duration backoff) {
		return new RetryPolicy(maxAttempts, backoff, 2.0, Duration.ZERO, List.of(), List.of());
	}

	public static RetryPolicy from(Retry retry) {
		return new RetryPolicy(retry.maxAttempts(), Duration.ofMillis(retry.backoff()), retry.multiplier(),
				Duration.ofMillis(retry.maxBackoff()), List.of(retry.retryOn()), List.of(retry.abortOn()));
	}

	public boolean shouldRetry(Throwable error, int attemptsSoFar) {
		if (attemptsSoFar >= this.maxAttempts) {
			return false;
		}
		for (Class<? extends Throwable> type : this.abortOn) {
			if (type.isInstance(error)) {
				return false;
			}
		}
		if (this.retryOn.isEmpty()) {
			return true;
		}
		for (Class<? extends Throwable> type : this.retryOn) {
			if (type.isInstance(error)) {
				return true;
			}
		}
		return false;
	}

	/** Delay before attempt number {@code nextAttempt} (2 for the first retry). */
	public Duration delayBefore(int nextAttempt) {
		double millis = this.backoff.toMillis() * Math.pow(this.multiplier, Math.max(0, nextAttempt - 2));
		if (!this.maxBackoff.isZero() && millis > this.maxBackoff.toMillis()) {
			millis = this.maxBackoff.toMillis();
		}
		return Duration.ofMillis((long) millis);
	}

	public RetryPolicy withMultiplier(double m) {
		return new RetryPolicy(this.maxAttempts, this.backoff, m, this.maxBackoff, this.retryOn, this.abortOn);
	}

	public RetryPolicy retryingOn(Class<? extends Throwable> type) {
		List<Class<? extends Throwable>> list = new java.util.ArrayList<>(this.retryOn);
		list.add(type);
		return new RetryPolicy(this.maxAttempts, this.backoff, this.multiplier, this.maxBackoff, List.copyOf(list), this.abortOn);
	}

	public RetryPolicy abortingOn(Class<? extends Throwable> type) {
		List<Class<? extends Throwable>> list = new java.util.ArrayList<>(this.abortOn);
		list.add(type);
		return new RetryPolicy(this.maxAttempts, this.backoff, this.multiplier, this.maxBackoff, this.retryOn, List.copyOf(list));
	}

}
