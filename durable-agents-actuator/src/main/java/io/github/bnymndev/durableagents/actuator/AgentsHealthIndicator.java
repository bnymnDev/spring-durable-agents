package io.github.bnymndev.durableagents.actuator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import io.github.bnymndev.durableagents.internal.RunReaper;
import io.github.bnymndev.durableagents.spi.AgentRunStore;

/**
 * Health of the durable-agents subsystem: the store answers, the reaper ran recently, and the
 * number of runs with stale leases (crashed somewhere, waiting for a resume).
 */
public class AgentsHealthIndicator implements HealthIndicator {

	private final AgentRunStore runs;

	private final @Nullable RunReaper reaper;

	private final Duration reaperInterval;

	private final Clock clock;

	public AgentsHealthIndicator(AgentRunStore runs, @Nullable RunReaper reaper, Duration reaperInterval, Clock clock) {
		this.runs = runs;
		this.reaper = reaper;
		this.reaperInterval = reaperInterval;
		this.clock = clock;
	}

	@Override
	public Health health() {
		Health.Builder health = Health.up();
		try {
			long stale = this.runs.findExpiredLeases(this.clock.instant(), 1000).size();
			health.withDetail("staleLeases", stale);
			health.withDetail("agents", this.runs.countByAgentAndStatus().size());
		}
		catch (RuntimeException ex) {
			return Health.down(ex).withDetail("store", "unreachable").build();
		}
		if (this.reaper != null) {
			Instant last = this.reaper.lastRun();
			health.withDetail("reaperLastRun", (last != null) ? last.toString() : "never");
			if (last != null && last.plus(this.reaperInterval.multipliedBy(3)).isBefore(this.clock.instant())) {
				health.status("DEGRADED").withDetail("reaper", "no run for more than 3 intervals");
			}
		}
		return health.build();
	}

}
