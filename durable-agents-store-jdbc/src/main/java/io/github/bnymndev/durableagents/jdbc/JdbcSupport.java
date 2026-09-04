package io.github.bnymndev.durableagents.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jspecify.annotations.Nullable;

/** Small helpers shared by the JDBC stores. */
final class JdbcSupport {

	private JdbcSupport() {
	}

	static @Nullable OffsetDateTime ts(@Nullable Instant instant) {
		return (instant != null) ? instant.atOffset(ZoneOffset.UTC) : null;
	}

	static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return (value != null) ? value.toInstant() : null;
	}

	static Instant requiredInstant(ResultSet rs, String column) throws SQLException {
		Instant value = instant(rs, column);
		if (value == null) {
			throw new SQLException("Column " + column + " is null");
		}
		return value;
	}

	static @Nullable Long longOrNull(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

}
