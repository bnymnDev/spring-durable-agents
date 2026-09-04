package io.github.bnymndev.durableagents.examples.refund;

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
import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunSummary;

@RestController
public class RefundController {

	private final AgentRuns runs;

	public RefundController(AgentRuns runs) {
		this.runs = runs;
	}

	@PostMapping("/refunds")
	public ResponseEntity<Map<String, String>> request(@RequestBody RefundRequest request) {
		RunId runId = this.runs.start(RefundAgent.class, request, request.orderNumber()).runId();
		return ResponseEntity.accepted().body(Map.of("runId", runId.value()));
	}

	@GetMapping("/refunds/{runId}")
	public RunSummary status(@PathVariable("runId") String runId) {
		return this.runs.summary(RunId.of(runId))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no run " + runId));
	}

}
