package io.github.bnymndev.durableagents;

/** What a step records. */
public enum StepKind {

	/** A plain step. */
	STEP,

	/** A step started inside another step. */
	CHILD,

	/** An LLM call or a step that wraps one. */
	LLM,

	/** A tool invoked by the model during an LLM turn, including MCP tools. */
	TOOL,

	/** An ad-hoc durable value ({@link Steps#sideEffect}). */
	SIDE_EFFECT,

	/** A step that waits for a human decision. */
	APPROVAL,

	/** A compensation of a completed step, run after the run failed. */
	COMPENSATION

}
