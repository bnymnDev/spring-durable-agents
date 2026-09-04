package io.github.bnymndev.durableagents.examples.triage;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.bnymndev.durableagents.Step;

/**
 * Step methods live on a separate bean, the same rule as {@code @Transactional}: Spring AOP only
 * intercepts calls that cross a bean boundary. Called from the agent they are recorded as steps;
 * called from a plain unit test they just run.
 */
@Component
public class TriageSteps {

	private final TicketSystem tickets;

	public TriageSteps(TicketSystem tickets) {
		this.tickets = tickets;
	}

	@Step
	public List<SimilarTicket> findSimilar(Classification classification) {
		return this.tickets.findResolvedInCategory(classification.category());
	}

	@Step
	public Resolution propose(Ticket ticket, Classification classification, List<SimilarTicket> similar) {
		String action = similar.isEmpty() ? "escalate to second level"
				: "apply known resolution: " + similar.get(0).resolution();
		return new Resolution(ticket.id(), classification.category(), action, classification.summary());
	}

	@Step
	public void apply(Resolution resolution) {
		this.tickets.resolve(resolution);
	}

}
