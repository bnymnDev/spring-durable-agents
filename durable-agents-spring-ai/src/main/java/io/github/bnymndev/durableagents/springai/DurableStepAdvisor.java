package io.github.bnymndev.durableagents.springai;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.internal.DefaultSteps;
import io.github.bnymndev.durableagents.internal.RetryPolicy;
import io.github.bnymndev.durableagents.internal.StepSpec;
import io.github.bnymndev.durableagents.internal.StepsHolder;

/**
 * A {@code ChatClient} advisor that turns every call made while a run is active into a durable
 * child step of kind {@code LLM}: model, token usage, latency and the prompt hash are recorded, the
 * assistant text is stored, and on resume a completed call is replayed without contacting the model.
 * Tool callbacks attached to the request are wrapped so that every tool execution during the turn
 * becomes a durable step of kind {@code TOOL}.
 *
 * <p>Outside a run the advisor is transparent.
 */
public final class DurableStepAdvisor implements CallAdvisor {

	public static final String NAME = "durable-agents";

	/** Name of the child step recorded per chat call. */
	public static final String STEP_NAME = "llm";

	private final boolean recordPrompts;

	private final int order;

	/**
	 * Runs before Spring AI's {@code ToolCallingAdvisor}, so one chat call including its tool round
	 * trips is one step and the tool callbacks the advisor executes are the durable wrappers.
	 */
	public static final int DEFAULT_ORDER = ToolCallingAdvisor.DEFAULT_ORDER - 100;

	public DurableStepAdvisor(boolean recordPrompts) {
		this(recordPrompts, DEFAULT_ORDER);
	}

	public DurableStepAdvisor(boolean recordPrompts, int order) {
		this.recordPrompts = recordPrompts;
		this.order = order;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		DefaultSteps steps = StepsHolder.current();
		if (steps == null) {
			return chain.nextCall(request);
		}
		ChatClientRequest durableRequest = wrapTools(request);
		String prompt = request.prompt().getContents();
		String input = this.recordPrompts ? prompt : "sha256:" + Hashes.sha256(prompt);
		StepSpec spec = StepSpec.named(STEP_NAME).kind(StepKind.LLM).type(LlmStepResult.class).replayGuard(false).input(input)
			.retry(RetryPolicy.NONE);
		ChatClientResponse[] live = new ChatClientResponse[1];
		LlmStepResult stored = steps.execute(spec, () -> {
			ChatClientResponse response = chain.nextCall(durableRequest);
			live[0] = response;
			LlmStepResult result = toResult(response.chatResponse());
			steps.recordLlmUsage(result.model(), result.promptTokens(), result.completionTokens());
			return result;
		});
		if (live[0] != null) {
			return live[0];
		}
		return replay(request, stored);
	}

	private static ChatClientRequest wrapTools(ChatClientRequest request) {
		Prompt prompt = request.prompt();
		ChatOptions options = prompt.getOptions();
		if (!(options instanceof ToolCallingChatOptions toolOptions)) {
			return request;
		}
		List<ToolCallback> callbacks = toolOptions.getToolCallbacks();
		if (callbacks == null || callbacks.isEmpty()) {
			return request;
		}
		List<ToolCallback> wrapped = callbacks.stream().map(DurableToolCallback::wrap).toList();
		ToolCallingChatOptions durableOptions = toolOptions.mutate().toolCallbacks(wrapped).build();
		return request.mutate().prompt(prompt.mutate().chatOptions(durableOptions).build()).build();
	}

	static LlmStepResult toResult(@Nullable ChatResponse response) {
		if (response == null) {
			return new LlmStepResult("", null, null, null, null);
		}
		Generation generation = (response.getResults().isEmpty()) ? null : response.getResult();
		String text = (generation != null && generation.getOutput().getText() != null) ? generation.getOutput().getText() : "";
		String finish = (generation != null && generation.getMetadata() != null) ? generation.getMetadata().getFinishReason() : null;
		ChatResponseMetadata metadata = response.getMetadata();
		String model = (metadata != null && metadata.getModel() != null && !metadata.getModel().isEmpty()) ? metadata.getModel() : null;
		Usage usage = (metadata != null) ? metadata.getUsage() : null;
		Long in = (usage != null && usage.getPromptTokens() != null) ? usage.getPromptTokens().longValue() : null;
		Long out = (usage != null && usage.getCompletionTokens() != null) ? usage.getCompletionTokens().longValue() : null;
		return new LlmStepResult(text, model, in, out, finish);
	}

	static ChatClientResponse replay(ChatClientRequest request, @Nullable LlmStepResult stored) {
		LlmStepResult result = (stored != null) ? stored : new LlmStepResult("", null, null, null, null);
		ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder();
		if (result.model() != null) {
			metadata.model(result.model());
		}
		if (result.promptTokens() != null || result.completionTokens() != null) {
			int in = (result.promptTokens() != null) ? result.promptTokens().intValue() : 0;
			int out = (result.completionTokens() != null) ? result.completionTokens().intValue() : 0;
			metadata.usage(new DefaultUsage(in, out));
		}
		ChatGenerationMetadata generationMetadata = (result.finishReason() != null)
				? ChatGenerationMetadata.builder().finishReason(result.finishReason()).build() : ChatGenerationMetadata.NULL;
		ChatResponse chatResponse = ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage(result.text()), generationMetadata)))
			.metadata(metadata.build())
			.build();
		return ChatClientResponse.builder().chatResponse(chatResponse).context(request.context()).build();
	}

}
