package io.github.bnymndev.durableagents.jdbc;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Store contract against a real PostgreSQL. Skipped automatically when Docker is not available. */
@Testcontainers(disabledWithoutDocker = true)
class PostgresJdbcStoreTests extends JdbcStoreContract {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	private static HikariDataSource dataSource;

	@BeforeAll
	static void createDataSource() {
		dataSource = new HikariDataSource();
		dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
		dataSource.setUsername(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
	}

	@AfterAll
	static void closeDataSource() {
		dataSource.close();
	}

	@Override
	protected DataSource dataSource() {
		return dataSource;
	}

	@Override
	protected SqlDialect dialect() {
		return SqlDialect.POSTGRESQL;
	}

}
