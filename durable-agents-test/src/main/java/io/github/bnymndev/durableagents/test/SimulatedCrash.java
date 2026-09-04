package io.github.bnymndev.durableagents.test;

/**
 * Thrown by {@link CrashSimulator} right after a step completed. It is an {@link Error}, which the
 * engine treats like a process crash: nothing else is recorded, the run stays {@code RUNNING} with a
 * released lease, and {@code resume()} (or the reaper) continues it from the store.
 */
public final class SimulatedCrash extends Error {

	public SimulatedCrash(String stepName) {
		super("simulated crash after step '" + stepName + "'", null, false, false);
	}

}
