package io.github.bnymndev.durableagents.internal.store;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.ApprovalDecision;
import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.spi.ApprovalRecord;
import io.github.bnymndev.durableagents.spi.ApprovalStore;

/** In-memory {@link ApprovalStore}. */
public final class InMemoryApprovalStore implements ApprovalStore {

	private final Map<String, ApprovalRecord> approvals = new ConcurrentHashMap<>();

	@Override
	public void insert(ApprovalRecord approval) {
		if (this.approvals.putIfAbsent(approval.id(), approval) != null) {
			throw new IllegalStateException("Approval " + approval.id() + " already exists");
		}
	}

	@Override
	public void update(ApprovalRecord approval) {
		this.approvals.computeIfPresent(approval.id(), (id, existing) -> approval);
	}

	@Override
	public Optional<ApprovalRecord> find(String id) {
		return Optional.ofNullable(this.approvals.get(id));
	}

	@Override
	public Optional<ApprovalRecord> findByStep(RunId runId, String stepKey) {
		return this.approvals.values().stream()
			.filter((a) -> a.runId().equals(runId) && a.stepKey().equals(stepKey))
			.max(Comparator.comparing(ApprovalRecord::requestedAt));
	}

	@Override
	public List<ApprovalRecord> findPending(@Nullable String role) {
		return this.approvals.values().stream()
			.filter((a) -> a.decision() == ApprovalDecision.PENDING)
			.filter((a) -> role == null || role.equals(a.requiredRole()))
			.sorted(Comparator.comparing(ApprovalRecord::requestedAt))
			.toList();
	}

	@Override
	public List<ApprovalRecord> findPendingExpiredBefore(Instant now) {
		return this.approvals.values().stream()
			.filter((a) -> a.decision() == ApprovalDecision.PENDING && !a.expiresAt().isAfter(now))
			.toList();
	}

	@Override
	public long countPending() {
		return this.approvals.values().stream().filter((a) -> a.decision() == ApprovalDecision.PENDING).count();
	}

	void deleteByRun(RunId runId) {
		this.approvals.values().removeIf((a) -> a.runId().equals(runId));
	}

	public void clear() {
		this.approvals.clear();
	}

}
