package io.github.bnymndev.durableagents.jdbc;

import javax.sql.DataSource;

import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;

/** The two databases the JDBC store knows. */
public enum SqlDialect {

	POSTGRESQL("postgresql", "::jsonb"),

	H2("h2", "");

	private final String migrationFolder;

	private final String jsonCast;

	SqlDialect(String migrationFolder, String jsonCast) {
		this.migrationFolder = migrationFolder;
		this.jsonCast = jsonCast;
	}

	/** Folder below {@code io/github/bnymndev/durableagents/db/} holding the Flyway migrations. */
	public String migrationFolder() {
		return this.migrationFolder;
	}

	/** Suffix appended to a JSON parameter placeholder, e.g. {@code ?::jsonb}. */
	public String jsonCast() {
		return this.jsonCast;
	}

	public static SqlDialect detect(DataSource dataSource) {
		try {
			String product = JdbcUtils.extractDatabaseMetaData(dataSource, (m) -> m.getDatabaseProductName());
			return fromProductName(product);
		}
		catch (MetaDataAccessException ex) {
			throw new IllegalStateException("Could not read database metadata to detect the SQL dialect", ex);
		}
	}

	public static SqlDialect fromProductName(String product) {
		String name = product.toLowerCase();
		if (name.contains("postgres")) {
			return POSTGRESQL;
		}
		if (name.contains("h2")) {
			return H2;
		}
		throw new IllegalStateException("Unsupported database '" + product + "'. durable-agents-store-jdbc supports PostgreSQL and H2.");
	}

}
