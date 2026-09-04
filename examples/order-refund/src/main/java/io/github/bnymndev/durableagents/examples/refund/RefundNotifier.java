package io.github.bnymndev.durableagents.examples.refund;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.bnymndev.durableagents.approval.ApprovalRequested;

/**
 * A log-based notifier. Both events are Spring Modulith events: with {@code spring-modulith-events-kafka}
 * (or AMQP, SQS, JMS) on the classpath the same events go to a broker without a code change.
 */
@Component
public class RefundNotifier {

	private static final Log logger = LogFactory.getLog(RefundNotifier.class);

	@ApplicationModuleListener
	public void on(ApprovalRequested request) {
		logger.info("APPROVAL NEEDED (" + request.requiredRole() + "): " + request.description() + " -> POST "
				+ request.decideUrl() + "/approve");
	}

	@ApplicationModuleListener
	public void on(RefundProcessed refund) {
		logger.info("REFUNDED " + refund.amount() + " for order " + refund.orderNumber() + " (reservation "
				+ refund.reservationId() + ")");
	}

}
