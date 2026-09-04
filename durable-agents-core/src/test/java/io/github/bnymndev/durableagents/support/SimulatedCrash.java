package io.github.bnymndev.durableagents.support;

/** An {@code Error}: the engine treats it like a process crash and leaves the run resumable. */
public final class SimulatedCrash extends Error {

	public SimulatedCrash(String message) {
		super(message);
	}

}
