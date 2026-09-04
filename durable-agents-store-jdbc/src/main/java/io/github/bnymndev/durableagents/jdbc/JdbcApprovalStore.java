package io.github.bnymndev.durableagents.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.bnymndev.durableagents.ApprovalDecision;
import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.spi.ApprovalRecord;
import io.github.bnymndev.durableagents.spi.ApprovalStore;

import static io.github.bnymndev.durableagents.jdbc.JdbcSupport.instant;
import static io.github.bnymndev.durableagents.jdbc.JdbcSupport.requiredInstant;
import static io.github.bnymndev.durableagents.jdbc.JdbcSupport.ts;

/** {@link ApprovalStore} on {@code agent_approval}. */
public final class JdbcApprovalStore implements ApprovalStore {

	private static final String COLUMNS = "id, run_id, step_key, required_role, description, requested_at, expires_at, decision, "
			+ "decided_at, decided_by, comment";

	private final JdbcClient jdbc;

	public JdbcApprovalStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void insert(ApprovalRecord a) {
		this.jdbc.sql("insert into agent_approval (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
			.param(a.id())
			.param(a.runId().value())
			.param(a.stepKey())
			.param(a.requiredRole())
			.param(a.description())
			.param(ts(a.requestedAt()))
			.param(ts(a.expiresAt()))
			.param(a.decision().name())
			.param(ts(a.decidedAt()))
			.param(a.decidedBy())
			.param(a.comment())
			.update();
	}

	@Override
	public void update(ApprovalRecord a) {
		this.jdbc.sql("update agent_approval set decision = ?, decided_at = ?, decided_by = ?, comment = ? where id = ?")
			.param(a.decision().name())
			.param(ts(a.decidedAt()))
			.param(a.decidedBy())
			.param(a.comment())
			.param(a.id())
			.update();
	}

	@Override
	public Optional<ApprovalRecord> find(String id) {
		return this.jdbc.sql("select " + COLUMNS + " from agent_approval where id = ?")
			.param(id)
			.query(JdbcApprovalStore::map)
			.optional();
	}

	@Override
	public Optional<ApprovalRecord> findByStep(RunId runId, String stepKey) {
		return this.jdbc.sql("select " + COLUMNS + " from agent_approval where run_id = ? and step_key = ? "
				+ "order by requested_at desc limit 1")
			.param(runId.value())
			.param(stepKey)
			.query(JdbcApprovalStore::map)
			.optional();
	}

	@Override
	public List<ApprovalRecord> findPending(@Nullable String role) {
		if (role == null) {
			return this.jdbc.sql("select " + COLUMNS + " from agent_approval where decision = 'PENDING' order by requested_at")
				.query(JdbcApprovalStore::map)
				.list();
		}
		return this.jdbc.sql("select " + COLUMNS + " from agent_approval where decision = 'PENDING' and required_role = ? "
				+ "order by requested_at")
			.param(role)
			.query(JdbcApprovalStore::map)
			.list();
	}

	@Override
	public List<ApprovalRecord> findPendingExpiredBefore(Instant now) {
		return this.jdbc.sql("select " + COLUMNS + " from agent_approval where decision = 'PENDING' and expires_at <= ?")
			.param(ts(now))
			.query(JdbcApprovalStore::map)
			.list();
	}

	@Override
	public long countPending() {
		return this.jdbc.sql("select count(*) from agent_approval where decision = 'PENDING'").query(Long.class).single();
	}

	static ApprovalRecord map(ResultSet rs, int rowNum) throws SQLException {
		return new ApprovalRecord(rs.getString("id"), RunId.of(rs.getString("run_id")), rs.getString("step_key"),
				rs.getString("required_role"), rs.getString("description"), requiredInstant(rs, "requested_at"),
				requiredInstant(rs, "expires_at"), ApprovalDecision.valueOf(rs.getString("decision")), instant(rs, "decided_at"),
				rs.getString("decided_by"), rs.getString("comment"));
	}

}
