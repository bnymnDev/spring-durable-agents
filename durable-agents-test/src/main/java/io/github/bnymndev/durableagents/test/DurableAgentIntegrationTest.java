package io.github.bnymndev.durableagents.test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.TestPropertySource;

/**
 * Full application test against a PostgreSQL Testcontainer wired through
 * {@code @ServiceConnection}. Requires Docker and {@code org.testcontainers:testcontainers-postgresql}
 * on the test classpath. Scheduling stays on, so the reaper is part of the test.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest
@Import({ PostgresTestcontainer.class, DurableAgentTestConfiguration.class })
@TestPropertySource(properties = { "durable-agents.store=jdbc", "durable-agents.strict-replay=true" })
public @interface DurableAgentIntegrationTest {

	@AliasFor(annotation = SpringBootTest.class, attribute = "properties")
	String[] properties() default {};

	@AliasFor(annotation = SpringBootTest.class, attribute = "webEnvironment")
	SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;

}
