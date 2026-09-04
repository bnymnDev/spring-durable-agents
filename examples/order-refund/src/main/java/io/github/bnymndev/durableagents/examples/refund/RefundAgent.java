package io.github.bnymndev.durableagents.examples.refund;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.ApplicationEventPublisher;

import io.github.bnymndev.durableagents.Agent;
import io.github.bnymndev.durableagents.ApprovalTimeoutPolicy;
import io.github.bnymndev.durableagents.DurableAgent;
import io.github.bnymndev.durableagents.Steps;

@DurableAgent("order-refund")
public class RefundAgent implements Agent<RefundRequest, RefundResult> {

	static final BigDecimal APPROVAL_THRESHOLD = new BigDecimal("100.00");

	private final ChatClient chat;

	private final ToolCallbackProvider tools;

	private final RefundSteps refunds;

	private final ApplicationEventPublisher events;

	public RefundAgent(ChatClient.Builder chatClientBuilder, ToolCallbackProvider tools, RefundSteps refunds,
			ApplicationEventPublisher events) {
		this.chat = chatClientBuilder.build();
		this.tools = tools;
		this.refunds = refunds;
		this.events = events;
	}

	@Override
	public RefundResult run(RefundRequest request, Steps steps) {
		// 1. the model reads the order with MCP tools; every tool call is a durable TOOL step
		RefundAssessment assessment = steps.llm("assess", RefundAssessment.class, () -> this.chat.prompt()
			.system("You decide refunds for a shop. Use the tools to read the order and its transactions. "
					+ "Refund the paid amount when the reason is justified. Answer with the requested JSON only.")
			.user((u) -> u.text("Order {order}. Customer says: {reason}").param("order", request.orderNumber())
				.param("reason", request.reason()))
			.tools(this.tools)
			.call()
			.entity(RefundAssessment.class));

		if (!assessment.eligible()) {
			return new RefundResult(request.orderNumber(), false, BigDecimal.ZERO, "", assessment.reasoning());
		}

		// 2. reserve the money; the compensation releases it if a later step fails.
		//    The StepContext parameter is injected by the engine, so the caller passes null.
		String reservationId = this.refunds.reserve(request.orderNumber(), assessment.amount(), null);

		// 3. finance approves anything above the threshold; small amounts go through
		if (assessment.amount().compareTo(APPROVAL_THRESHOLD) > 0) {
			steps.approval("FINANCE", Duration.ofDays(2))
				.onTimeout(ApprovalTimeoutPolicy.REJECT)
				.description("Refund " + assessment.amount() + " " + assessment.currency() + " for order " + request.orderNumber()
						+ ": " + assessment.reasoning())
				.run("approveRefund", () -> {
				});
		}

		// 4. the order state transition is an MCP tool as well, called directly, still a step
		this.refunds.transitionOrder(request.orderNumber(), "refund");

		// 5. tell the world, once; Modulith externalizes the event when a broker is configured
		steps.run("notify", () -> this.events
			.publishEvent(new RefundProcessed(request.orderNumber(), assessment.amount(), reservationId)));

		return new RefundResult(request.orderNumber(), true, assessment.amount(), reservationId, assessment.reasoning());
	}

}
