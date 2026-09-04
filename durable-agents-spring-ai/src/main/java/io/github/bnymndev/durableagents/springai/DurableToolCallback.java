package io.github.bnymndev.durableagents.springai;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.internal.DefaultSteps;
import io.github.bnymndev.durableagents.internal.StepSpec;
import io.github.bnymndev.durableagents.internal.StepsHolder;

/**
 * Wraps a Spring AI {@link ToolCallback} ({@code @Tool} methods, MCP tools, function callbacks) so
 * that each execution during an active run is a durable step of kind {@code TOOL}. The step name is
 * {@code tool.<name>.<argument hash>} so that the same tool called with different arguments never
 * replays the wrong result; the arguments are stored as the step input.
 */
public final class DurableToolCallback implements ToolCallback {

	private final ToolCallback delegate;

	private DurableToolCallback(ToolCallback delegate) {
		this.delegate = delegate;
	}

	public static ToolCallback wrap(ToolCallback callback) {
		return (callback instanceof DurableToolCallback) ? callback : new DurableToolCallback(callback);
	}

	public ToolCallback delegate() {
		return this.delegate;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return this.delegate.getToolDefinition();
	}

	@Override
	public ToolMetadata getToolMetadata() {
		return this.delegate.getToolMetadata();
	}

	@Override
	public String call(String toolInput) {
		return call(toolInput, null);
	}

	@Override
	public String call(String toolInput, @Nullable ToolContext toolContext) {
		DefaultSteps steps = StepsHolder.current();
		if (steps == null) {
			return invoke(toolInput, toolContext);
		}
		String toolName = getToolDefinition().name();
		StepSpec spec = StepSpec.named("tool." + toolName + "." + Hashes.shortHash(toolInput))
			.kind(StepKind.TOOL)
			.type(String.class)
			.replayGuard(false)
			.input(toolInput);
		String result = steps.execute(spec, () -> invoke(toolInput, toolContext));
		return (result != null) ? result : "";
	}

	private String invoke(String toolInput, @Nullable ToolContext toolContext) {
		return (toolContext != null) ? this.delegate.call(toolInput, toolContext) : this.delegate.call(toolInput);
	}

	@Override
	public String toString() {
		return "DurableToolCallback[" + getToolDefinition().name() + "]";
	}

}
