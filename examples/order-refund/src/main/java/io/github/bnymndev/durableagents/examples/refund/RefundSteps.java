package io.github.bnymndev.durableagents.examples.refund;

import java.math.BigDecimal;
import java.util.Arrays;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import io.github.bnymndev.durableagents.Retry;
import io.github.bnymndev.durableagents.Step;
import io.github.bnymndev.durableagents.StepContext;

/** Steps with side effects, on their own bean so that Spring AOP can record them. */
@Component
public class RefundSteps {

	private final PaymentGateway payments;

	private final ToolCallbackProvider tools;

	public RefundSteps(PaymentGateway payments, ToolCallbackProvider tools) {
		this.payments = payments;
		this.tools = tools;
	}

	/**
	 * Reserves the refund. The idempotency key comes from {@link StepContext#sideEffect}, so a retry
	 * or a resume never reserves twice; {@code releaseReservation} undoes it when the run fails later.
	 */
	@Step(retry = @Retry(maxAttempts = 3, backoff = 500), compensate = "releaseReservation")
	public String reserve(String orderNumber, BigDecimal amount, @org.jspecify.annotations.Nullable StepContext context) {
		if (context == null) {
			throw new IllegalStateException("reserve() must be called from inside a run");
		}
		String key = context.sideEffect("reservation:" + orderNumber, String.class, () -> java.util.UUID.randomUUID().toString());
		return this.payments.reserve(key, orderNumber, amount);
	}

	public void releaseReservation(String orderNumber, BigDecimal amount, @org.jspecify.annotations.Nullable StepContext context) {
		this.payments.releaseByOrder(orderNumber);
	}

	/** Calls the MCP tool {@code order_state_transition} exposed by shopware-mcp through agentgate. */
	@Step(timeout = "PT30S")
	public String transitionOrder(String orderNumber, String transition) {
		ToolCallback tool = Arrays.stream(this.tools.getToolCallbacks())
			.filter((t) -> t.getToolDefinition().name().endsWith("order_state_transition"))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("MCP tool order_state_transition is not available"));
		return tool.call("{\"orderNumber\":\"" + orderNumber + "\",\"transition\":\"" + transition + "\",\"dryRun\":false}");
	}

}
