package io.github.bnymndev.durableagents.autoconfigure;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.bnymndev.durableagents.springai.DurableChatClientCustomizer;
import io.github.bnymndev.durableagents.springai.DurableStepAdvisor;

/** Registers the durable {@code ChatClient} advisor when Spring AI and {@code durable-agents-spring-ai} are present. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ ChatClient.class, DurableStepAdvisor.class })
class DurableAgentsSpringAiConfiguration {

	@Bean
	@ConditionalOnMissingBean
	DurableStepAdvisor durableStepAdvisor(DurableAgentsProperties properties) {
		return new DurableStepAdvisor(properties.getObservability().isRecordPrompts());
	}

	@Bean
	@ConditionalOnMissingBean
	DurableChatClientCustomizer durableChatClientCustomizer(DurableStepAdvisor advisor) {
		return new DurableChatClientCustomizer(advisor);
	}

}
