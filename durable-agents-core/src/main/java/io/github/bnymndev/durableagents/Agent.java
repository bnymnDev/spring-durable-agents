package io.github.bnymndev.durableagents;

/**
 * A durable agent. The engine calls {@link #run(Object, Steps)} to start a run and calls it again,
 * with the same input, to resume a run after a crash, a redeploy or an approval.
 *
 * <p>The code <em>between</em> steps must be deterministic on replay: same input, same sequence of
 * step names. Everything that is not deterministic (LLM calls, I/O, {@code Instant.now()}, random
 * values) belongs inside a step, where it is executed once and replayed from the store afterwards.
 *
 * @param <I> the input type; must be serializable by the configured {@link io.github.bnymndev.durableagents.spi.StepCodec}
 * @param <O> the output type; must be serializable as well
 */
@FunctionalInterface
public interface Agent<I, O> {

	/**
	 * Runs the agent. Called once to start a run and once more per resume.
	 * @param input the run input
	 * @param steps the durable step API for this run
	 * @return the run output
	 */
	O run(I input, Steps steps);

}
