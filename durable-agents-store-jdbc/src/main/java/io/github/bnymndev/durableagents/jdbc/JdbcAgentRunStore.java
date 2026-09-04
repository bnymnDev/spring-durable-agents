package io.github.bnymndev.durableagents.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

import io.github.bnymndev.durableagents.Page;
import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunQuery;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.RunSummary;
import io.github.bnymndev.durableagents.spi.AgentRunStore;
import io.github.bnymndev.durableagents.spi.RunRecord;

import static io.github.bnymndev.durableagents.jdbc.JdbcSupport.instant;
import static io.github.bnymndev.durableagents.jdbc.JdbcSupport.requiredInstant;
import static io.github.bnymndev.durableagents.jdbc.JdbcSupport.ts;

/** {@link AgentRunStore} on {@code agent_run}. */
public final class JdbcAgentRunStore implements AgentRunStore {

	private static final String COLUMNS = "id, agent_name, agent_version, status, input, output, error, created_at, updated_at, "
			+ "owner_instance, lease_until, correlation_id";

	private final JdbcClient jdbc;

	private final SqlDialect dialect;

	public JdbcAgentRunStore(JdbcClient jdbc, SqlDialect dialect) {
		this.jdbc = jdbc;
		this.dialect = dialect;
	}

	@Override
	public void insert(RunRecord run) {
		this.jdbc.sql("insert into agent_run (" + COLUMNS + ") values (?, ?, ?, ?, ?" + this.dialect.jsonCast() + ", ?"
				+ this.dialect.jsonCast() + ", ?, ?, ?, ?, ?, ?)")
			.param(run.id().value())
			.param(run.agentName())
			.param(run.agentVersion())
			.param(run.status().name())
			.param(run.input())
			.param(run.output())
			.param(run.error())
			.param(ts(run.createdAt()))
			.param(ts(run.updatedAt()))
			.param(run.ownerInstance())
			.param(ts(run.leaseUntil()))
			.param(run.correlationId())
			.update();
	}

	@Override
	public Optional<RunRecord> find(RunId id) {
		return this.jdbc.sql("select " + COLUMNS + " from agent_run where id = ?")
			.param(id.value())
			.query(JdbcAgentRunStore::mapRun)
			.optional();
	}

	@Override
	public void update(RunRecord run) {
		this.jdbc.sql("update agent_run set status = ?, output = ?" + this.dialect.jsonCast()
				+ ", error = ?, updated_at = ?, owner_instance = ?, lease_until = ? where id = ?")
			.param(run.status().name())
			.param(run.output())
			.param(run.error())
			.param(ts(run.updatedAt()))
			.param(run.ownerInstance())
			.param(ts(run.leaseUntil()))
			.param(run.id().value())
			.update();
	}

	@Override
	public boolean tryAcquireLease(RunId id, String owner, Instant until, Instant now) {
		int updated = this.jdbc.sql("update agent_run set status = 'RUNNING', owner_instance = ?, lease_until = ?, updated_at = ? "
				+ "where id = ? and status in ('RUNNING', 'SUSPENDED', 'FAILED') "
				+ "and (status <> 'RUNNING' or lease_until is null or lease_until <= ? or owner_instance = ?)")
			.param(owner)
			.param(ts(until))
			.param(ts(now))
			.param(id.value())
			.param(ts(now))
			.param(owner)
			.update();
		return updated == 1;
	}

	@Override
	public boolean extendLease(RunId id, String owner, Instant until, Instant now) {
		int updated = this.jdbc.sql("update agent_run set lease_until = ?, updated_at = ? "
				+ "where id = ? and status = 'RUNNING' and owner_instance = ?")
			.param(ts(until))
			.param(ts(now))
			.param(id.value())
			.param(owner)
			.update();
		return updated == 1;
	}

	@Override
	public List<RunRecord> findExpiredLeases(Instant now, int limit) {
		return this.jdbc.sql("select " + COLUMNS + " from agent_run where status = 'RUNNING' "
				+ "and (lease_until is null or lease_until <= ?) order by updated_at limit ?")
			.param(ts(now))
			.param(limit)
			.query(JdbcAgentRunStore::mapRun)
			.list();
	}

	@Override
	public Page<RunSummary> find(RunQuery query) {
		StringBuilder where = new StringBuilder(" where 1 = 1");
		List<Object> params = new ArrayList<>();
		if (query.agentName() != null) {
			where.append(" and agent_name = ?");
			params.add(query.agentName());
		}
		if (query.status() != null) {
			where.append(" and status = ?");
			params.add(query.status().name());
		}
		if (query.correlationId() != null) {
			where.append(" and correlation_id = ?");
			params.add(query.correlationId());
		}
		Long total = bind(this.jdbc.sql("select count(*) from agent_run" + where), params).query(Long.class).single();
		List<RunSummary> content = bind(this.jdbc.sql("select " + COLUMNS + " from agent_run" + where
				+ " order by created_at desc, id desc limit ? offset ?"), params)
			.param(query.size())
			.param((long) query.page() * query.size())
			.query(JdbcAgentRunStore::mapSummary)
			.list();
		return new Page<>(content, query.page(), query.size(), total);
	}

	private static StatementSpec bind(StatementSpec spec, List<Object> params) {
		for (Object param : params) {
			spec = spec.param(param);
		}
		return spec;
	}

	@Override
	public Map<String, Map<RunStatus, Long>> countByAgentAndStatus() {
		Map<String, Map<RunStatus, Long>> result = new TreeMap<>();
		this.jdbc.sql("select agent_name, status, count(*) as total from agent_run group by agent_name, status")
			.query((rs) -> {
				result.computeIfAbsent(rs.getString("agent_name"), (k) -> new EnumMap<>(RunStatus.class))
					.put(RunStatus.valueOf(rs.getString("status")), rs.getLong("total"));
			});
		return result;
	}

	@Override
	public List<RunSummary> findRecentFailures(int limit) {
		return this.jdbc.sql("select " + COLUMNS + " from agent_run where status = 'FAILED' order by updated_at desc limit ?")
			.param(limit)
			.query(JdbcAgentRunStore::mapSummary)
			.list();
	}

	@Override
	public int deleteFinishedBefore(Instant before) {
		return this.jdbc.sql("delete from agent_run where status in ('COMPLETED', 'FAILED', 'CANCELLED') and updated_at < ?")
			.param(ts(before))
			.update();
	}

	static RunRecord mapRun(ResultSet rs, int rowNum) throws SQLException {
		return new RunRecord(RunId.of(rs.getString("id")), rs.getString("agent_name"), rs.getInt("agent_version"),
				RunStatus.valueOf(rs.getString("status")), rs.getString("input"), rs.getString("output"), rs.getString("error"),
				requiredInstant(rs, "created_at"), requiredInstant(rs, "updated_at"), rs.getString("owner_instance"),
				instant(rs, "lease_until"), rs.getString("correlation_id"));
	}

	static RunSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
		return new RunSummary(RunId.of(rs.getString("id")), rs.getString("agent_name"), rs.getInt("agent_version"),
				RunStatus.valueOf(rs.getString("status")), requiredInstant(rs, "created_at"), requiredInstant(rs, "updated_at"),
				rs.getString("error"), rs.getString("correlation_id"));
	}

}
