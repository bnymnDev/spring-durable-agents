package io.github.bnymndev.durableagents.examples.triage;

/** Output of the agent. */
public record Resolution(String ticketId, String category, String action, String note) {
}
