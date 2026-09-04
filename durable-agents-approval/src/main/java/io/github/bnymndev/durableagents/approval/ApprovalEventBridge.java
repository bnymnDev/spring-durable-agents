package io.github.bnymndev.durableagents.approval;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import io.github.bnymndev.durableagents.event.ApprovalRequestedEvent;

/** Re-publishes the engine's {@link ApprovalRequestedEvent} as the externalizable {@link ApprovalRequested}. */
public class ApprovalEventBridge {

	private final ApplicationEventPublisher publisher;

	private final String basePath;

	public ApprovalEventBridge(ApplicationEventPublisher publisher, String basePath) {
		this.publisher = publisher;
		this.basePath = basePath;
	}

	@EventListener
	public void on(ApprovalRequestedEvent event) {
		this.publisher.publishEvent(new ApprovalRequested(event.approvalId(), event.runId().value(), event.agentName(),
				event.stepName(), event.requiredRole(), event.description(), event.expiresAt(), event.correlationId(),
				this.basePath + "/" + event.approvalId()));
	}

}
