package io.github.bnymndev.durableagents.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.spi.AgentRunStore;
import io.github.bnymndev.durableagents.spi.RunRecord;

/**
 * Re-queues runs whose lease expired (the owning instance crashed) and deletes finished runs past
 * the retention period. Scheduled by the starter every {@code durable-agents.reaper-interval}.
 */
public final class RunReaper {

	private static final Log logger = LogFactory.getLog(RunReaper.class);

	private final AgentRunStore runs;

	private final RunEngine engine;

	private final Clock clock;

	private final @Nullable Duration retention;

	private final int batchSize;

	private volatile @Nullable Instant lastRun;

	public RunReaper(AgentRunStore runs, RunEngine engine, Clock clock, @Nullable Duration retention, int batchSize) {
		this.runs = runs;
		this.engine = engine;
		this.clock = clock;
		this.retention = retention;
		this.batchSize = batchSize;
	}

	/** Resumes runs with expired leases. Returns how many were resumed on this instance. */
	public int reap() {
		Instant now = this.clock.instant();
		this.lastRun = now;
		List<RunRecord> expired = this.runs.findExpiredLeases(now, this.batchSize);
		int resumed = 0;
		for (RunRecord run : expired) {
			if (this.engine.activeRuns().contains(run.id())) {
				continue;
			}
			try {
				this.engine.resume(run.id());
				resumed++;
				logger.info("Resumed run " + run.id() + " of agent '" + run.agentName() + "' after its lease expired");
			}
			catch (IllegalStateException ex) {
				logger.debug("Run " + run.id() + " was taken by another instance: " + ex.getMessage());
			}
			catch (RuntimeException ex) {
				logger.warn("Could not resume run " + run.id(), ex);
			}
		}
		return resumed;
	}

	/** Deletes finished runs older than the retention. Returns how many were deleted. */
	public int cleanup() {
		if (this.retention == null || this.retention.isZero()) {
			return 0;
		}
		int deleted = this.runs.deleteFinishedBefore(this.clock.instant().minus(this.retention));
		if (deleted > 0) {
			logger.info("Deleted " + deleted + " finished runs older than " + this.retention);
		}
		return deleted;
	}

	public @Nullable Instant lastRun() {
		return this.lastRun;
	}

	public long staleLeases() {
		return this.runs.findExpiredLeases(this.clock.instant(), 1000).size();
	}

}
