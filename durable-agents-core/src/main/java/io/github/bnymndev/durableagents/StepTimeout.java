package io.github.bnymndev.durableagents;

import java.time.Duration;

/** Thrown when a step exceeds its timeout. Recorded as {@code FAILED} with the message {@code timeout}. */
public class StepTimeout extends RuntimeException {

	public StepTimeout(String stepKey, Duration timeout) {
		super("Step " + stepKey + " timed out after " + timeout);
	}

}
