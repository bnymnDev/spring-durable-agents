package io.github.bnymndev.durableagents.springai;

import org.jspecify.annotations.Nullable;

/**
 * What the durable advisor stores for one {@code ChatClient} call: enough to replay the assistant's
 * answer without calling the model again.
 *
 * @param text the assistant text, or empty when the model returned none
 * @param model the model that answered
 * @param promptTokens prompt tokens reported by the provider
 * @param completionTokens completion tokens reported by the provider
 * @param finishReason the provider's finish reason
 */
public record LlmStepResult(String text, @Nullable String model, @Nullable Long promptTokens, @Nullable Long completionTokens,
		@Nullable String finishReason) {
}
