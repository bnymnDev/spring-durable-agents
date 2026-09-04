package io.github.bnymndev.durableagents.actuator;

import java.time.Duration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.internal.RunEngine;
import io.github.bnymndev.durableagents.spi.ApprovalStore;
import io.github.bnymndev.durableagents.spi.RunListener;
import io.github.bnymndev.durableagents.spi.StepExecution;
import io.github.bnymndev.durableagents.spi.StepListener;

/**
 * Micrometer metrics:
 * <ul>
 * <li>{@code agent.step.duration} (timer; tags {@code agent}, {@code step}, {@code kind}, {@code status})</li>
 * <li>{@code agent.run.duration} (timer; tags {@code agent}, {@code status})</li>
 * <li>{@code agent.llm.tokens} (counter; tags {@code agent}, {@code model}, {@code direction})</li>
 * <li>{@code agent.run.active} (gauge, runs executing on this instance)</li>
 * <li>{@code agent.approval.pending} (gauge)</li>
 * </ul>
 */
public class AgentMetrics implements StepListener, RunListener {

	private final MeterRegistry registry;

	public AgentMetrics(MeterRegistry registry, RunEngine engine, ApprovalStore approvals) {
		this.registry = registry;
		Gauge.builder("agent.run.active", engine, (e) -> e.activeRuns().size())
			.description("Runs executing on this instance")
			.register(registry);
		Gauge.builder("agent.approval.pending", approvals, ApprovalStore::countPending)
			.description("Approvals waiting for a decision")
			.register(registry);
	}

	@Override
	public void onStepFinished(StepExecution step, Duration duration, @Nullable Throwable error) {
		Timer.builder("agent.step.duration")
			.description("Duration of durable steps")
			.tags("agent", step.agentName(), "step", metricStepName(step), "kind", step.kind().name(),
					"status", (error == null) ? "COMPLETED" : "FAILED")
			.register(this.registry)
			.record(duration);
		if (step.tokensIn() != null || step.tokensOut() != null) {
			String model = (step.model() != null) ? step.model() : "unknown";
			if (step.tokensIn() != null) {
				tokens(step.agentName(), model, "in").increment(step.tokensIn());
			}
			if (step.tokensOut() != null) {
				tokens(step.agentName(), model, "out").increment(step.tokensOut());
			}
		}
	}

	@Override
	public void onRunFinished(RunId runId, String agentName, RunStatus status, Duration duration, @Nullable Throwable error) {
		Timer.builder("agent.run.duration")
			.description("Duration of one execution of a run")
			.tags("agent", agentName, "status", status.name())
			.register(this.registry)
			.record(duration);
	}

	private Counter tokens(String agent, String model, String direction) {
		return Counter.builder("agent.llm.tokens")
			.description("LLM tokens consumed by durable agents")
			.tags("agent", agent, "model", model, "direction", direction)
			.register(this.registry);
	}

	/** Tool steps carry an argument hash in their name; strip it to keep the tag cardinality bounded. */
	static String metricStepName(StepExecution step) {
		String name = step.stepName();
		if (name.startsWith("tool.")) {
			int last = name.lastIndexOf('.');
			return (last > 5) ? name.substring(0, last) : name;
		}
		return name;
	}

}
