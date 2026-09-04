package io.github.bnymndev.durableagents.actuator;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.spi.RunListener;
import io.github.bnymndev.durableagents.spi.StepExecution;
import io.github.bnymndev.durableagents.spi.StepListener;

/** Puts {@code runId}, {@code agent} and {@code stepKey} into the SLF4J MDC while a run executes. */
public class MdcStepListener implements StepListener, RunListener {

	public static final String RUN_ID = "runId";

	public static final String AGENT = "agent";

	public static final String STEP_KEY = "stepKey";

	@Override
	public void onRunStarted(RunId runId, String agentName, boolean resumed) {
		MDC.put(RUN_ID, runId.value());
		MDC.put(AGENT, agentName);
	}

	@Override
	public void onRunFinished(RunId runId, String agentName, RunStatus status, Duration duration, @Nullable Throwable error) {
		MDC.remove(RUN_ID);
		MDC.remove(AGENT);
		MDC.remove(STEP_KEY);
	}

	@Override
	public void onStepStarted(StepExecution step) {
		MDC.put(STEP_KEY, step.stepKey());
	}

	@Override
	public void onStepFinished(StepExecution step, Duration duration, @Nullable Throwable error) {
		if (step.parentKey() != null) {
			MDC.put(STEP_KEY, step.parentKey());
		}
		else {
			MDC.remove(STEP_KEY);
		}
	}

}
