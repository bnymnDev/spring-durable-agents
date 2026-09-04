package io.github.bnymndev.durableagents.test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.boot.test.context.filter.annotation.TypeExcludeFilters;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.github.bnymndev.durableagents.autoconfigure.DurableAgentsAutoConfiguration;

/**
 * Test slice for durable agents. Loads {@code @DurableAgent} beans and beans with {@code @Step}
 * methods (plus whatever {@link #includeFilters()} adds), the engine on an in-memory store, no
 * scheduler, and a {@link FakeChatModel} behind {@code ChatClient.Builder} when Spring AI is on the
 * classpath. Inject {@link DurableAgentTester} to run agents, simulate crashes and assert on steps.
 *
 * <pre>{@code
 * @DurableAgentTest
 * class TicketTriageAgentTests {
 *
 *   @Autowired DurableAgentTester agents;
 *   @Autowired FakeChatModel chat;
 *
 *   @Test
 *   void resumesAfterCrash() {
 *     chat.respondWithJson(new Classification("billing", false));
 *     agents.crashAfter("classify");
 *     RunResult crashed = agents.run(TicketTriageAgent.class, ticket);
 *     RunResult done = agents.resume(crashed.runId());
 *     agents.assertThatRun(done.runId()).completed().hasSteps("classify", "findSimilar", "apply");
 *   }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@BootstrapWith(SpringBootTestContextBootstrapper.class)
@ExtendWith(SpringExtension.class)
@OverrideAutoConfiguration(enabled = false)
@TypeExcludeFilters(DurableAgentTypeExcludeFilter.class)
@ImportAutoConfiguration({ DurableAgentsAutoConfiguration.class, JacksonAutoConfiguration.class, AopAutoConfiguration.class })
@Import(DurableAgentTestConfiguration.class)
@TestPropertySource(properties = { "durable-agents.store=memory", "durable-agents.scheduling.enabled=false",
		"durable-agents.strict-replay=true", "spring.threads.virtual.enabled=true" })
public @interface DurableAgentTest {

	/** Properties in {@code key=value} form added to the environment. */
	String[] properties() default {};

	/** Whether default filters (agents and step beans) apply. */
	boolean useDefaultFilters() default true;

	/** Additional components to include, e.g. a repository the step bean needs. */
	ComponentScan.Filter[] includeFilters() default {};

	/** Components to exclude. */
	ComponentScan.Filter[] excludeFilters() default {};

	/** Auto-configuration exclusions. */
	@AliasFor(annotation = ImportAutoConfiguration.class, attribute = "exclude")
	Class<?>[] excludeAutoConfiguration() default {};

}
