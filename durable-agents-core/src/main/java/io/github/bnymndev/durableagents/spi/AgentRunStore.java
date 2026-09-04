package io.github.bnymndev.durableagents.spi;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.bnymndev.durableagents.Page;
import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunQuery;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.RunSummary;

/**
 * Store for runs. Implementations participate in the caller's transaction when one is active and
 * never open long transactions themselves.
 */
public interface AgentRunStore {

	void insert(RunRecord run);

	Optional<RunRecord> find(RunId id);

	/** Replaces the mutable columns (status, output, error, lease, updatedAt). */
	void update(RunRecord run);

	/**
	 * Atomically takes the lease of a run when it is free: the run is not {@code RUNNING}, or its
	 * lease has expired. Returns {@code false} when another instance holds it.
	 */
	boolean tryAcquireLease(RunId id, String owner, Instant until, Instant now);

	/** Extends the lease held by {@code owner}. Returns {@code false} when the lease was lost. */
	boolean extendLease(RunId id, String owner, Instant until, Instant now);

	/** Runs that are {@code RUNNING} with an expired lease, i.e. crashed somewhere. */
	List<RunRecord> findExpiredLeases(Instant now, int limit);

	Page<RunSummary> find(RunQuery query);

	/** Counts per agent and status, for the actuator endpoint. */
	Map<String, Map<RunStatus, Long>> countByAgentAndStatus();

	List<RunSummary> findRecentFailures(int limit);

	/** Deletes final runs (and their steps and approvals) whose last update is before {@code before}. */
	int deleteFinishedBefore(Instant before);

}
