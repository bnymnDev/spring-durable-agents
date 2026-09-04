package io.github.bnymndev.durableagents.examples.triage;

/** Structured output of the classification prompt. */
public record Classification(String category, Urgency urgency, String summary) {

	public enum Urgency {

		LOW, NORMAL, HIGH

	}

}
