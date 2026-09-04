package io.github.bnymndev.durableagents.autoconfigure;

import java.time.Clock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.bnymndev.durableagents.actuator.AgentMetrics;
import io.github.bnymndev.durableagents.actuator.AgentObservations;
import io.github.bnymndev.durableagents.actuator.AgentsEndpoint;
import io.github.bnymndev.durableagents.actuator.AgentsHealthIndicator;
import io.github.bnymndev.durableagents.actuator.MdcStepListener;
import io.github.bnymndev.durableagents.internal.RunEngine;
import io.github.bnymndev.durableagents.internal.RunReaper;
import io.github.bnymndev.durableagents.spi.AgentRunStore;
import io.github.bnymndev.durableagents.spi.ApprovalStore;
import io.github.bnymndev.durableagents.spi.StepStore;

/** Actuator endpoint, health, metrics, observations and MDC when {@code durable-agents-actuator} is present. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(AgentsEndpoint.class)
class DurableAgentsActuatorConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(Endpoint.class)
	static class EndpointConfiguration {

		@Bean
		@ConditionalOnMissingBean
		@ConditionalOnAvailableEndpoint(AgentsEndpoint.class)
		AgentsEndpoint agentsEndpoint(AgentRunStore runs, StepStore steps, ApprovalStore approvals, RunEngine engine) {
			return new AgentsEndpoint(runs, steps, approvals, engine);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HealthIndicator.class)
	static class HealthConfiguration {

		@Bean
		@ConditionalOnMissingBean(name = "agentsHealthIndicator")
		@ConditionalOnEnabledHealthIndicator("agents")
		AgentsHealthIndicator agentsHealthIndicator(AgentRunStore runs, ObjectProvider<RunReaper> reaper,
				DurableAgentsProperties properties, @Qualifier("durableAgentsClock") Clock clock) {
			return new AgentsHealthIndicator(runs, reaper.getIfAvailable(), properties.getReaperInterval(), clock);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(MeterRegistry.class)
	@ConditionalOnBean(MeterRegistry.class)
	@ConditionalOnProperty(prefix = "durable-agents.observability", name = "metrics", havingValue = "true", matchIfMissing = true)
	static class MetricsConfiguration {

		@Bean
		@ConditionalOnMissingBean
		AgentMetrics durableAgentsMetrics(MeterRegistry registry, ObjectProvider<RunEngine> engine, ApprovalStore approvals) {
			return new AgentMetrics(registry, engine.getObject(), approvals);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(ObservationRegistry.class)
	@ConditionalOnBean(ObservationRegistry.class)
	@ConditionalOnProperty(prefix = "durable-agents.observability", name = "observations", havingValue = "true",
			matchIfMissing = true)
	static class ObservationConfiguration {

		@Bean
		@ConditionalOnMissingBean
		AgentObservations durableAgentsObservations(ObservationRegistry registry) {
			return new AgentObservations(registry);
		}

	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = "durable-agents.observability", name = "mdc", havingValue = "true", matchIfMissing = true)
	MdcStepListener durableAgentsMdcListener() {
		return new MdcStepListener();
	}

}
