package io.github.bnymndev.durableagents;

import java.util.List;

/**
 * Thrown on resume in strict-replay mode ({@code durable-agents.strict-replay=true}) when the
 * sequence of step names produced by the code differs from the recorded one.
 */
public class NonDeterministicReplay extends IllegalStateException {

	public NonDeterministicReplay(RunId runId, int position, String recorded, String actual, List<String> history) {
		super("Run " + runId + " diverged from its history at step #" + position + ": recorded '" + recorded
				+ "' but the code called '" + actual + "'. Recorded sequence: " + history
				+ ". Either the agent code changed in an incompatible way or it is not deterministic between steps.");
	}

}
