package io.github.bnymndev.durableagents.event;

import io.github.bnymndev.durableagents.RunId;

/** Base type of all events published by the engine through Spring's {@code ApplicationEventPublisher}. */
public sealed interface RunEvent
		permits RunStartedEvent, RunCompletedEvent, RunFailedEvent, RunSuspendedEvent, RunCancelledEvent, StepCompletedEvent,
		ApprovalRequestedEvent, ApprovalDecidedEvent {

	RunId runId();

	String agentName();

}
