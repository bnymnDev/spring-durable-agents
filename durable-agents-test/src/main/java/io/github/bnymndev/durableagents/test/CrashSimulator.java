package io.github.bnymndev.durableagents.test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import io.github.bnymndev.durableagents.spi.StepExecution;
import io.github.bnymndev.durableagents.spi.StepListener;

/** Crashes the run once, right after the named step completed. Armed with {@link #crashAfter(String)}. */
public class CrashSimulator implements StepListener {

	private final AtomicReference<@Nullable String> armed = new AtomicReference<>();

	/** Arms a one-shot crash after the next completion of the step named {@code stepName}. */
	public void crashAfter(String stepName) {
		this.armed.set(stepName);
	}

	public void disarm() {
		this.armed.set(null);
	}

	public boolean isArmed() {
		return this.armed.get() != null;
	}

	@Override
	public void onStepFinished(StepExecution step, Duration duration, @Nullable Throwable error) {
		String current = this.armed.get();
		if (error == null && current != null && current.equals(step.stepName()) && this.armed.compareAndSet(current, null)) {
			throw new SimulatedCrash(step.stepName());
		}
	}

}
