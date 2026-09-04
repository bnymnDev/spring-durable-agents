package io.github.bnymndev.durableagents.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.github.bnymndev.durableagents.Agent;
import io.github.bnymndev.durableagents.DurableAgent;
import io.github.bnymndev.durableagents.Step;
import io.github.bnymndev.durableagents.Steps;
import io.github.bnymndev.durableagents.support.Recording;
import io.github.bnymndev.durableagents.support.TestEngineConfig;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepPlacementValidatorTests {

	@Test
	void stepOnAgentClassFailsStartup() {
		assertThatThrownBy(() -> new AnnotationConfigApplicationContext(TestEngineConfig.class, Recording.class, BadAgentConfig.class)
			.close())
			.isInstanceOf(BeanCreationException.class)
			.hasMessageContaining("@Step methods are not allowed on the @DurableAgent class")
			.hasMessageContaining("classify");
	}

	@Test
	void privateStepMethodFailsStartup() {
		assertThatThrownBy(() -> new AnnotationConfigApplicationContext(TestEngineConfig.class, Recording.class, PrivateStepConfig.class)
			.close())
			.isInstanceOf(BeanCreationException.class)
			.hasMessageContaining("must be public");
	}

	@Configuration(proxyBeanMethods = false)
	@Import(BadAgent.class)
	static class BadAgentConfig {
	}

	@DurableAgent("bad")
	static class BadAgent implements Agent<String, String> {

		@Override
		public String run(String input, Steps steps) {
			return classify(input);
		}

		@Step
		public String classify(String input) {
			return input;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class PrivateStepConfig {

		@Bean
		PrivateStepBean privateStepBean() {
			return new PrivateStepBean();
		}

	}

	static class PrivateStepBean {

		@Step
		private String hidden() {
			return "x";
		}

	}

}
