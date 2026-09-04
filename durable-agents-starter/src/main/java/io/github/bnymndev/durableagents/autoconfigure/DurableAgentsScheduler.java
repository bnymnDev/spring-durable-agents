package io.github.bnymndev.durableagents.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

import io.github.bnymndev.durableagents.Approvals;
import io.github.bnymndev.durableagents.internal.RunReaper;

/** Schedules the reaper, retention cleanup and approval expiry on virtual threads. */
public final class DurableAgentsScheduler implements SmartLifecycle {

	private static final Log logger = LogFactory.getLog(DurableAgentsScheduler.class);

	private final RunReaper reaper;

	private final @Nullable Approvals approvals;

	private final DurableAgentsProperties properties;

	private final SimpleAsyncTaskScheduler scheduler = new SimpleAsyncTaskScheduler();

	private final List<ScheduledFuture<?>> tasks = new ArrayList<>();

	private volatile boolean running;

	public DurableAgentsScheduler(RunReaper reaper, @Nullable Approvals approvals, DurableAgentsProperties properties) {
		this.reaper = reaper;
		this.approvals = approvals;
		this.properties = properties;
		this.scheduler.setThreadNamePrefix("durable-agents-");
		this.scheduler.setVirtualThreads(true);
	}

	@Override
	public void start() {
		this.tasks.add(this.scheduler.scheduleWithFixedDelay(guarded("reaper", this.reaper::reap),
				this.properties.getReaperInterval()));
		if (!this.properties.getRetention().isZero()) {
			this.tasks.add(this.scheduler.scheduleWithFixedDelay(guarded("retention", this.reaper::cleanup),
					this.properties.getRetentionInterval()));
		}
		if (this.approvals != null) {
			Duration interval = this.properties.getApproval().getExpiryInterval();
			this.tasks.add(this.scheduler.scheduleWithFixedDelay(guarded("approval-expiry", this.approvals::expire), interval));
		}
		this.running = true;
	}

	private static Runnable guarded(String name, Runnable task) {
		return () -> {
			try {
				task.run();
			}
			catch (RuntimeException ex) {
				logger.warn("Scheduled durable-agents task '" + name + "' failed", ex);
			}
		};
	}

	@Override
	public void stop() {
		this.tasks.forEach((t) -> t.cancel(false));
		this.tasks.clear();
		this.scheduler.close();
		this.running = false;
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	@Override
	public int getPhase() {
		return SmartLifecycle.DEFAULT_PHASE - 1024;
	}

}
