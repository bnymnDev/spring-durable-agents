package io.github.bnymndev.durableagents.test;

import org.assertj.core.api.AbstractAssert;

import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.StepStatus;
import io.github.bnymndev.durableagents.spi.StepRecord;

/** Assertions on one recorded step. */
public class StepAssert extends AbstractAssert<StepAssert, StepRecord> {

	StepAssert(StepRecord actual) {
		super(actual, StepAssert.class);
	}

	public StepAssert hasStatus(StepStatus expected) {
		if (this.actual.status() != expected) {
			failWithMessage("Expected step %s to be %s but was %s", this.actual.stepKey(), expected, this.actual.status());
		}
		return this;
	}

	public StepAssert completed() {
		return hasStatus(StepStatus.COMPLETED);
	}

	public StepAssert failed() {
		return hasStatus(StepStatus.FAILED);
	}

	public StepAssert pendingApproval() {
		return hasStatus(StepStatus.PENDING_APPROVAL);
	}

	/** An approval step that a human approved and that then executed. */
	public StepAssert wasApproved() {
		hasKind(StepKind.APPROVAL);
		return completed();
	}

	public StepAssert hasKind(StepKind expected) {
		if (this.actual.kind() != expected) {
			failWithMessage("Expected step %s to be of kind %s but was %s", this.actual.stepKey(), expected, this.actual.kind());
		}
		return this;
	}

	public StepAssert hasAttempts(int expected) {
		if (this.actual.attempt() != expected) {
			failWithMessage("Expected step %s to have %s attempts but had %s", this.actual.stepKey(), expected, this.actual.attempt());
		}
		return this;
	}

	public StepAssert hasOutputContaining(String text) {
		String output = this.actual.output();
		if (output == null || !output.contains(text)) {
			failWithMessage("Expected output of step %s to contain <%s> but was <%s>", this.actual.stepKey(), text, output);
		}
		return this;
	}

	public StepAssert hasModel(String model) {
		if (!model.equals(this.actual.model())) {
			failWithMessage("Expected step %s to record model <%s> but was <%s>", this.actual.stepKey(), model, this.actual.model());
		}
		return this;
	}

	public StepRecord record() {
		return this.actual;
	}

}
