package io.github.bnymndev.durableagents.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.RunId;

/** Store for approval requests. */
public interface ApprovalStore {

	void insert(ApprovalRecord approval);

	void update(ApprovalRecord approval);

	Optional<ApprovalRecord> find(String id);

	Optional<ApprovalRecord> findByStep(RunId runId, String stepKey);

	List<ApprovalRecord> findPending(@Nullable String role);

	List<ApprovalRecord> findPendingExpiredBefore(Instant now);

	long countPending();

}
