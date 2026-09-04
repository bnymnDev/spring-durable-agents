package io.github.bnymndev.durableagents.test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.bnymndev.durableagents.AgentRuns;
import io.github.bnymndev.durableagents.Approvals;
import io.github.bnymndev.durableagents.spi.StepStore;
import io.github.bnymndev.durableagents.springai.DurableStepAdvisor;

/** Beans of the {@link DurableAgentTest} slice. */
@TestConfiguration(proxyBeanMethods = false)
public class DurableAgentTestConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public CrashSimulator crashSimulator() {
		return new CrashSimulator();
	}

	@Bean
	@ConditionalOnMissingBean
	public DurableAgentTester durableAgentTester(AgentRuns runs, Approvals approvals, StepStore steps, CrashSimulator crashes) {
		return new DurableAgentTester(runs, approvals, steps, crashes);
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ ChatModel.class, DurableStepAdvisor.class })
	static class FakeChatConfiguration {

		@Bean
		@ConditionalOnMissingBean(ChatModel.class)
		FakeChatModel fakeChatModel() {
			return new FakeChatModel();
		}

		@Bean
		@ConditionalOnMissingBean
		ChatClient.Builder chatClientBuilder(ChatModel chatModel, ObjectProvider<DurableStepAdvisor> advisor) {
			ChatClient.Builder builder = ChatClient.builder(chatModel);
			advisor.ifAvailable(builder::defaultAdvisors);
			return builder;
		}

		@Bean
		@ConditionalOnMissingBean
		ChatClient chatClient(ChatClient.Builder builder) {
			return builder.build();
		}

	}

}
