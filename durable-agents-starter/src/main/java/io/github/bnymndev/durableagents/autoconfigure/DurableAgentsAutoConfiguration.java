package io.github.bnymndev.durableagents.autoconfigure;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.sql.DataSource;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

import io.github.bnymndev.durableagents.AgentRuns;
import io.github.bnymndev.durableagents.Approvals;
import io.github.bnymndev.durableagents.internal.AgentRegistry;
import io.github.bnymndev.durableagents.internal.DefaultApprovals;
import io.github.bnymndev.durableagents.internal.EngineSettings;
import io.github.bnymndev.durableagents.internal.RunEngine;
import io.github.bnymndev.durableagents.internal.RunReaper;
import io.github.bnymndev.durableagents.internal.StepAdvisor;
import io.github.bnymndev.durableagents.internal.StepBeanNames;
import io.github.bnymndev.durableagents.internal.StepMethodInterceptor;
import io.github.bnymndev.durableagents.internal.StepPlacementValidator;
import io.github.bnymndev.durableagents.internal.StepsThreadLocalAccessor;
import io.github.bnymndev.durableagents.internal.store.InMemoryAgentRunStore;
import io.github.bnymndev.durableagents.internal.store.InMemoryApprovalStore;
import io.github.bnymndev.durableagents.internal.store.InMemoryStepStore;
import io.github.bnymndev.durableagents.jdbc.DurableAgentsSchema;
import io.github.bnymndev.durableagents.jdbc.JdbcAgentRunStore;
import io.github.bnymndev.durableagents.jdbc.JdbcApprovalStore;
import io.github.bnymndev.durableagents.jdbc.JdbcStepStore;
import io.github.bnymndev.durableagents.jdbc.SqlDialect;
import io.github.bnymndev.durableagents.spi.AgentRunStore;
import io.github.bnymndev.durableagents.spi.ApprovalStore;
import io.github.bnymndev.durableagents.spi.RunListener;
import io.github.bnymndev.durableagents.spi.StepCodec;
import io.github.bnymndev.durableagents.spi.StepListener;
import io.github.bnymndev.durableagents.spi.StepStore;

/**
 * Autoconfiguration of the durable agents engine. Every bean is {@code @ConditionalOnMissingBean},
 * so an application can replace any part: the store, the codec, the executor, the clock.
 */
@AutoConfiguration(afterName = { "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
		"org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
		"org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration" })
@ConditionalOnProperty(prefix = "durable-agents", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DurableAgentsProperties.class)
@Import({ DurableAgentsAutoConfiguration.JdbcStoreConfiguration.class,
		DurableAgentsAutoConfiguration.MemoryStoreConfiguration.class, DurableAgentsSpringAiConfiguration.class,
		DurableAgentsApprovalConfiguration.class, DurableAgentsActuatorConfiguration.class })
public class DurableAgentsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public static StepBeanNames durableAgentsStepBeanNames() {
		return new StepBeanNames();
	}

	/**
	 * Fails fast with a readable message when no store could be configured. Member classes and
	 * imports are processed before the bean methods of this class, so the condition sees the store
	 * beans of the JDBC or memory configuration.
	 */
	@Bean
	@ConditionalOnMissingBean(AgentRunStore.class)
	public AgentRunStore durableAgentsMissingStore(DurableAgentsProperties properties) {
		throw new IllegalStateException("durable-agents.store=" + properties.getStore().name().toLowerCase()
				+ " but no store could be configured. For JDBC add a DataSource (spring-boot-starter-jdbc plus a driver) "
				+ "and keep durable-agents-store-jdbc on the classpath; for tests and demos set durable-agents.store=memory.");
	}

	@Bean
	@ConditionalOnMissingBean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public static StepPlacementValidator durableAgentsStepPlacementValidator(StepBeanNames names) {
		return new StepPlacementValidator(names);
	}

	@Bean
	@ConditionalOnMissingBean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public static StepAdvisor durableAgentsStepAdvisor(StepBeanNames names) {
		return new StepAdvisor(new StepMethodInterceptor(names));
	}

	@Bean
	@ConditionalOnMissingBean
	public AgentRegistry durableAgentsRegistry(ListableBeanFactory beanFactory) {
		return new AgentRegistry(beanFactory);
	}

	@Bean
	@ConditionalOnMissingBean
	public StepCodec durableAgentsStepCodec(ObjectProvider<JsonMapper> jsonMapper) {
		return new JacksonStepCodec(jsonMapper.getIfAvailable(() -> JsonMapper.builder().build()));
	}

	@Bean
	@ConditionalOnMissingBean(name = "durableAgentsClock")
	public Clock durableAgentsClock() {
		return Clock.systemUTC();
	}

	@Bean
	@ConditionalOnMissingBean(name = "durableAgentsExecutor")
	public Executor durableAgentsExecutor() {
		return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("durable-agent-", 0).factory());
	}

	@Bean
	@ConditionalOnMissingBean
	public EngineSettings durableAgentsEngineSettings(DurableAgentsProperties properties) {
		String instanceId = (properties.getInstanceId() != null) ? properties.getInstanceId()
				: EngineSettings.defaultInstanceId();
		return new EngineSettings(instanceId, properties.getLeaseDuration(), properties.isStrictReplay(),
				properties.getApproval().getDefaultTimeout());
	}

	@Bean(destroyMethod = "close")
	@ConditionalOnMissingBean(AgentRuns.class)
	public RunEngine durableAgentsRunEngine(AgentRegistry registry, AgentRunStore runs, StepStore steps,
			ApprovalStore approvals, StepCodec codec, ApplicationEventPublisher publisher,
			ObjectProvider<StepListener> stepListeners, ObjectProvider<RunListener> runListeners,
			@org.springframework.beans.factory.annotation.Qualifier("durableAgentsExecutor") Executor executor,
			EngineSettings settings, @org.springframework.beans.factory.annotation.Qualifier("durableAgentsClock") Clock clock) {
		StepsThreadLocalAccessor.register();
		List<StepListener> stepListenerList = stepListeners.orderedStream().toList();
		List<RunListener> runListenerList = runListeners.orderedStream().toList();
		return new RunEngine(registry, runs, steps, approvals, codec, publisher, stepListenerList, runListenerList, executor,
				settings, clock);
	}

	@Bean
	@ConditionalOnMissingBean
	public DefaultApprovals durableAgentsApprovals(ApprovalStore approvals, AgentRunStore runs, StepStore steps,
			RunEngine engine, ApplicationEventPublisher publisher,
			@org.springframework.beans.factory.annotation.Qualifier("durableAgentsClock") Clock clock) {
		return new DefaultApprovals(approvals, runs, steps, engine, publisher, clock);
	}

	@Bean
	@ConditionalOnMissingBean
	public RunReaper durableAgentsRunReaper(AgentRunStore runs, RunEngine engine,
			@org.springframework.beans.factory.annotation.Qualifier("durableAgentsClock") Clock clock,
			DurableAgentsProperties properties) {
		Duration retention = properties.getRetention().isZero() ? null : properties.getRetention();
		return new RunReaper(runs, engine, clock, retention, properties.getReaperBatchSize());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = "durable-agents.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
	public DurableAgentsScheduler durableAgentsScheduler(RunReaper reaper, ObjectProvider<Approvals> approvals,
			DurableAgentsProperties properties) {
		return new DurableAgentsScheduler(reaper, approvals.getIfAvailable(), properties);
	}

	/** JDBC store: PostgreSQL in production, H2 in tests. Active by default when a DataSource exists. */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = "durable-agents", name = "store", havingValue = "jdbc", matchIfMissing = true)
	@ConditionalOnClass({ JdbcClient.class, JdbcAgentRunStore.class })
	@ConditionalOnBean(DataSource.class)
	static class JdbcStoreConfiguration {

		@Bean
		@ConditionalOnMissingBean
		SqlDialect durableAgentsSqlDialect(DataSource dataSource) {
			return SqlDialect.detect(dataSource);
		}

		@Bean
		@ConditionalOnMissingBean(name = "durableAgentsSchemaMigration")
		@ConditionalOnProperty(prefix = "durable-agents.jdbc", name = "migrate", havingValue = "true", matchIfMissing = true)
		DurableAgentsSchemaMigration durableAgentsSchemaMigration(DataSource dataSource, SqlDialect dialect) {
			return new DurableAgentsSchemaMigration(dataSource, dialect);
		}

		@Bean
		@ConditionalOnMissingBean(name = "durableAgentsJdbcClient")
		JdbcClient durableAgentsJdbcClient(DataSource dataSource) {
			return JdbcClient.create(dataSource);
		}

		@Bean
		@ConditionalOnMissingBean(StepStore.class)
		JdbcStepStore durableAgentsStepStore(
				@org.springframework.beans.factory.annotation.Qualifier("durableAgentsJdbcClient") JdbcClient jdbc,
				SqlDialect dialect, ObjectProvider<DurableAgentsSchemaMigration> migration) {
			migration.ifAvailable(DurableAgentsSchemaMigration::ensureMigrated);
			return new JdbcStepStore(jdbc, dialect);
		}

		@Bean
		@ConditionalOnMissingBean(ApprovalStore.class)
		JdbcApprovalStore durableAgentsApprovalStore(
				@org.springframework.beans.factory.annotation.Qualifier("durableAgentsJdbcClient") JdbcClient jdbc,
				ObjectProvider<DurableAgentsSchemaMigration> migration) {
			migration.ifAvailable(DurableAgentsSchemaMigration::ensureMigrated);
			return new JdbcApprovalStore(jdbc);
		}

		@Bean
		@ConditionalOnMissingBean(AgentRunStore.class)
		JdbcAgentRunStore durableAgentsRunStore(
				@org.springframework.beans.factory.annotation.Qualifier("durableAgentsJdbcClient") JdbcClient jdbc,
				SqlDialect dialect, ObjectProvider<DurableAgentsSchemaMigration> migration) {
			migration.ifAvailable(DurableAgentsSchemaMigration::ensureMigrated);
			return new JdbcAgentRunStore(jdbc, dialect);
		}

	}

	/** In-memory store, {@code durable-agents.store=memory}. */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = "durable-agents", name = "store", havingValue = "memory")
	static class MemoryStoreConfiguration {

		@Bean
		@ConditionalOnMissingBean(StepStore.class)
		InMemoryStepStore durableAgentsStepStore() {
			return new InMemoryStepStore();
		}

		@Bean
		@ConditionalOnMissingBean(ApprovalStore.class)
		InMemoryApprovalStore durableAgentsApprovalStore() {
			return new InMemoryApprovalStore();
		}

		@Bean
		@ConditionalOnMissingBean(AgentRunStore.class)
		InMemoryAgentRunStore durableAgentsRunStore(InMemoryStepStore steps, InMemoryApprovalStore approvals) {
			return new InMemoryAgentRunStore(steps, approvals);
		}

	}

	/** Runs the bundled migrations once, before the first store bean is created. */
	public static final class DurableAgentsSchemaMigration {

		private final DataSource dataSource;

		private final SqlDialect dialect;

		private volatile boolean migrated;

		DurableAgentsSchemaMigration(DataSource dataSource, SqlDialect dialect) {
			this.dataSource = dataSource;
			this.dialect = dialect;
		}

		synchronized void ensureMigrated() {
			if (!this.migrated) {
				DurableAgentsSchema.migrate(this.dataSource, this.dialect);
				this.migrated = true;
			}
		}

	}

}
