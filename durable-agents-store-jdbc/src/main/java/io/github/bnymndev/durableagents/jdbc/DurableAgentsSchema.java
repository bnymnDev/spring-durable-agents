package io.github.bnymndev.durableagents.jdbc;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Applies the bundled Flyway migrations with a separate history table
 * ({@code durable_agents_schema_history}) so they never interfere with the application's own
 * migrations. Disable with {@code durable-agents.jdbc.migrate=false} and apply the SQL under
 * {@code io/github/bnymndev/durableagents/db/} yourself.
 */
public final class DurableAgentsSchema {

	public static final String HISTORY_TABLE = "durable_agents_schema_history";

	public static final String LOCATION_PREFIX = "classpath:io/github/bnymndev/durableagents/db/";

	private static final Log logger = LogFactory.getLog(DurableAgentsSchema.class);

	private DurableAgentsSchema() {
	}

	public static MigrateResult migrate(DataSource dataSource, SqlDialect dialect) {
		Flyway flyway = Flyway.configure()
			.dataSource(dataSource)
			.table(HISTORY_TABLE)
			.locations(LOCATION_PREFIX + dialect.migrationFolder())
			.baselineOnMigrate(false)
			.load();
		MigrateResult result = flyway.migrate();
		if (result.migrationsExecuted > 0) {
			logger.info("Applied " + result.migrationsExecuted + " durable-agents migration(s) for " + dialect);
		}
		return result;
	}

}
