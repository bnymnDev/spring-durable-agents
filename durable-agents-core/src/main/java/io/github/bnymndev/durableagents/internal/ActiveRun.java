package io.github.bnymndev.durableagents.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.RunCancelled;
import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.spi.AgentRunStore;

/** A run executing on this instance: lease heartbeat and cancellation flag. */
final class ActiveRun {

	private static final Log logger = LogFactory.getLog(ActiveRun.class);

	private final RunId runId;

	private final AgentRunStore store;

	private final EngineSettings settings;

	private final java.time.Clock clock;

	private final AtomicReference<@Nullable String> cancelReason = new AtomicReference<>();

	private volatile boolean leaseLost;

	private volatile @Nullable ScheduledFuture<?> heartbeatTask;

	ActiveRun(RunId runId, AgentRunStore store, EngineSettings settings, java.time.Clock clock) {
		this.runId = runId;
		this.store = store;
		this.settings = settings;
		this.clock = clock;
	}

	RunId runId() {
		return this.runId;
	}

	void cancel(String reason) {
		this.cancelReason.compareAndSet(null, reason);
	}

	boolean leaseLost() {
		return this.leaseLost;
	}

	void checkCancelled() {
		if (this.leaseLost) {
			throw new RunCancelled(this.runId, "lease lost to another instance");
		}
		String reason = this.cancelReason.get();
		if (reason != null) {
			throw new RunCancelled(this.runId, reason);
		}
	}

	void heartbeat() {
		Instant now = this.clock.instant();
		Duration lease = this.settings.leaseDuration();
		if (!this.store.extendLease(this.runId, this.settings.instanceId(), now.plus(lease), now)) {
			if (!this.leaseLost) {
				logger.warn("Lease of run " + this.runId + " was lost; the run will stop at its next step boundary");
			}
			this.leaseLost = true;
		}
	}

	void heartbeatTask(ScheduledFuture<?> task) {
		this.heartbeatTask = task;
	}

	void stopHeartbeat() {
		ScheduledFuture<?> task = this.heartbeatTask;
		if (task != null) {
			task.cancel(false);
		}
	}

}
