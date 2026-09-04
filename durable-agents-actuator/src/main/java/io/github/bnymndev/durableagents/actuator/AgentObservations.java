package io.github.bnymndev.durableagents.actuator;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.spi.RunListener;
import io.github.bnymndev.durableagents.spi.StepExecution;
import io.github.bnymndev.durableagents.spi.StepListener;

/**
 * One Micrometer observation (and so one OpenTelemetry span, when tracing is configured) per run
 * and per step, nested. LLM steps carry GenAI semantic-convention attributes
 * ({@code gen_ai.operation.name}, {@code gen_ai.response.model}, {@code gen_ai.usage.input_tokens},
 * {@code gen_ai.usage.output_tokens}).
 */
public class AgentObservations implements StepListener, RunListener {

	private final ObservationRegistry registry;

	private final ThreadLocal<Deque<Scoped>> stack = ThreadLocal.withInitial(ArrayDeque::new);

	public AgentObservations(ObservationRegistry registry) {
		this.registry = registry;
	}

	@Override
	public void onRunStarted(RunId runId, String agentName, boolean resumed) {
		Observation observation = Observation.createNotStarted("agent.run", this.registry)
			.contextualName("agent " + agentName)
			.lowCardinalityKeyValue("agent", agentName)
			.lowCardinalityKeyValue("resumed", String.valueOf(resumed))
			.highCardinalityKeyValue("run.id", runId.value())
			.start();
		this.stack.get().push(new Scoped(observation, observation.openScope()));
	}

	@Override
	public void onRunFinished(RunId runId, String agentName, RunStatus status, Duration duration, @Nullable Throwable error) {
		Scoped scoped = pop();
		if (scoped == null) {
			return;
		}
		scoped.observation.lowCardinalityKeyValue("status", status.name());
		if (error != null) {
			scoped.observation.error(error);
		}
		scoped.close();
	}

	@Override
	public void onStepStarted(StepExecution step) {
		Observation observation = Observation.createNotStarted("agent.step", this.registry)
			.contextualName(step.stepName())
			.lowCardinalityKeyValue("agent", step.agentName())
			.lowCardinalityKeyValue("kind", step.kind().name())
			.highCardinalityKeyValue("step.key", step.stepKey())
			.highCardinalityKeyValue("step.attempt", String.valueOf(step.attempt()))
			.highCardinalityKeyValue("run.id", step.runId().value());
		if (step.kind() == StepKind.LLM) {
			observation.lowCardinalityKeyValue("gen_ai.operation.name", "chat");
		}
		observation.start();
		this.stack.get().push(new Scoped(observation, observation.openScope()));
	}

	@Override
	public void onStepFinished(StepExecution step, Duration duration, @Nullable Throwable error) {
		Scoped scoped = pop();
		if (scoped == null) {
			return;
		}
		scoped.observation.lowCardinalityKeyValue("status", (error == null) ? "COMPLETED" : "FAILED");
		if (step.model() != null) {
			scoped.observation.lowCardinalityKeyValue("gen_ai.response.model", step.model());
		}
		if (step.tokensIn() != null) {
			scoped.observation.highCardinalityKeyValue("gen_ai.usage.input_tokens", String.valueOf(step.tokensIn()));
		}
		if (step.tokensOut() != null) {
			scoped.observation.highCardinalityKeyValue("gen_ai.usage.output_tokens", String.valueOf(step.tokensOut()));
		}
		if (error != null) {
			scoped.observation.error(error);
		}
		scoped.close();
	}

	private @Nullable Scoped pop() {
		Deque<Scoped> deque = this.stack.get();
		return deque.isEmpty() ? null : deque.pop();
	}

	private record Scoped(Observation observation, Observation.Scope scope) {

		void close() {
			this.scope.close();
			this.observation.stop();
		}

	}

}
