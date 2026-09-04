package io.github.bnymndev.durableagents.examples.triage;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.bnymndev.durableagents.AgentRuns;
import io.github.bnymndev.durableagents.RunHandle;
import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunSummary;

@RestController
public class TicketController {

	private final AgentRuns runs;

	private final TicketSystem tickets;

	public TicketController(AgentRuns runs, TicketSystem tickets) {
		this.runs = runs;
		this.tickets = tickets;
	}

	/** Opens a ticket and starts a triage run; returns immediately with the run id. */
	@PostMapping("/tickets")
	public ResponseEntity<Map<String, String>> open(@RequestBody Ticket ticket) {
		this.tickets.open(ticket);
		RunHandle handle = this.runs.start(TicketTriageAgent.class, ticket, ticket.id());
		return ResponseEntity.accepted().body(Map.of("ticketId", ticket.id(), "runId", handle.runId().value()));
	}

	@GetMapping("/tickets/{id}")
	public Map<String, Object> ticket(@PathVariable("id") String id) {
		return Map.of("open", this.tickets.find(id).map(Object.class::cast).orElse("no"),
				"resolution", this.tickets.resolution(id).map(Object.class::cast).orElse("pending"));
	}

	@GetMapping("/runs/{id}")
	public RunSummary run(@PathVariable("id") String id) {
		return this.runs.summary(RunId.of(id))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no run " + id));
	}

	@PostMapping("/runs/{id}/resume")
	public Map<String, String> resume(@PathVariable("id") String id) {
		return Map.of("runId", this.runs.resume(RunId.of(id)).runId().value());
	}

}
