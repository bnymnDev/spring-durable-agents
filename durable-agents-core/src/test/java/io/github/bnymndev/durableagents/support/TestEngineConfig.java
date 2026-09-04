package io.github.bnymndev.durableagents.support;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Role;

import io.github.bnymndev.durableagents.internal.AgentRegistry;
import io.github.bnymndev.durableagents.internal.DefaultApprovals;
import io.github.bnymndev.durableagents.internal.EngineSettings;
import io.github.bnymndev.durableagents.internal.RunEngine;
import io.github.bnymndev.durableagents.internal.StepAdvisor;
import io.github.bnymndev.durableagents.internal.StepBeanNames;
import io.github.bnymndev.durableagents.internal.StepMethodInterceptor;
import io.github.bnymndev.durableagents.internal.StepPlacementValidator;
import io.github.bnymndev.durableagents.internal.store.InMemoryAgentRunStore;
import io.github.bnymndev.durableagents.internal.store.InMemoryApprovalStore;
import io.github.bnymndev.durableagents.internal.store.InMemoryStepStore;
import io.github.bnymndev.durableagents.spi.RunListener;
import io.github.bnymndev.durableagents.spi.StepListener;

/** Wires the engine by hand, the way the starter does with autoconfiguration. */
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
public class TestEngineConfig {

	@Bean
	InMemoryStepStore stepStore() {
		return new InMemoryStepStore();
	}

	@Bean
	InMemoryApprovalStore approvalStore() {
		return new InMemoryApprovalStore();
	}

	@Bean
	InMemoryAgentRunStore runStore(InMemoryStepStore steps, InMemoryApprovalStore approvals) {
		return new InMemoryAgentRunStore(steps, approvals);
	}

	@Bean
	TestCodec codec() {
		return new TestCodec();
	}

	@Bean
	EngineSettings engineSettings() {
		return new EngineSettings("test-instance", Duration.ofSeconds(2), true);
	}

	@Bean
	AgentRegistry agentRegistry(ListableBeanFactory beanFactory) {
		return new AgentRegistry(beanFactory);
	}

	@Bean(destroyMethod = "close")
	RunEngine runEngine(AgentRegistry registry, InMemoryAgentRunStore runs, InMemoryStepStore steps,
			InMemoryApprovalStore approvals, TestCodec codec, ApplicationEventPublisher publisher, List<StepListener> stepListeners,
			List<RunListener> runListeners, EngineSettings settings) {
		Executor executor = Executors.newVirtualThreadPerTaskExecutor();
		return new RunEngine(registry, runs, steps, approvals, codec, publisher, stepListeners, runListeners, executor, settings,
				Clock.systemUTC());
	}

	@Bean
	DefaultApprovals approvals(InMemoryApprovalStore approvals, InMemoryAgentRunStore runs, InMemoryStepStore steps,
			RunEngine engine, ApplicationEventPublisher publisher) {
		return new DefaultApprovals(approvals, runs, steps, engine, publisher, Clock.systemUTC());
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	StepBeanNames stepBeanNames() {
		return new StepBeanNames();
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static StepPlacementValidator stepPlacementValidator(StepBeanNames names) {
		return new StepPlacementValidator(names);
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	StepAdvisor stepAdvisor(StepBeanNames names) {
		return new StepAdvisor(new StepMethodInterceptor(names));
	}

}
