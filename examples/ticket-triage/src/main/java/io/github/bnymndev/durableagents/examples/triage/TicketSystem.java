package io.github.bnymndev.durableagents.examples.triage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Stands in for the ticket system the agent works against. */
@Component
public class TicketSystem {

	private final Map<String, Ticket> open = new ConcurrentHashMap<>();

	private final Map<String, Resolution> resolved = new ConcurrentHashMap<>();

	public void open(Ticket ticket) {
		this.open.put(ticket.id(), ticket);
	}

	public Optional<Ticket> find(String id) {
		return Optional.ofNullable(this.open.get(id));
	}

	public Optional<Resolution> resolution(String id) {
		return Optional.ofNullable(this.resolved.get(id));
	}

	public List<SimilarTicket> findResolvedInCategory(String category) {
		return switch (category.toLowerCase()) {
			case "billing" -> List.of(new SimilarTicket("T-1042", "re-issued invoice with corrected VAT", 0.91));
			case "login" -> List.of(new SimilarTicket("T-877", "reset MFA device", 0.88), new SimilarTicket("T-901", "unlocked account", 0.7));
			default -> List.of();
		};
	}

	public void resolve(Resolution resolution) {
		this.resolved.put(resolution.ticketId(), resolution);
		this.open.remove(resolution.ticketId());
	}

}
