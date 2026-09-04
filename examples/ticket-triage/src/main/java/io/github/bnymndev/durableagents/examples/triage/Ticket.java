package io.github.bnymndev.durableagents.examples.triage;

/** Input of the agent. */
public record Ticket(String id, String customer, String subject, String body) {
}
