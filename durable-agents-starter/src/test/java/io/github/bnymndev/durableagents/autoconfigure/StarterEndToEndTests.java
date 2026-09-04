package io.github.bnymndev.durableagents.autoconfigure;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import io.github.bnymndev.durableagents.Agent;
import io.github.bnymndev.durableagents.AgentRuns;
import io.github.bnymndev.durableagents.Approvals;
import io.github.bnymndev.durableagents.DurableAgent;
import io.github.bnymndev.durableagents.PendingApproval;
import io.github.bnymndev.durableagents.RunResult;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.Step;
import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.Steps;
import io.github.bnymndev.durableagents.spi.StepRecord;
import io.github.bnymndev.durableagents.spi.StepStore;
import io.github.bnymndev.durableagents.springai.DurableStepAdvisor;

import static org.assertj.core.api.Assertions.assertThat;

/** The whole starter on H2: agent, @Step bean, ChatClient advisor, approval, crash and resume. */
@SpringBootTest(properties = { "spring.datasource.url=jdbc:h2:mem:e2e;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"durable-agents.scheduling.enabled=false", "durable-agents.strict-replay=true", "spring.threads.virtual.enabled=true" })
class StarterEndToEndTests {

	@Autowired
	AgentRuns runs;

	@Autowired
	Approvals approvals;

	@Autowired
	StepStore steps;

	@Autowired
	Fixtures fixtures;

	@Test
	void crashResumeApproveComplete() {
		this.fixtures.crash.set(true);
		RunResult crashed = this.runs.start(TriageAgent.class, new Ticket("T-1", "Printer on fire"), "T-1").await(Duration.ofSeconds(10));
		assertThat(crashed.status()).isEqualTo(RunStatus.RUNNING);
		assertThat(this.fixtures.modelCalls).hasValue(1);
		this.fixtures.crash.set(false);

		RunResult suspended = this.runs.resume(crashed.runId()).await(Duration.ofSeconds(10));
		assertThat(suspended.status()).isEqualTo(RunStatus.SUSPENDED);
		assertThat(this.fixtures.modelCalls).hasValue(1);
		assertThat(this.fixtures.similarCalls).hasValue(1);

		List<PendingApproval> pending = this.approvals.pendingForRole("SUPPORT_LEAD");
		assertThat(pending).singleElement().satisfies((a) -> {
			assertThat(a.correlationId()).isEqualTo("T-1");
			assertThat(a.stepName()).isEqualTo("apply");
		});
		RunResult done = this.approvals.approve(pending.get(0).id(), "lead", null).await(Duration.ofSeconds(10));

		assertThat(done.completed()).isTrue();
		assertThat(done.outputAs(Resolution.class).text()).isEqualTo("Classification[category=hardware, urgent=true] with 2 similar");
		List<StepRecord> recorded = this.steps.findByRun(done.runId());
		assertThat(recorded).extracting(StepRecord::stepName)
			.containsExactly("classify", DurableStepAdvisor.STEP_NAME, "triageSteps.findSimilar", "apply");
		assertThat(recorded.get(1).kind()).isEqualTo(StepKind.LLM);
		assertThat(recorded.get(1).model()).isEqualTo("fake-model");
		assertThat(recorded.get(1).tokensIn()).isEqualTo(5L);
		assertThat(this.runs.summary(done.runId()).orElseThrow().correlationId()).isEqualTo("T-1");
	}

	record Ticket(String id, String body) {
	}

	record Classification(String category, boolean urgent) {
	}

	record Resolution(String text) {
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class Fixtures {

		final AtomicBoolean crash = new AtomicBoolean();

		final AtomicInteger modelCalls = new AtomicInteger();

		final AtomicInteger similarCalls = new AtomicInteger();

		@Bean
		ChatModel chatModel() {
			return new ChatModel() {
				@Override
				public ChatResponse call(Prompt prompt) {
					Fixtures.this.modelCalls.incrementAndGet();
					return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"category\":\"hardware\",\"urgent\":true}"))),
							ChatResponseMetadata.builder().model("fake-model").usage(new DefaultUsage(5, 9)).build());
				}

				@Override
				public ChatOptions getOptions() {
					return ToolCallingChatOptions.builder().build();
				}
			};
		}

		@Bean
		TriageAgent triageAgent(ChatClient.Builder chatClientBuilder, TriageSteps triageSteps) {
			return new TriageAgent(chatClientBuilder.build(), triageSteps, this);
		}

		@Bean
		TriageSteps triageSteps() {
			return new TriageSteps(this);
		}

	}

	@DurableAgent("triage")
	static class TriageAgent implements Agent<Ticket, Resolution> {

		private final ChatClient chat;

		private final TriageSteps triage;

		private final Fixtures fixtures;

		TriageAgent(ChatClient chat, TriageSteps triage, Fixtures fixtures) {
			this.chat = chat;
			this.triage = triage;
			this.fixtures = fixtures;
		}

		@Override
		public Resolution run(Ticket ticket, Steps steps) {
			Classification c = steps.llm("classify", Classification.class,
					() -> this.chat.prompt().user("Classify: " + ticket.body()).call().entity(Classification.class));
			if (this.fixtures.crash.get()) {
				throw new OutOfMemoryError("simulated");
			}
			List<String> similar = this.triage.findSimilar(c);
			return steps.approval("SUPPORT_LEAD", Duration.ofHours(1)).run("apply", Resolution.class,
					() -> new Resolution(c + " with " + similar.size() + " similar"));
		}

	}

	@Component
	static class TriageSteps {

		private final Fixtures fixtures;

		TriageSteps(Fixtures fixtures) {
			this.fixtures = fixtures;
		}

		@Step
		public List<String> findSimilar(Classification c) {
			this.fixtures.similarCalls.incrementAndGet();
			return List.of("T-7", "T-9");
		}

	}

}
