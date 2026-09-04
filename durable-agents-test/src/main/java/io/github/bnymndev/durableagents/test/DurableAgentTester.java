package io.github.bnymndev.durableagents.test;

import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.Agent;
import io.github.bnymndev.durableagents.AgentRuns;
import io.github.bnymndev.durableagents.Approvals;
import io.github.bnymndev.durableagents.PendingApproval;
import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunResult;
import io.github.bnymndev.durableagents.spi.StepStore;

/** Runs agents synchronously, simulates crashes, decides approvals and hands out assertions. */
public class DurableAgentTester {

	private final AgentRuns runs;

	private final Approvals approvals;

	private final StepStore steps;

	private final CrashSimulator crashes;

	private Duration timeout = Duration.ofSeconds(30);

	public DurableAgentTester(AgentRuns runs, Approvals approvals, StepStore steps, CrashSimulator crashes) {
		this.runs = runs;
		this.approvals = approvals;
		this.steps = steps;
		this.crashes = crashes;
	}

	public DurableAgentTester timeout(Duration timeout) {
		this.timeout = timeout;
		return this;
	}

	/** Starts a run and waits until this execution ends (completed, failed, suspended or crashed). */
	public <I> RunResult run(Class<? extends Agent<I, ?>> agent, I input) {
		return this.runs.start(agent, input).await(this.timeout);
	}

	public <I> RunResult run(Class<? extends Agent<I, ?>> agent, I input, @Nullable String correlationId) {
		return this.runs.start(agent, input, correlationId).await(this.timeout);
	}

	/** Resumes a run and waits for the execution to end. */
	public RunResult resume(RunId runId) {
		return this.runs.resume(runId).await(this.timeout);
	}

	/** Arms a one-shot crash right after the named step completes; the run is left resumable. */
	public DurableAgentTester crashAfter(String stepName) {
		this.crashes.crashAfter(stepName);
		return this;
	}

	public List<PendingApproval> pendingApprovals() {
		return this.approvals.pendingForRole(null);
	}

	/** Approves the single pending approval of {@code runId} as {@code decidedBy} and waits for the run. */
	public RunResult approve(RunId runId, String decidedBy) {
		return this.approvals.approve(pendingFor(runId).id(), decidedBy, null).await(this.timeout);
	}

	public RunResult reject(RunId runId, String decidedBy, @Nullable String comment) {
		return this.approvals.reject(pendingFor(runId).id(), decidedBy, comment).await(this.timeout);
	}

	private PendingApproval pendingFor(RunId runId) {
		List<PendingApproval> matching = pendingApprovals().stream().filter((a) -> a.runId().equals(runId)).toList();
		if (matching.size() != 1) {
			throw new AssertionError("Expected exactly one pending approval for run " + runId + " but found " + matching);
		}
		return matching.get(0);
	}

	public AgentRunAssert assertThatRun(RunId runId) {
		return new AgentRunAssert(runId, this.runs, this.steps);
	}

	public AgentRunAssert assertThatRun(RunResult result) {
		return assertThatRun(result.runId());
	}

	public AgentRuns runs() {
		return this.runs;
	}

	public Approvals approvals() {
		return this.approvals;
	}

}
