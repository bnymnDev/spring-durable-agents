package io.github.bnymndev.durableagents;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Handle on a run that was started or resumed on this instance. */
public final class RunHandle {

	private final RunId runId;

	private final CompletableFuture<RunResult> completion;

	public RunHandle(RunId runId, CompletableFuture<RunResult> completion) {
		this.runId = runId;
		this.completion = completion;
	}

	public RunId runId() {
		return this.runId;
	}

	/**
	 * Completes when this execution of the run ends: completed, failed, cancelled or suspended. A
	 * suspended run completes the future with status {@code SUSPENDED}; resume it to get a new handle.
	 */
	public CompletableFuture<RunResult> completion() {
		return this.completion;
	}

	/** Waits for this execution to end. */
	public RunResult await(Duration timeout) {
		try {
			return this.completion.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			throw new IllegalStateException("Run " + this.runId + " did not finish within " + timeout, ex);
		}
		catch (ExecutionException ex) {
			throw new IllegalStateException("Run " + this.runId + " execution failed", ex.getCause());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for run " + this.runId, ex);
		}
	}

}
