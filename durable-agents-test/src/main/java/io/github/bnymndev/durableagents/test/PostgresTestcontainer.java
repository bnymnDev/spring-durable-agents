package io.github.bnymndev.durableagents.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A PostgreSQL container exposed as a Spring Boot service connection. Image via {@code durable-agents.test.postgres-image}. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

	public static final String DEFAULT_IMAGE = "postgres:17-alpine";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer(org.springframework.core.env.Environment environment) {
		String image = environment.getProperty("durable-agents.test.postgres-image", DEFAULT_IMAGE);
		return new PostgreSQLContainer(image);
	}

}
