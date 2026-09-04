package io.github.bnymndev.durableagents.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import io.github.bnymndev.durableagents.Agent;
import io.github.bnymndev.durableagents.AgentRuns;
import io.github.bnymndev.durableagents.ApprovalRejected;
import io.github.bnymndev.durableagents.Approvals;
import io.github.bnymndev.durableagents.DurableAgent;
import io.github.bnymndev.durableagents.NonDeterministicReplay;
import io.github.bnymndev.durableagents.PendingApproval;
import io.github.bnymndev.durableagents.Retry;
import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunQuery;
import io.github.bnymndev.durableagents.RunResult;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.Step;
import io.github.bnymndev.durableagents.StepContext;
import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.StepStatus;
import io.github.bnymndev.durableagents.StepTimeout;
import io.github.bnymndev.durableagents.Steps;
import io.github.bnymndev.durableagents.TypeRef;
import io.github.bnymndev.durableagents.internal.store.InMemoryAgentRunStore;
import io.github.bnymndev.durableagents.internal.store.InMemoryApprovalStore;
import io.github.bnymndev.durableagents.internal.store.InMemoryStepStore;
import io.github.bnymndev.durableagents.spi.StepRecord;
import io.github.bnymndev.durableagents.support.Recording;
import io.github.bnymndev.durableagents.support.SimulatedCrash;
import io.github.bnymndev.durableagents.support.TestEngineConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig({ TestEngineConfig.class, RunEngineTests.Fixtures.class })
class RunEngineTests {

	private static final Duration WAIT = Duration.ofSeconds(10);

	@Autowired
	AgentRuns runs;

	@Autowired
	Approvals approvals;

	@Autowired
	Recording recording;

	@Autowired
	InMemoryStepStore stepStore;

	@Autowired
	InMemoryAgentRunStore runStore;

	@Autowired
	InMemoryApprovalStore approvalStore;

	@Autowired
	Fixtures fixtures;

	@AfterEach
	void reset() {
		this.recording.clear();
		this.fixtures.crashAfter.set(null);
		this.fixtures.failFirstAttempts.set(0);
		this.fixtures.divergeOnResume.set(false);
		this.stepStore.clear();
		this.runStore.clear();
		this.approvalStore.clear();
	}

	@Test
	void completesAndStoresEveryStepOnce() {
		RunResult result = this.runs.start(LoopAgent.class, new Input("abc", 3)).await(WAIT);

		assertThat(result.completed()).isTrue();
		assertThat(result.outputAs(Output.class).steps()).containsExactly("abc-0", "abc-1", "abc-2");
		List<StepRecord> steps = this.stepStore.findByRun(result.runId());
		assertThat(steps).extracting(StepRecord::stepKey)
			.containsExactly(key(result.runId(), "work", 0), key(result.runId(), "work", 1), key(result.runId(), "work", 2),
					key(result.runId(), "finish", 0));
		assertThat(steps).allMatch((s) -> s.status() == StepStatus.COMPLETED);
		assertThat(steps).extracting(StepRecord::kind).containsOnly(StepKind.STEP);
		assertThat(this.runs.status(result.runId())).isEqualTo(RunStatus.COMPLETED);
	}

	@Test
	void crashAfterSecondStepResumesWithoutReExecutingCompletedSteps() {
		this.fixtures.crashAfter.set("work:1");

		RunResult crashed = this.runs.start(LoopAgent.class, new Input("x", 3)).await(WAIT);

		assertThat(crashed.status()).isEqualTo(RunStatus.RUNNING);
		assertThat(this.runs.status(crashed.runId())).isEqualTo(RunStatus.RUNNING);
		assertThat(this.recording.executions()).containsExactly("work:0", "work:1");
		assertThat(this.stepStore.findByRun(crashed.runId())).hasSize(2);

		this.fixtures.crashAfter.set(null);
		RunResult resumed = this.runs.resume(crashed.runId()).await(WAIT);

		assertThat(resumed.completed()).isTrue();
		assertThat(resumed.outputAs(Output.class).steps()).containsExactly("x-0", "x-1", "x-2");
		assertThat(this.recording.executions()).containsExactly("work:0", "work:1", "work:2", "finish");
	}

	@Test
	void reaperResumesRunWithExpiredLease() {
		this.fixtures.crashAfter.set("work:0");
		RunResult crashed = this.runs.start(LoopAgent.class, new Input("r", 2)).await(WAIT);
		assertThat(this.runStore.findExpiredLeases(java.time.Instant.now(), 10)).extracting((r) -> r.id())
			.containsExactly(crashed.runId());
		this.fixtures.crashAfter.set(null);

		RunReaper reaper = new RunReaper(this.runStore, (RunEngine) this.runs, java.time.Clock.systemUTC(), null, 10);
		assertThat(reaper.reap()).isEqualTo(1);

		awaitStatus(crashed.runId(), RunStatus.COMPLETED);
		assertThat(this.recording.count("work:0")).isEqualTo(1);
	}

	@Test
	void stepOnOtherBeanIsRecordedWithGenericReturnType() {
		RunResult result = this.runs.start(StepBeanAgent.class, new Input("s", 2)).await(WAIT);

		assertThat(result.completed()).isTrue();
		List<StepRecord> steps = this.stepStore.findByRun(result.runId());
		assertThat(steps).extracting(StepRecord::stepName).containsExactly("helperSteps.findSimilar", "helperSteps.apply", "total");
		assertThat(steps.get(0).outputType()).contains("List");
		assertThat(result.outputAs(Integer.class)).isEqualTo(2);

		// resume the completed run's history by hand: a replay must decode List<Similar>, not List<Map>
		RunEngine engine = (RunEngine) this.runs;
		this.runStore.update(this.runStore.find(result.runId()).orElseThrow()
			.withError(RunStatus.FAILED, "forced", java.time.Instant.now()));
		RunResult replayed = engine.resume(result.runId()).await(WAIT);
		assertThat(replayed.completed()).isTrue();
		assertThat(this.recording.count("findSimilar")).isEqualTo(1);
	}

	@Test
	void stepBeanOutsideRunExecutesNormally() {
		assertThat(this.fixtures.helper.findSimilar("plain")).hasSize(2);
	}

	@Test
	void approvalSuspendsThenResumesOnApprove() {
		RunResult suspended = this.runs.start(ApprovalAgent.class, new Input("a", 1)).await(WAIT);

		assertThat(suspended.status()).isEqualTo(RunStatus.SUSPENDED);
		assertThat(this.runs.status(suspended.runId())).isEqualTo(RunStatus.SUSPENDED);
		assertThat(this.recording.executions()).containsExactly("propose");
		List<PendingApproval> pending = this.approvals.pendingForRole("SUPPORT_LEAD");
		assertThat(pending).hasSize(1);
		assertThat(pending.get(0).stepName()).isEqualTo("apply");
		assertThat(this.approvals.pendingForRole("OTHER")).isEmpty();

		RunResult done = this.approvals.approve(pending.get(0).id(), "lead", "ok").await(WAIT);

		assertThat(done.completed()).isTrue();
		assertThat(this.recording.executions()).containsExactly("propose", "apply");
		assertThat(this.approvals.pendingForRole(null)).isEmpty();
		StepRecord apply = this.stepStore.find(done.runId(), key(done.runId(), "apply", 0)).orElseThrow();
		assertThat(apply.kind()).isEqualTo(StepKind.APPROVAL);
		assertThat(apply.status()).isEqualTo(StepStatus.COMPLETED);
	}

	@Test
	void rejectedApprovalFailsTheRun() {
		RunResult suspended = this.runs.start(ApprovalAgent.class, new Input("a", 1)).await(WAIT);
		String id = this.approvals.pendingForRole(null).get(0).id();

		RunResult done = this.approvals.reject(id, "lead", "no").await(WAIT);

		assertThat(done.status()).isEqualTo(RunStatus.FAILED);
		assertThat(done.error()).contains(ApprovalRejected.class.getName()).contains("rejected by lead: no");
		assertThat(this.recording.executions()).containsExactly("propose");
		assertThat(this.runs.summary(suspended.runId()).orElseThrow().status()).isEqualTo(RunStatus.FAILED);
	}

	@Test
	void retriesUntilSuccessAndPersistsAttempts() {
		this.fixtures.failFirstAttempts.set(2);

		RunResult result = this.runs.start(RetryAgent.class, new Input("r", 1)).await(WAIT);

		assertThat(result.completed()).isTrue();
		StepRecord flaky = this.stepStore.find(result.runId(), key(result.runId(), "flaky", 0)).orElseThrow();
		assertThat(flaky.attempt()).isEqualTo(3);
		assertThat(flaky.status()).isEqualTo(StepStatus.COMPLETED);
		assertThat(this.recording.count("flaky")).isEqualTo(3);
	}

	@Test
	void exhaustedRetriesFailTheRunAndRunCompensations() {
		this.fixtures.failFirstAttempts.set(99);

		RunResult result = this.runs.start(RetryAgent.class, new Input("r", 1)).await(WAIT);

		assertThat(result.status()).isEqualTo(RunStatus.FAILED);
		assertThat(result.error()).contains("boom");
		assertThat(this.recording.executions()).startsWith("reserve", "flaky", "flaky", "flaky").endsWith("undo-reserve");
		List<StepRecord> steps = this.stepStore.findByRun(result.runId());
		assertThat(steps).extracting(StepRecord::stepName).contains("compensate:reserve");
		assertThat(steps).filteredOn((s) -> s.stepName().equals("compensate:reserve")).extracting(StepRecord::kind)
			.containsExactly(StepKind.COMPENSATION);
	}

	@Test
	void timeoutFailsTheStep() {
		RunResult result = this.runs.start(TimeoutAgent.class, new Input("t", 1)).await(WAIT);

		assertThat(result.status()).isEqualTo(RunStatus.FAILED);
		assertThat(result.error()).contains(StepTimeout.class.getName());
		StepRecord slow = this.stepStore.find(result.runId(), key(result.runId(), "slow", 0)).orElseThrow();
		assertThat(slow.status()).isEqualTo(StepStatus.FAILED);
		assertThat(slow.error()).contains("timed out");
	}

	@Test
	void sideEffectReturnsSameValueOnReplay() {
		this.fixtures.crashAfter.set("work:0");
		RunResult crashed = this.runs.start(SideEffectAgent.class, new Input("s", 1)).await(WAIT);
		String first = this.fixtures.lastPaymentId.get();
		this.fixtures.crashAfter.set(null);

		RunResult resumed = this.runs.resume(crashed.runId()).await(WAIT);

		assertThat(resumed.completed()).isTrue();
		assertThat(resumed.outputAs(String.class)).isEqualTo(first);
		assertThat(this.recording.count("paymentId")).isEqualTo(1);
	}

	@Test
	void strictReplayDetectsDivergence() {
		this.fixtures.crashAfter.set("work:1");
		RunResult crashed = this.runs.start(LoopAgent.class, new Input("d", 3)).await(WAIT);
		this.fixtures.crashAfter.set(null);
		this.fixtures.divergeOnResume.set(true);

		RunResult resumed = this.runs.resume(crashed.runId()).await(WAIT);

		assertThat(resumed.status()).isEqualTo(RunStatus.FAILED);
		assertThat(resumed.error()).contains(NonDeterministicReplay.class.getName()).contains("recorded 'work'");
	}

	@Test
	void cancelStopsASuspendedRun() {
		RunResult suspended = this.runs.start(ApprovalAgent.class, new Input("c", 1)).await(WAIT);

		this.runs.cancel(suspended.runId(), "not needed");

		assertThat(this.runs.status(suspended.runId())).isEqualTo(RunStatus.CANCELLED);
		assertThatThrownBy(() -> this.runs.resume(suspended.runId())).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void findFiltersByAgentStatusAndCorrelation() {
		this.runs.start(LoopAgent.class, new Input("f", 1), "order-1").await(WAIT);
		this.runs.start(ApprovalAgent.class, new Input("f", 1), "order-2").await(WAIT);

		assertThat(this.runs.find(RunQuery.all()).total()).isEqualTo(2);
		assertThat(this.runs.find(RunQuery.forAgent("loop")).content()).hasSize(1);
		assertThat(this.runs.find(RunQuery.withStatus(RunStatus.SUSPENDED)).content()).extracting((s) -> s.correlationId())
			.containsExactly("order-2");
		assertThat(this.runs.find(RunQuery.all().correlation("order-1")).content()).hasSize(1);
	}

	@Test
	void nestedStepsAreChildren() {
		RunResult result = this.runs.start(NestedAgent.class, new Input("n", 1)).await(WAIT);

		assertThat(result.completed()).isTrue();
		List<StepRecord> steps = this.stepStore.findByRun(result.runId());
		StepRecord inner = steps.stream().filter((s) -> s.stepName().equals("inner")).findFirst().orElseThrow();
		assertThat(inner.kind()).isEqualTo(StepKind.CHILD);
		assertThat(inner.parentKey()).isEqualTo(key(result.runId(), "outer", 0));
	}

	private void awaitStatus(RunId id, RunStatus expected) {
		long deadline = System.currentTimeMillis() + WAIT.toMillis();
		while (System.currentTimeMillis() < deadline) {
			if (this.runs.status(id) == expected) {
				return;
			}
			try {
				Thread.sleep(20);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
		assertThat(this.runs.status(id)).isEqualTo(expected);
	}

	private static String key(RunId runId, String name, int n) {
		return runId + ":" + name + ":" + n;
	}

	// ------------------------------------------------------------------ fixtures

	record Input(String prefix, int count) {
	}

	record Output(List<String> steps) {
	}

	record Similar(String id, double score) {
	}

	@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
	@org.springframework.context.annotation.Import({ Recording.class, LoopAgent.class, StepBeanAgent.class, HelperSteps.class,
			ApprovalAgent.class, RetryAgent.class, TimeoutAgent.class, SideEffectAgent.class, NestedAgent.class })
	static class Fixtures {

		final AtomicReferenceString crashAfter = new AtomicReferenceString();

		final AtomicInteger failFirstAttempts = new AtomicInteger();

		final AtomicBoolean divergeOnResume = new AtomicBoolean();

		final AtomicReferenceString lastPaymentId = new AtomicReferenceString();

		@Autowired
		HelperSteps helper;

		void maybeCrash(String point) {
			if (point.equals(this.crashAfter.get())) {
				throw new SimulatedCrash("crash after " + point);
			}
		}

	}

	static final class AtomicReferenceString extends java.util.concurrent.atomic.AtomicReference<String> {
	}

	@DurableAgent("loop")
	static class LoopAgent implements Agent<Input, Output> {

		private final Recording recording;

		private final Fixtures fixtures;

		LoopAgent(Recording recording, Fixtures fixtures) {
			this.recording = recording;
			this.fixtures = fixtures;
		}

		@Override
		public Output run(Input input, Steps steps) {
			List<String> out = new ArrayList<>();
			for (int i = 0; i < input.count(); i++) {
				int n = i;
				String name = (this.fixtures.divergeOnResume.get() && n == 0) ? "changed" : "work";
				out.add(steps.run(name, String.class, () -> {
					this.recording.record("work:" + n);
					return input.prefix() + "-" + n;
				}));
				this.fixtures.maybeCrash("work:" + n);
			}
			steps.run("finish", () -> this.recording.record("finish"));
			return new Output(out);
		}

	}

	@Component
	static class HelperSteps {

		private final Recording recording;

		HelperSteps(Recording recording) {
			this.recording = recording;
		}

		@Step
		public List<Similar> findSimilar(String text) {
			this.recording.record("findSimilar");
			return List.of(new Similar("a", 0.9), new Similar("b", 0.5));
		}

		@Step(retry = @Retry(maxAttempts = 2, backoff = 1))
		public void apply(List<Similar> similar, StepContext context) {
			this.recording.record("apply:" + context.stepName() + ":" + context.attempt());
		}

	}

	@DurableAgent
	static class StepBeanAgent implements Agent<Input, Integer> {

		private final HelperSteps helper;

		StepBeanAgent(HelperSteps helper) {
			this.helper = helper;
		}

		@Override
		public Integer run(Input input, Steps steps) {
			List<Similar> similar = this.helper.findSimilar(input.prefix());
			this.helper.apply(similar, null);
			// similar must be a List<Similar> after replay too, otherwise this cast fails
			return steps.run("total", Integer.class, () -> similar.size() + (int) similar.get(0).score());
		}

	}

	@DurableAgent("approval")
	static class ApprovalAgent implements Agent<Input, String> {

		private final Recording recording;

		ApprovalAgent(Recording recording) {
			this.recording = recording;
		}

		@Override
		public String run(Input input, Steps steps) {
			String proposal = steps.run("propose", String.class, () -> {
				this.recording.record("propose");
				return "refund " + input.prefix();
			});
			return steps.approval("SUPPORT_LEAD", Duration.ofDays(1)).description(proposal).run("apply", String.class, () -> {
				this.recording.record("apply");
				return proposal + " applied";
			});
		}

	}

	@DurableAgent("retry")
	static class RetryAgent implements Agent<Input, String> {

		private final Recording recording;

		private final Fixtures fixtures;

		RetryAgent(Recording recording, Fixtures fixtures) {
			this.recording = recording;
			this.fixtures = fixtures;
		}

		@Override
		public String run(Input input, Steps steps) {
			steps.compensate(() -> this.recording.record("undo-reserve")).run("reserve", () -> this.recording.record("reserve"));
			return steps.retry(3, Duration.ofMillis(1)).run("flaky", String.class, () -> {
				this.recording.record("flaky");
				if (this.fixtures.failFirstAttempts.getAndDecrement() > 0) {
					throw new IllegalStateException("boom");
				}
				return "ok";
			});
		}

	}

	@DurableAgent("timeout")
	static class TimeoutAgent implements Agent<Input, String> {

		@Override
		public String run(Input input, Steps steps) {
			return steps.timeout(Duration.ofMillis(100)).run("slow", String.class, () -> {
				try {
					Thread.sleep(5_000);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
				return "late";
			});
		}

	}

	@DurableAgent("sideEffect")
	static class SideEffectAgent implements Agent<Input, String> {

		private final Recording recording;

		private final Fixtures fixtures;

		SideEffectAgent(Recording recording, Fixtures fixtures) {
			this.recording = recording;
			this.fixtures = fixtures;
		}

		@Override
		public String run(Input input, Steps steps) {
			String paymentId = steps.sideEffect("payment:" + input.prefix(), String.class, () -> {
				this.recording.record("paymentId");
				return java.util.UUID.randomUUID().toString();
			});
			this.fixtures.lastPaymentId.set(paymentId);
			steps.run("work", () -> this.recording.record("work:0"));
			this.fixtures.maybeCrash("work:0");
			return paymentId;
		}

	}

	@DurableAgent("nested")
	static class NestedAgent implements Agent<Input, String> {

		@Override
		public String run(Input input, Steps steps) {
			return steps.run("outer", String.class, () -> "outer:" + steps.run("inner", String.class, () -> "inner"));
		}

	}

}
