package io.github.bnymndev.durableagents.internal;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.context.ApplicationEventPublisher;

import io.github.bnymndev.durableagents.spi.ApprovalStore;
import io.github.bnymndev.durableagents.spi.StepCodec;
import io.github.bnymndev.durableagents.spi.StepListener;
import io.github.bnymndev.durableagents.spi.StepStore;

/** Collaborators shared by all runs on this instance. */
public record RunContext(StepStore stepStore, ApprovalStore approvalStore, StepCodec codec,
		ApplicationEventPublisher publisher, List<StepListener> listeners, Executor executor, EngineSettings settings,
		Clock clock) {
}
