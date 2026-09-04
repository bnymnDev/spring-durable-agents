package io.github.bnymndev.durableagents.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.StepStatus;
import io.github.bnymndev.durableagents.spi.StepRecord;
import io.github.bnymndev.durableagents.spi.StepStore;

import static io.github.bnymndev.durableagents.jdbc.JdbcSupport.instant;
import static io.github.bnymndev.durableagents.jdbc.JdbcSupport.longOrNull;
import static io.github.bnymndev.durableagents.jdbc.JdbcSupport.ts;

/** {@link StepStore} on {@code agent_step}. */
public final class JdbcStepStore implements StepStore {

	private static final String COLUMNS = "run_id, step_key, step_name, step_version, sequence, status, attempt, input, output, "
			+ "output_type, error, kind, parent_key, tokens_in, tokens_out, model, started_at, finished_at";

	private final JdbcClient jdbc;

	private final SqlDialect dialect;

	public JdbcStepStore(JdbcClient jdbc, SqlDialect dialect) {
		this.jdbc = jdbc;
		this.dialect = dialect;
	}

	@Override
	public void save(StepRecord step) {
		String json = this.dialect.jsonCast();
		int updated = this.jdbc.sql("update agent_step set step_name = ?, step_version = ?, sequence = ?, status = ?, attempt = ?, "
				+ "input = ?" + json + ", output = ?" + json + ", output_type = ?, error = ?, kind = ?, parent_key = ?, "
				+ "tokens_in = ?, tokens_out = ?, model = ?, started_at = ?, finished_at = ? where run_id = ? and step_key = ?")
			.param(step.stepName())
			.param(step.stepVersion())
			.param(step.sequence())
			.param(step.status().name())
			.param(step.attempt())
			.param(step.input())
			.param(step.output())
			.param(step.outputType())
			.param(step.error())
			.param(step.kind().name())
			.param(step.parentKey())
			.param(step.tokensIn())
			.param(step.tokensOut())
			.param(step.model())
			.param(ts(step.startedAt()))
			.param(ts(step.finishedAt()))
			.param(step.runId().value())
			.param(step.stepKey())
			.update();
		if (updated == 0) {
			this.jdbc.sql("insert into agent_step (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?" + json + ", ?" + json
					+ ", ?, ?, ?, ?, ?, ?, ?, ?, ?)")
				.param(step.runId().value())
				.param(step.stepKey())
				.param(step.stepName())
				.param(step.stepVersion())
				.param(step.sequence())
				.param(step.status().name())
				.param(step.attempt())
				.param(step.input())
				.param(step.output())
				.param(step.outputType())
				.param(step.error())
				.param(step.kind().name())
				.param(step.parentKey())
				.param(step.tokensIn())
				.param(step.tokensOut())
				.param(step.model())
				.param(ts(step.startedAt()))
				.param(ts(step.finishedAt()))
				.update();
		}
	}

	@Override
	public Optional<StepRecord> find(RunId runId, String stepKey) {
		return this.jdbc.sql("select " + COLUMNS + " from agent_step where run_id = ? and step_key = ?")
			.param(runId.value())
			.param(stepKey)
			.query(JdbcStepStore::mapStep)
			.optional();
	}

	@Override
	public List<StepRecord> findByRun(RunId runId) {
		return this.jdbc.sql("select " + COLUMNS + " from agent_step where run_id = ? order by sequence")
			.param(runId.value())
			.query(JdbcStepStore::mapStep)
			.list();
	}

	static StepRecord mapStep(ResultSet rs, int rowNum) throws SQLException {
		return new StepRecord(RunId.of(rs.getString("run_id")), rs.getString("step_key"), rs.getString("step_name"),
				rs.getInt("step_version"), rs.getInt("sequence"), StepStatus.valueOf(rs.getString("status")), rs.getInt("attempt"),
				rs.getString("input"), rs.getString("output"), rs.getString("output_type"), rs.getString("error"),
				StepKind.valueOf(rs.getString("kind")), rs.getString("parent_key"), longOrNull(rs, "tokens_in"),
				longOrNull(rs, "tokens_out"), rs.getString("model"), instant(rs, "started_at"), instant(rs, "finished_at"));
	}

}
