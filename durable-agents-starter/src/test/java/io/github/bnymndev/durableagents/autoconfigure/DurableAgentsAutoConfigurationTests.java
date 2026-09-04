package io.github.bnymndev.durableagents.autoconfigure;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.bnymndev.durableagents.AgentRuns;
import io.github.bnymndev.durableagents.Approvals;
import io.github.bnymndev.durableagents.internal.RunEngine;
import io.github.bnymndev.durableagents.internal.RunReaper;
import io.github.bnymndev.durableagents.internal.StepAdvisor;
import io.github.bnymndev.durableagents.internal.StepPlacementValidator;
import io.github.bnymndev.durableagents.internal.store.InMemoryAgentRunStore;
import io.github.bnymndev.durableagents.internal.store.InMemoryStepStore;
import io.github.bnymndev.durableagents.jdbc.JdbcAgentRunStore;
import io.github.bnymndev.durableagents.jdbc.SqlDialect;
import io.github.bnymndev.durableagents.spi.AgentRunStore;
import io.github.bnymndev.durableagents.spi.StepStore;
import io.github.bnymndev.durableagents.springai.DurableChatClientCustomizer;
import io.github.bnymndev.durableagents.springai.DurableStepAdvisor;

import static org.assertj.core.api.Assertions.assertThat;

class DurableAgentsAutoConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(DurableAgentsAutoConfiguration.class, JacksonAutoConfiguration.class));

	@Test
	void memoryStoreWiresTheEngine() {
		this.runner.withPropertyValues("durable-agents.store=memory").run((context) -> {
			assertThat(context).hasSingleBean(AgentRuns.class).hasSingleBean(Approvals.class).hasSingleBean(RunReaper.class)
				.hasSingleBean(StepAdvisor.class).hasSingleBean(StepPlacementValidator.class)
				.hasSingleBean(DurableAgentsScheduler.class).hasSingleBean(DurableStepAdvisor.class)
				.hasSingleBean(DurableChatClientCustomizer.class);
			assertThat(context).getBean(AgentRunStore.class).isInstanceOf(InMemoryAgentRunStore.class);
			assertThat(context).getBean(StepStore.class).isInstanceOf(InMemoryStepStore.class);
			assertThat(context.getBean(DurableAgentsScheduler.class).isRunning()).isTrue();
		});
	}

	@Test
	void jdbcStoreIsDefaultWhenADataSourceExistsAndMigrates() {
		this.runner.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
			.withPropertyValues("spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
					"durable-agents.scheduling.enabled=false")
			.run((context) -> {
				assertThat(context).getBean(AgentRunStore.class).isInstanceOf(JdbcAgentRunStore.class);
				assertThat(context).getBean(SqlDialect.class).isEqualTo(SqlDialect.H2);
				assertThat(context).doesNotHaveBean(DurableAgentsScheduler.class);
				assertThat(context.getBean(AgentRunStore.class).countByAgentAndStatus()).isEmpty();
			});
	}

	@Test
	void jdbcWithoutDataSourceFailsClearly() {
		this.runner.run((context) -> assertThat(context).hasFailed().getFailure().rootCause()
			.hasMessageContaining("durable-agents.store=jdbc").hasMessageContaining("durable-agents.store=memory"));
	}

	@Test
	void canBeDisabled() {
		this.runner.withPropertyValues("durable-agents.enabled=false")
			.run((context) -> assertThat(context).doesNotHaveBean(AgentRuns.class));
	}

	@Test
	void userBeansWin() {
		this.runner.withPropertyValues("durable-agents.store=memory").withUserConfiguration(CustomClock.class).run((context) -> {
			assertThat(context.getBean("durableAgentsClock", Clock.class).getZone()).isEqualTo(java.time.ZoneId.of("Europe/Berlin"));
			assertThat(context).hasSingleBean(RunEngine.class);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomClock {

		@Bean
		Clock durableAgentsClock() {
			return Clock.system(java.time.ZoneId.of("Europe/Berlin"));
		}

	}

}
