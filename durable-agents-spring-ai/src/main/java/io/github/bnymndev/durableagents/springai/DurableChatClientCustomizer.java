package io.github.bnymndev.durableagents.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;

/** Adds the {@link DurableStepAdvisor} to every {@code ChatClient.Builder} Spring Boot autoconfigures. */
public final class DurableChatClientCustomizer implements ChatClientBuilderCustomizer {

	private final DurableStepAdvisor advisor;

	public DurableChatClientCustomizer(DurableStepAdvisor advisor) {
		this.advisor = advisor;
	}

	@Override
	public void customize(ChatClient.Builder builder) {
		builder.defaultAdvisors(this.advisor);
	}

}
