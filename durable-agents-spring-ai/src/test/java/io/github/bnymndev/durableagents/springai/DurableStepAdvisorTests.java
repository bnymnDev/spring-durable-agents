package io.github.bnymndev.durableagents.springai;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import io.github.bnymndev.durableagents.Agent;
import io.github.bnymndev.durableagents.DurableAgent;
import io.github.bnymndev.durableagents.RunResult;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.Steps;
import io.github.bnymndev.durableagents.spi.StepRecord;

import static org.assertj.core.api.Assertions.assertThat;

class DurableStepAdvisorTests {

	private static final Duration WAIT = Duration.ofSeconds(10);

	final AtomicInteger modelCalls = new AtomicInteger();

	final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();

	final AtomicInteger toolCalls = new AtomicInteger();

	/** Like a real provider model: tool-calling options by default, so ChatClient merges tool callbacks into them. */
	final ChatModel model = new ChatModel() {
		@Override
		public ChatResponse call(Prompt prompt) {
			DurableStepAdvisorTests.this.modelCalls.incrementAndGet();
			DurableStepAdvisorTests.this.lastPrompt.set(prompt);
			if (prompt.getOptions() instanceof ToolCallingChatOptions tools && tools.getToolCallbacks() != null
					&& !tools.getToolCallbacks().isEmpty()) {
				// pretend the model asked for the tool and we executed it, like ToolCallingAdvisor would
				tools.getToolCallbacks().get(0).call("{\"city\":\"Berlin\"}");
			}
			return new ChatResponse(List.of(new Generation(new AssistantMessage("answer #" + DurableStepAdvisorTests.this.modelCalls.get()))),
					ChatResponseMetadata.builder().model("fake-1").usage(new DefaultUsage(11, 7)).build());
		}

		@Override
		public ChatOptions getOptions() {
			return ToolCallingChatOptions.builder().build();
		}
	};

	final ToolCallback weather = new ToolCallback() {
		@Override
		public ToolDefinition getToolDefinition() {
			return ToolDefinition.builder().name("weather").description("weather").inputSchema("{}").build();
		}

		@Override
		public String call(String toolInput) {
			DurableStepAdvisorTests.this.toolCalls.incrementAndGet();
			return "{\"temp\":21}";
		}
	};

	final AtomicReference<String> crashAt = new AtomicReference<>();

	EngineHarness harness;

	@AfterEach
	void close() {
		if (this.harness != null) {
			this.harness.close();
		}
	}

	@Test
	void recordsChatCallAsLlmChildStepAndReplaysIt() {
		ChatClient chat = ChatClient.builder(this.model).defaultAdvisors(new DurableStepAdvisor(false)).build();
		ChatAgent agent = new ChatAgent(chat, this.crashAt, null);
		this.harness = new EngineHarness(agent);
		this.crashAt.set("after-classify");

		RunResult crashed = this.harness.engine.start(ChatAgent.class, "hello").await(WAIT);
		assertThat(crashed.status()).isEqualTo(RunStatus.RUNNING);
		assertThat(this.modelCalls).hasValue(1);

		List<StepRecord> steps = this.harness.steps.findByRun(crashed.runId());
		StepRecord llm = steps.stream().filter((s) -> s.stepName().equals("llm")).findFirst().orElseThrow();
		assertThat(llm.kind()).isEqualTo(StepKind.LLM);
		assertThat(llm.model()).isEqualTo("fake-1");
		assertThat(llm.tokensIn()).isEqualTo(11);
		assertThat(llm.tokensOut()).isEqualTo(7);
		assertThat(llm.input()).startsWith("sha256:");
		assertThat(llm.parentKey()).isEqualTo(crashed.runId() + ":classify:0");
		StepRecord classify = steps.stream().filter((s) -> s.stepName().equals("classify")).findFirst().orElseThrow();
		assertThat(classify.kind()).isEqualTo(StepKind.LLM);

		this.crashAt.set(null);
		RunResult resumed = this.harness.engine.resume(crashed.runId()).await(WAIT);
		assertThat(resumed.completed()).isTrue();
		assertThat(resumed.outputAs(String.class)).isEqualTo("answer #1|answer #2");
		assertThat(this.modelCalls).hasValue(2);
	}

	@Test
	void replaysWhenOuterStepReRunsAfterCrashInsideIt() {
		ChatClient chat = ChatClient.builder(this.model).defaultAdvisors(new DurableStepAdvisor(true)).build();
		ChatAgent agent = new ChatAgent(chat, this.crashAt, null);
		this.harness = new EngineHarness(agent);
		this.crashAt.set("inside-classify");

		RunResult crashed = this.harness.engine.start(ChatAgent.class, "hello").await(WAIT);
		assertThat(crashed.status()).isEqualTo(RunStatus.RUNNING);
		assertThat(this.modelCalls).hasValue(1);
		StepRecord llm = this.harness.steps.findByRun(crashed.runId()).get(0);
		assertThat(llm.stepName()).isEqualTo("llm");
		assertThat(llm.input()).contains("hello");

		this.crashAt.set(null);
		RunResult resumed = this.harness.engine.resume(crashed.runId()).await(WAIT);
		assertThat(resumed.completed()).isTrue();
		// the outer step re-ran but the chat call inside it was replayed: only the second step hit the model
		assertThat(this.modelCalls).hasValue(2);
		assertThat(resumed.outputAs(String.class)).isEqualTo("answer #1|answer #2");
	}

	@Test
	void wrapsToolCallbacksIntoToolSteps() {
		ChatClient chat = ChatClient.builder(this.model).defaultAdvisors(new DurableStepAdvisor(false)).build();
		ChatAgent agent = new ChatAgent(chat, this.crashAt, this.weather);
		this.harness = new EngineHarness(agent);

		RunResult result = this.harness.engine.start(ChatAgent.class, "tools").await(WAIT);

		assertThat(result.completed()).isTrue();
		assertThat(this.toolCalls).hasValue(2);
		List<StepRecord> tools = this.harness.steps.findByRun(result.runId()).stream()
			.filter((s) -> s.kind() == StepKind.TOOL)
			.toList();
		assertThat(tools).hasSize(2);
		assertThat(tools.get(0).stepName()).startsWith("tool.weather.");
		assertThat(tools.get(0).input()).contains("Berlin");
		assertThat(tools.get(0).output()).contains("21");
		assertThat(tools.get(0).parentKey()).isEqualTo(result.runId() + ":classify:0/llm:0");
		assertThat(tools.get(0).stepKey()).startsWith(result.runId() + ":classify:0/llm:0/tool.weather.");
		ToolCallingChatOptions options = (ToolCallingChatOptions) this.lastPrompt.get().getOptions();
		assertThat(options.getToolCallbacks().get(0)).isInstanceOf(DurableToolCallback.class);
	}

	@Test
	void isTransparentOutsideARun() {
		ChatClient chat = ChatClient.builder(this.model).defaultAdvisors(new DurableStepAdvisor(false)).build();
		assertThat(chat.prompt().user("x").call().content()).isEqualTo("answer #1");
		assertThat(DurableToolCallback.wrap(this.weather).call("{}")).contains("21");
	}

	@DurableAgent
	static final class ChatAgent implements Agent<String, String> {

		private final ChatClient chat;

		private final AtomicReference<String> crashAt;

		private final ToolCallback tool;

		ChatAgent(ChatClient chat, AtomicReference<String> crashAt, ToolCallback tool) {
			this.chat = chat;
			this.crashAt = crashAt;
			this.tool = tool;
		}

		@Override
		public String run(String input, Steps steps) {
			String first = steps.llm("classify", String.class, () -> {
				String text = ask(input + " one");
				crash("inside-classify");
				return text;
			});
			crash("after-classify");
			String second = steps.llm("summarize", String.class, () -> ask(input + " two"));
			return first + "|" + second;
		}

		private String ask(String text) {
			ChatClient.ChatClientRequestSpec spec = this.chat.prompt().user(text);
			if (this.tool != null) {
				spec = spec.toolCallbacks(this.tool);
			}
			return spec.call().content();
		}

		private void crash(String point) {
			if (point.equals(this.crashAt.get())) {
				throw new Crash(point);
			}
		}

	}

	static final class Crash extends Error {

		Crash(String message) {
			super(message);
		}

	}

}
