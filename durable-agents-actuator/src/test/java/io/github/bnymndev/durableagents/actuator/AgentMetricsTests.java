package io.github.bnymndev.durableagents.actuator;

import java.time.Duration;
import java.time.Instant;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.internal.RunEngine;
import io.github.bnymndev.durableagents.spi.ApprovalStore;
import io.github.bnymndev.durableagents.spi.StepExecution;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMetricsTests {

	@Test
	void recordsTimersTokensAndGauges() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		RunEngine engine = Mockito.mock(RunEngine.class);
		ApprovalStore approvals = Mockito.mock(ApprovalStore.class);
		Mockito.when(engine.activeRuns()).thenReturn(java.util.Set.of(RunId.next(), RunId.next()));
		Mockito.when(approvals.countPending()).thenReturn(3L);
		AgentMetrics metrics = new AgentMetrics(registry, engine, approvals);

		RunId runId = RunId.next();
		StepExecution llm = new StepExecution(runId, "triage", runId + ":classify:0/llm:0", "llm", StepKind.LLM,
				runId + ":classify:0", 1, Instant.now(), "gpt-x", 100L, 20L);
		metrics.onStepFinished(llm, Duration.ofMillis(120), null);
		StepExecution tool = new StepExecution(runId, "triage", "k", "tool.weather.abc123", StepKind.TOOL, null, 1, Instant.now(),
				null, null, null);
		metrics.onStepFinished(tool, Duration.ofMillis(5), new IllegalStateException("x"));
		metrics.onRunFinished(runId, "triage", RunStatus.COMPLETED, Duration.ofSeconds(1), null);

		assertThat(registry.get("agent.step.duration").tags("agent", "triage", "step", "llm", "kind", "LLM", "status", "COMPLETED")
			.timer().count()).isEqualTo(1);
		assertThat(registry.get("agent.step.duration").tags("step", "tool.weather", "status", "FAILED").timer().count()).isEqualTo(1);
		assertThat(registry.get("agent.llm.tokens").tags("direction", "in", "model", "gpt-x").counter().count()).isEqualTo(100);
		assertThat(registry.get("agent.llm.tokens").tags("direction", "out").counter().count()).isEqualTo(20);
		assertThat(registry.get("agent.run.duration").tags("status", "COMPLETED").timer().count()).isEqualTo(1);
		assertThat(registry.get("agent.run.active").gauge().value()).isEqualTo(2);
		assertThat(registry.get("agent.approval.pending").gauge().value()).isEqualTo(3);
	}

	@Test
	void observationsNestStepsInsideRuns() {
		TestObservationRegistry registry = TestObservationRegistry.create();
		AgentObservations observations = new AgentObservations(registry);
		RunId runId = RunId.next();

		observations.onRunStarted(runId, "triage", false);
		StepExecution step = new StepExecution(runId, "triage", runId + ":classify:0", "classify", StepKind.LLM, null, 1,
				Instant.now(), null, null, null);
		observations.onStepStarted(step);
		observations.onStepFinished(step.withUsage("gpt-x", 10L, 5L), Duration.ofMillis(3), null);
		observations.onRunFinished(runId, "triage", RunStatus.COMPLETED, Duration.ofMillis(10), null);

		TestObservationRegistryAssert.assertThat(registry)
			.hasNumberOfObservationsEqualTo(2)
			.hasObservationWithNameEqualTo("agent.step")
			.that()
			.hasLowCardinalityKeyValue("gen_ai.response.model", "gpt-x")
			.hasHighCardinalityKeyValue("gen_ai.usage.input_tokens", "10")
			.hasBeenStopped();
		TestObservationRegistryAssert.assertThat(registry).hasObservationWithNameEqualTo("agent.run").that()
			.hasLowCardinalityKeyValue("status", "COMPLETED").hasBeenStopped();
	}

}
