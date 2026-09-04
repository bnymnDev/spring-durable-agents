package io.github.bnymndev.durableagents.test;

import java.util.List;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import io.github.bnymndev.durableagents.AgentRuns;
import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.spi.StepRecord;
import io.github.bnymndev.durableagents.spi.StepStore;

/**
 * AssertJ assertions on a run and its recorded steps:
 * {@code assertThatRun(id).completed().hasSteps("classify", "findSimilar", "apply").step("apply").wasApproved()}.
 */
public class AgentRunAssert extends AbstractAssert<AgentRunAssert, RunId> {

	private final AgentRuns runs;

	private final StepStore steps;

	AgentRunAssert(RunId runId, AgentRuns runs, StepStore steps) {
		super(runId, AgentRunAssert.class);
		this.runs = runs;
		this.steps = steps;
	}

	public AgentRunAssert hasStatus(RunStatus expected) {
		RunStatus status = this.runs.status(this.actual);
		if (status != expected) {
			failWithMessage("Expected run %s to be %s but was %s%s", this.actual, expected, status, errorSuffix());
		}
		return this;
	}

	public AgentRunAssert completed() {
		return hasStatus(RunStatus.COMPLETED);
	}

	public AgentRunAssert failed() {
		return hasStatus(RunStatus.FAILED);
	}

	public AgentRunAssert suspended() {
		return hasStatus(RunStatus.SUSPENDED);
	}

	public AgentRunAssert cancelled() {
		return hasStatus(RunStatus.CANCELLED);
	}

	/** Still {@code RUNNING} in the store: crashed, waiting for a resume. */
	public AgentRunAssert crashed() {
		return hasStatus(RunStatus.RUNNING);
	}

	public AgentRunAssert hasOutput(Object expected) {
		Object output = this.runs.output(this.actual, Object.class).orElse(null);
		Assertions.assertThat(output).as("output of run %s", this.actual).isEqualTo(expected);
		return this;
	}

	public <O> AgentRunAssert hasOutput(Class<O> type, O expected) {
		O output = this.runs.output(this.actual, type).orElse(null);
		Assertions.assertThat(output).as("output of run %s", this.actual).isEqualTo(expected);
		return this;
	}

	public AgentRunAssert hasErrorContaining(String text) {
		String error = this.runs.summary(this.actual).map((s) -> s.error()).orElse(null);
		Assertions.assertThat(error).as("error of run %s", this.actual).contains(text);
		return this;
	}

	/** Top-level step names in execution order (children, side effects and compensations excluded). */
	public AgentRunAssert hasSteps(String... names) {
		List<String> actualNames = topLevel().stream().map(StepRecord::stepName).toList();
		Assertions.assertThat(actualNames).as("top-level steps of run %s", this.actual).containsExactly(names);
		return this;
	}

	public AgentRunAssert hasStepCount(int expected) {
		Assertions.assertThat(topLevel()).as("top-level steps of run %s", this.actual).hasSize(expected);
		return this;
	}

	/** Assertions on the first step with this name (use {@code name:n} keys via {@link #stepWithKey(String)} for loops). */
	public StepAssert step(String name) {
		StepRecord record = all().stream().filter((s) -> s.stepName().equals(name)).findFirst()
			.orElseThrow(() -> new AssertionError("Run " + this.actual + " has no step named '" + name + "'. Steps: "
					+ all().stream().map(StepRecord::stepName).toList()));
		return new StepAssert(record);
	}

	public StepAssert stepWithKey(String key) {
		StepRecord record = this.steps.find(this.actual, key)
			.orElseThrow(() -> new AssertionError("Run " + this.actual + " has no step with key '" + key + "'"));
		return new StepAssert(record);
	}

	public List<StepRecord> steps() {
		return all();
	}

	private List<StepRecord> all() {
		return this.steps.findByRun(this.actual);
	}

	private List<StepRecord> topLevel() {
		return all().stream()
			.filter((s) -> s.parentKey() == null && s.kind() != StepKind.SIDE_EFFECT && s.kind() != StepKind.COMPENSATION)
			.toList();
	}

	private String errorSuffix() {
		return this.runs.summary(this.actual).map((s) -> (s.error() != null) ? " (error: " + s.error() + ")" : "").orElse("");
	}

}
