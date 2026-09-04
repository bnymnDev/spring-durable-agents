package io.github.bnymndev.durableagents.actuator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import io.github.bnymndev.durableagents.AgentRuns;
import io.github.bnymndev.durableagents.RunQuery;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.RunSummary;
import io.github.bnymndev.durableagents.internal.RunEngine;
import io.github.bnymndev.durableagents.spi.AgentRunStore;
import io.github.bnymndev.durableagents.spi.ApprovalStore;
import io.github.bnymndev.durableagents.spi.StepRecord;
import io.github.bnymndev.durableagents.spi.StepStore;

/**
 * {@code /actuator/agents}: counts per agent and status, recent failures, pending approvals.
 * {@code /actuator/agents/runs}: the most recent runs. {@code /actuator/agents/runs/{id}}: one run
 * with its full step timeline.
 */
@Endpoint(id = "agents")
public class AgentsEndpoint {

	private final AgentRunStore runs;

	private final StepStore steps;

	private final ApprovalStore approvals;

	private final RunEngine engine;

	public AgentsEndpoint(AgentRunStore runs, StepStore steps, ApprovalStore approvals, RunEngine engine) {
		this.runs = runs;
		this.steps = steps;
		this.approvals = approvals;
		this.engine = engine;
	}

	@ReadOperation
	public Map<String, Object> summary() {
		Map<String, Object> result = new LinkedHashMap<>();
		Map<String, Map<String, Long>> agents = new LinkedHashMap<>();
		this.runs.countByAgentAndStatus().forEach((agent, counts) -> {
			Map<String, Long> byStatus = new LinkedHashMap<>();
			for (RunStatus status : RunStatus.values()) {
				byStatus.put(status.name(), counts.getOrDefault(status, 0L));
			}
			agents.put(agent, byStatus);
		});
		result.put("agents", agents);
		result.put("activeOnThisInstance", this.engine.activeRuns().stream().map((id) -> id.value()).sorted().toList());
		result.put("pendingApprovals", this.approvals.countPending());
		result.put("recentFailures", this.runs.findRecentFailures(10));
		return result;
	}

	@ReadOperation
	public @Nullable Object section(@Selector String section) {
		if ("runs".equals(section)) {
			return this.runs.find(RunQuery.all());
		}
		return null;
	}

	@ReadOperation
	public @Nullable Object run(@Selector String section, @Selector String id) {
		if (!"runs".equals(section)) {
			return null;
		}
		return this.runs.find(io.github.bnymndev.durableagents.RunId.of(id)).map((run) -> {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("run", RunEngine.toSummary(run));
			result.put("input", run.input());
			result.put("output", run.output());
			result.put("ownerInstance", run.ownerInstance());
			result.put("leaseUntil", run.leaseUntil());
			result.put("steps", this.steps.findByRun(run.id()));
			return (Object) result;
		}).orElse(null);
	}

	/** Exposed for tests. */
	List<RunSummary> recentRuns(AgentRuns agentRuns) {
		return agentRuns.find(RunQuery.all()).content();
	}

	static List<StepRecord> timeline(StepStore store, io.github.bnymndev.durableagents.RunId id) {
		return store.findByRun(id);
	}

}
