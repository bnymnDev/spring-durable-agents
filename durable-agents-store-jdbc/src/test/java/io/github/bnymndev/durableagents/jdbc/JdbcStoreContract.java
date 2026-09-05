package io.github.bnymndev.durableagents.jdbc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.bnymndev.durableagents.ApprovalDecision;
import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunQuery;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.StepStatus;
import io.github.bnymndev.durableagents.spi.ApprovalRecord;
import io.github.bnymndev.durableagents.spi.RunRecord;
import io.github.bnymndev.durableagents.spi.StepRecord;

import static org.assertj.core.api.Assertions.assertThat;

/** Store contract, run against H2 and (with Docker) PostgreSQL. */
abstract class JdbcStoreContract {

	protected JdbcAgentRunStore runs;

	protected JdbcStepStore steps;

	protected JdbcApprovalStore approvals;

	protected abstract DataSource dataSource();

	protected abstract SqlDialect dialect();

	@BeforeEach
	void setUp() {
		DataSource ds = dataSource();
		DurableAgentsSchema.migrate(ds, dialect());
		JdbcClient jdbc = JdbcClient.create(ds);
		jdbc.sql("delete from agent_run").update();
		this.runs = new JdbcAgentRunStore(jdbc, dialect());
		this.steps = new JdbcStepStore(jdbc, dialect());
		this.approvals = new JdbcApprovalStore(jdbc);
	}

	@Test
	void migrationIsIdempotent() {
		assertThat(DurableAgentsSchema.migrate(dataSource(), dialect()).migrationsExecuted).isZero();
	}

	@Test
	void runRoundTrip() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		RunRecord run = new RunRecord(RunId.next(), "triage", 2, RunStatus.RUNNING, "{\"ticket\":\"T-1\"}", null, null, now, now,
				"instance-a", now.plusSeconds(30), "T-1");
		this.runs.insert(run);

		assertSameRun(this.runs.find(run.id()).orElseThrow(), run);

		RunRecord done = run.withOutput("{\"ok\":true}", now.plusSeconds(1));
		this.runs.update(done);
		assertSameRun(this.runs.find(run.id()).orElseThrow(), done);
		assertThat(this.runs.countByAgentAndStatus()).containsEntry("triage", Map.of(RunStatus.COMPLETED, 1L));
	}

	@Test
	void leaseIsExclusive() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		RunRecord run = new RunRecord(RunId.next(), "a", 1, RunStatus.RUNNING, null, null, null, now, now, "instance-a",
				now.plusSeconds(30), null);
		this.runs.insert(run);

		assertThat(this.runs.tryAcquireLease(run.id(), "instance-b", now.plusSeconds(30), now)).isFalse();
		assertThat(this.runs.extendLease(run.id(), "instance-a", now.plusSeconds(60), now)).isTrue();
		assertThat(this.runs.extendLease(run.id(), "instance-b", now.plusSeconds(60), now)).isFalse();

		Instant later = now.plusSeconds(120);
		assertThat(this.runs.findExpiredLeases(later, 10)).extracting(RunRecord::id).containsExactly(run.id());
		assertThat(this.runs.tryAcquireLease(run.id(), "instance-b", later.plusSeconds(30), later)).isTrue();
		assertThat(this.runs.find(run.id()).orElseThrow().ownerInstance()).isEqualTo("instance-b");
		assertThat(this.runs.findExpiredLeases(later, 10)).isEmpty();
	}

	@Test
	void suspendedRunCanBeAcquired() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		RunRecord run = new RunRecord(RunId.next(), "a", 1, RunStatus.SUSPENDED, null, null, null, now, now, null, null, null);
		this.runs.insert(run);
		assertThat(this.runs.tryAcquireLease(run.id(), "x", now.plusSeconds(30), now)).isTrue();
		assertThat(this.runs.find(run.id()).orElseThrow().status()).isEqualTo(RunStatus.RUNNING);
	}

	@Test
	void findPagesAndFilters() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		for (int i = 0; i < 5; i++) {
			RunStatus status = (i % 2 == 0) ? RunStatus.COMPLETED : RunStatus.FAILED;
			this.runs.insert(new RunRecord(RunId.next(), (i < 3) ? "a" : "b", 1, status, null, null, (status == RunStatus.FAILED) ? "boom" : null,
					now.plusMillis(i), now.plusMillis(i), null, null, "c-" + i));
		}
		assertThat(this.runs.find(RunQuery.all().page(0, 2)).content()).hasSize(2);
		assertThat(this.runs.find(RunQuery.all().page(0, 2)).total()).isEqualTo(5);
		assertThat(this.runs.find(RunQuery.all().page(2, 2)).content()).hasSize(1);
		assertThat(this.runs.find(RunQuery.forAgent("b")).total()).isEqualTo(2);
		assertThat(this.runs.find(RunQuery.withStatus(RunStatus.FAILED)).total()).isEqualTo(2);
		assertThat(this.runs.find(RunQuery.all().correlation("c-4")).content()).hasSize(1);
		assertThat(this.runs.findRecentFailures(10)).hasSize(2);
		assertThat(this.runs.find(RunQuery.all()).content().get(0).correlationId()).isEqualTo("c-4");
	}

	@Test
	void stepsUpsertAndOrder() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		RunId runId = RunId.next();
		this.runs.insert(new RunRecord(runId, "a", 1, RunStatus.RUNNING, null, null, null, now, now, "i", now.plusSeconds(30), null));
		StepRecord failed = new StepRecord(runId, runId + ":x:0", "x", 1, 1, StepStatus.FAILED, 1, null, null, null, "boom",
				StepKind.STEP, null, null, null, null, now, now);
		StepRecord first = new StepRecord(runId, runId + ":llm:0", "llm", 1, 0, StepStatus.COMPLETED, 1, "{\"p\":1}", "{\"a\":1}",
				"java.util.Map", null, StepKind.LLM, null, 12L, 34L, "gpt-x", now, now.plusMillis(5));
		this.steps.save(failed);
		this.steps.save(first);
		this.steps.save(failed.withLlmUsage(null, null, null));
		StepRecord completed = new StepRecord(runId, failed.stepKey(), "x", 1, 1, StepStatus.COMPLETED, 2, null, "\"ok\"",
				"java.lang.String", null, StepKind.STEP, null, null, null, null, now, now.plusMillis(9));
		this.steps.save(completed);

		List<StepRecord> all = this.steps.findByRun(runId);
		assertThat(all).hasSize(2);
		assertSameStep(all.get(0), first);
		assertSameStep(all.get(1), completed);
		assertSameStep(this.steps.find(runId, failed.stepKey()).orElseThrow(), completed);
		assertThat(this.steps.find(runId, "missing")).isEmpty();

		this.runs.deleteFinishedBefore(now.plusSeconds(1));
		assertThat(this.steps.findByRun(runId)).hasSize(2);
		this.runs.update(this.runs.find(runId).orElseThrow().withOutput("1", now));
		assertThat(this.runs.deleteFinishedBefore(now.plusSeconds(1))).isEqualTo(1);
		assertThat(this.steps.findByRun(runId)).isEmpty();
	}

	/** jsonb normalises whitespace, so JSON columns are compared without it. */
	private static void assertSameRun(RunRecord actual, RunRecord expected) {
		assertThat(actual).usingRecursiveComparison().ignoringFields("input", "output").isEqualTo(expected);
		assertThat(compact(actual.input())).isEqualTo(compact(expected.input()));
		assertThat(compact(actual.output())).isEqualTo(compact(expected.output()));
	}

	private static void assertSameStep(StepRecord actual, StepRecord expected) {
		assertThat(actual).usingRecursiveComparison().ignoringFields("input", "output").isEqualTo(expected);
		assertThat(compact(actual.input())).isEqualTo(compact(expected.input()));
		assertThat(compact(actual.output())).isEqualTo(compact(expected.output()));
	}

	private static String compact(String json) {
		return (json != null) ? json.replaceAll("\\s+", "") : null;
	}

	@Test
	void approvalsRoundTrip() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		RunId runId = RunId.next();
		this.runs.insert(new RunRecord(runId, "a", 1, RunStatus.SUSPENDED, null, null, null, now, now, null, null, null));
		ApprovalRecord a = new ApprovalRecord(RunId.next().value(), runId, runId + ":apply:0", "LEAD", "refund 10 EUR", now,
				now.plus(Duration.ofDays(1)), ApprovalDecision.PENDING, null, null, null);
		ApprovalRecord expired = new ApprovalRecord(RunId.next().value(), runId, runId + ":other:0", "CFO", null, now,
				now.minusSeconds(1), ApprovalDecision.PENDING, null, null, null);
		this.approvals.insert(a);
		this.approvals.insert(expired);

		assertThat(this.approvals.find(a.id())).contains(a);
		assertThat(this.approvals.findByStep(runId, a.stepKey())).contains(a);
		assertThat(this.approvals.findPending("LEAD")).containsExactly(a);
		assertThat(this.approvals.findPending(null)).hasSize(2);
		assertThat(this.approvals.findPendingExpiredBefore(now)).containsExactly(expired);
		assertThat(this.approvals.countPending()).isEqualTo(2);

		ApprovalRecord decided = a.decided(ApprovalDecision.APPROVED, now.plusSeconds(5), "alice", "fine");
		this.approvals.update(decided);
		assertThat(this.approvals.find(a.id())).contains(decided);
		assertThat(this.approvals.countPending()).isEqualTo(1);
	}

}
