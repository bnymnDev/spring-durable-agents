package io.github.bnymndev.durableagents.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import tools.jackson.databind.json.JsonMapper;

/**
 * A {@link ChatModel} that answers from a script. Queue answers with {@link #respondWith(String...)}
 * or {@link #respondWithJson(Object)}; each call consumes one. When the queue is empty the
 * {@link #fallback(Function)} answers, by default with {@code "fake response"}. Every prompt is
 * recorded in {@link #prompts()}.
 */
public class FakeChatModel implements ChatModel {

	public static final String MODEL_NAME = "fake-chat-model";

	private final Queue<String> responses = new ConcurrentLinkedQueue<>();

	private final List<Prompt> prompts = new ArrayList<>();

	private final JsonMapper mapper = JsonMapper.builder().build();

	private Function<Prompt, String> fallback = (prompt) -> "fake response";

	private int promptTokens = 10;

	private int completionTokens = 5;

	public FakeChatModel respondWith(String... texts) {
		this.responses.addAll(List.of(texts));
		return this;
	}

	/** Queues the JSON form of {@code value}, for agents that call {@code .entity(Class)}. */
	public FakeChatModel respondWithJson(Object value) {
		this.responses.add(this.mapper.writeValueAsString(value));
		return this;
	}

	public FakeChatModel fallback(Function<Prompt, String> answer) {
		this.fallback = answer;
		return this;
	}

	public FakeChatModel usage(int promptTokens, int completionTokens) {
		this.promptTokens = promptTokens;
		this.completionTokens = completionTokens;
		return this;
	}

	/** Prompts received so far, oldest first. */
	public synchronized List<Prompt> prompts() {
		return List.copyOf(this.prompts);
	}

	public int calls() {
		return prompts().size();
	}

	/** Forgets recorded prompts and queued answers. */
	public synchronized void reset() {
		this.prompts.clear();
		this.responses.clear();
	}

	@Override
	public synchronized ChatResponse call(Prompt prompt) {
		this.prompts.add(prompt);
		String text = this.responses.poll();
		if (text == null) {
			text = this.fallback.apply(prompt);
		}
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), ChatResponseMetadata.builder()
			.model(MODEL_NAME)
			.usage(new DefaultUsage(this.promptTokens, this.completionTokens))
			.build());
	}

	@Override
	public ChatOptions getOptions() {
		return ToolCallingChatOptions.builder().build();
	}

}
