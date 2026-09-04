package io.github.bnymndev.durableagents.internal.store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import io.github.bnymndev.durableagents.Page;
import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunQuery;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.RunSummary;
import io.github.bnymndev.durableagents.internal.RunEngine;
import io.github.bnymndev.durableagents.spi.AgentRunStore;
import io.github.bnymndev.durableagents.spi.RunRecord;

/** In-memory {@link AgentRunStore} for tests and {@code durable-agents.store=memory}. */
public final class InMemoryAgentRunStore implements AgentRunStore {

	private final Map<RunId, RunRecord> runs = new ConcurrentHashMap<>();

	private final InMemoryStepStore steps;

	private final InMemoryApprovalStore approvals;

	public InMemoryAgentRunStore(InMemoryStepStore steps, InMemoryApprovalStore approvals) {
		this.steps = steps;
		this.approvals = approvals;
	}

	@Override
	public void insert(RunRecord run) {
		if (this.runs.putIfAbsent(run.id(), run) != null) {
			throw new IllegalStateException("Run " + run.id() + " already exists");
		}
	}

	@Override
	public Optional<RunRecord> find(RunId id) {
		return Optional.ofNullable(this.runs.get(id));
	}

	@Override
	public void update(RunRecord run) {
		this.runs.computeIfPresent(run.id(), (id, existing) -> run);
	}

	@Override
	public synchronized boolean tryAcquireLease(RunId id, String owner, Instant until, Instant now) {
		RunRecord run = this.runs.get(id);
		if (run == null) {
			return false;
		}
		boolean free = run.status() != RunStatus.RUNNING || run.leaseExpired(now) || owner.equals(run.ownerInstance());
		if (!free) {
			return false;
		}
		this.runs.put(id, run.withStatus(RunStatus.RUNNING, now).withLease(owner, until, now));
		return true;
	}

	@Override
	public synchronized boolean extendLease(RunId id, String owner, Instant until, Instant now) {
		RunRecord run = this.runs.get(id);
		if (run == null || run.status() != RunStatus.RUNNING || !owner.equals(run.ownerInstance())) {
			return false;
		}
		this.runs.put(id, run.withLease(owner, until, now));
		return true;
	}

	@Override
	public List<RunRecord> findExpiredLeases(Instant now, int limit) {
		return this.runs.values().stream()
			.filter((run) -> run.status() == RunStatus.RUNNING && run.leaseExpired(now))
			.sorted(Comparator.comparing(RunRecord::updatedAt))
			.limit(limit)
			.toList();
	}

	@Override
	public Page<RunSummary> find(RunQuery query) {
		List<RunSummary> matching = this.runs.values().stream()
			.filter((run) -> query.agentName() == null || query.agentName().equals(run.agentName()))
			.filter((run) -> query.status() == null || query.status() == run.status())
			.filter((run) -> query.correlationId() == null || query.correlationId().equals(run.correlationId()))
			.sorted(Comparator.comparing(RunRecord::createdAt).reversed().thenComparing(RunRecord::id, Comparator.reverseOrder()))
			.map(RunEngine::toSummary)
			.toList();
		int from = Math.min(query.page() * query.size(), matching.size());
		int to = Math.min(from + query.size(), matching.size());
		return new Page<>(new ArrayList<>(matching.subList(from, to)), query.page(), query.size(), matching.size());
	}

	@Override
	public Map<String, Map<RunStatus, Long>> countByAgentAndStatus() {
		Map<String, Map<RunStatus, Long>> result = new TreeMap<>();
		for (RunRecord run : this.runs.values()) {
			result.computeIfAbsent(run.agentName(), (k) -> new EnumMap<>(RunStatus.class)).merge(run.status(), 1L, Long::sum);
		}
		return result;
	}

	@Override
	public List<RunSummary> findRecentFailures(int limit) {
		return this.runs.values().stream()
			.filter((run) -> run.status() == RunStatus.FAILED)
			.sorted(Comparator.comparing(RunRecord::updatedAt).reversed())
			.limit(limit)
			.map(RunEngine::toSummary)
			.toList();
	}

	@Override
	public int deleteFinishedBefore(Instant before) {
		List<RunId> victims = this.runs.values().stream()
			.filter((run) -> run.status().isFinal() && run.updatedAt().isBefore(before))
			.map(RunRecord::id)
			.toList();
		for (RunId id : victims) {
			this.runs.remove(id);
			this.steps.deleteByRun(id);
			this.approvals.deleteByRun(id);
		}
		return victims.size();
	}

	/** Removes everything; for tests. */
	public void clear() {
		this.runs.clear();
	}

}
