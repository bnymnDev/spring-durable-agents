package io.github.bnymndev.durableagents.springai;

import java.lang.reflect.Type;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import io.github.bnymndev.durableagents.Agent;
import io.github.bnymndev.durableagents.internal.AgentRegistry;
import io.github.bnymndev.durableagents.internal.EngineSettings;
import io.github.bnymndev.durableagents.internal.RunEngine;
import io.github.bnymndev.durableagents.internal.store.InMemoryAgentRunStore;
import io.github.bnymndev.durableagents.internal.store.InMemoryApprovalStore;
import io.github.bnymndev.durableagents.internal.store.InMemoryStepStore;
import io.github.bnymndev.durableagents.spi.StepCodec;

/** A hand-wired engine with in-memory stores for module tests that do not need a Spring context. */
final class EngineHarness implements AutoCloseable {

	final InMemoryStepStore steps = new InMemoryStepStore();

	final InMemoryApprovalStore approvals = new InMemoryApprovalStore();

	final InMemoryAgentRunStore runs = new InMemoryAgentRunStore(this.steps, this.approvals);

	final GenericApplicationContext context = new GenericApplicationContext();

	final RunEngine engine;

	EngineHarness(Agent<?, ?>... agents) {
		StaticListableBeanFactory beans = new StaticListableBeanFactory();
		for (Agent<?, ?> agent : agents) {
			beans.addBean(agent.getClass().getSimpleName(), agent);
		}
		this.context.refresh();
		this.engine = new RunEngine(new AgentRegistry(beans), this.runs, this.steps, this.approvals, new JacksonCodec(),
				this.context, List.of(), List.of(), Executors.newVirtualThreadPerTaskExecutor(),
				new EngineSettings("test", Duration.ofSeconds(5), false), Clock.systemUTC());
	}

	@Override
	public void close() {
		this.engine.close();
		this.context.close();
	}

	static final class JacksonCodec implements StepCodec {

		private final JsonMapper mapper = JsonMapper.builder().build();

		@Override
		public @Nullable String encode(@Nullable Object value) {
			return (value == null) ? null : this.mapper.writeValueAsString(value);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T extends @Nullable Object> @Nullable T decode(@Nullable String encoded, Type type) {
			if (encoded == null || type == Void.class) {
				return null;
			}
			return (T) this.mapper.readValue(encoded, this.mapper.constructType(type));
		}

	}

}
