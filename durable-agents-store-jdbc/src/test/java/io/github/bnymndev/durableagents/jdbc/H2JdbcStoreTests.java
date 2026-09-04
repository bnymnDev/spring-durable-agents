package io.github.bnymndev.durableagents.jdbc;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Store contract against H2 in PostgreSQL compatibility mode. */
class H2JdbcStoreTests extends JdbcStoreContract {

	private static final DataSource DATA_SOURCE = new DriverManagerDataSource(
			"jdbc:h2:mem:durable_agents_store;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");

	@Override
	protected DataSource dataSource() {
		return DATA_SOURCE;
	}

	@Override
	protected SqlDialect dialect() {
		return SqlDialect.detect(DATA_SOURCE);
	}

}
