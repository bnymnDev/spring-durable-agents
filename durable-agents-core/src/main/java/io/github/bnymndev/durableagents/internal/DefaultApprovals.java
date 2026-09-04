package io.github.bnymndev.durableagents.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;

import io.github.bnymndev.durableagents.ApprovalDecision;
import io.github.bnymndev.durableagents.Approvals;
import io.github.bnymndev.durableagents.PendingApproval;
import io.github.bnymndev.durableagents.RunHandle;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.event.ApprovalDecidedEvent;
import io.github.bnymndev.durableagents.spi.AgentRunStore;
import io.github.bnymndev.durableagents.spi.ApprovalRecord;
import io.github.bnymndev.durableagents.spi.ApprovalStore;
import io.github.bnymndev.durableagents.spi.RunRecord;
import io.github.bnymndev.durableagents.spi.StepRecord;
import io.github.bnymndev.durableagents.spi.StepStore;

/** Default {@link Approvals}: records the decision and resumes the run. */
public final class DefaultApprovals implements Approvals {

	private static final Log logger = LogFactory.getLog(DefaultApprovals.class);

	private final ApprovalStore approvals;

	private final AgentRunStore runs;

	private final StepStore steps;

	private final RunEngine engine;

	private final ApplicationEventPublisher publisher;

	private final Clock clock;

	public DefaultApprovals(ApprovalStore approvals, AgentRunStore runs, StepStore steps, RunEngine engine,
			ApplicationEventPublisher publisher, Clock clock) {
		this.approvals = approvals;
		this.runs = runs;
		this.steps = steps;
		this.engine = engine;
		this.publisher = publisher;
		this.clock = clock;
	}

	@Override
	public List<PendingApproval> pendingForRole(@Nullable String role) {
		return this.approvals.findPending(role).stream().map(this::toPending).toList();
	}

	@Override
	public Optional<PendingApproval> pending(String approvalId) {
		return this.approvals.find(approvalId)
			.filter((a) -> a.decision() == ApprovalDecision.PENDING)
			.map(this::toPending);
	}

	@Override
	public RunHandle approve(String approvalId, String decidedBy, @Nullable String comment) {
		return decide(approvalId, ApprovalDecision.APPROVED, decidedBy, comment);
	}

	@Override
	public RunHandle reject(String approvalId, String decidedBy, @Nullable String comment) {
		return decide(approvalId, ApprovalDecision.REJECTED, decidedBy, comment);
	}

	private RunHandle decide(String approvalId, ApprovalDecision decision, String decidedBy, @Nullable String comment) {
		ApprovalRecord approval = this.approvals.find(approvalId)
			.orElseThrow(() -> new IllegalArgumentException("Unknown approval " + approvalId));
		if (approval.decision() != ApprovalDecision.PENDING) {
			throw new IllegalStateException("Approval " + approvalId + " was already " + approval.decision());
		}
		ApprovalRecord decided = approval.decided(decision, this.clock.instant(), decidedBy, comment);
		this.approvals.update(decided);
		String agentName = this.runs.find(approval.runId()).map(RunRecord::agentName).orElse("unknown");
		this.publisher.publishEvent(new ApprovalDecidedEvent(approval.runId(), agentName, approvalId, approval.stepKey(),
				decision, decidedBy, comment));
		return this.engine.resume(approval.runId());
	}

	@Override
	public int expire() {
		Instant now = this.clock.instant();
		int count = 0;
		for (ApprovalRecord approval : this.approvals.findPendingExpiredBefore(now)) {
			this.approvals.update(approval.decided(ApprovalDecision.EXPIRED, now, null, "timeout"));
			String agentName = this.runs.find(approval.runId()).map(RunRecord::agentName).orElse("unknown");
			this.publisher.publishEvent(new ApprovalDecidedEvent(approval.runId(), agentName, approval.id(),
					approval.stepKey(), ApprovalDecision.EXPIRED, null, "timeout"));
			count++;
			boolean suspended = this.runs.find(approval.runId()).map((r) -> r.status() == RunStatus.SUSPENDED).orElse(false);
			if (suspended) {
				try {
					this.engine.resume(approval.runId());
				}
				catch (RuntimeException ex) {
					logger.warn("Could not resume run " + approval.runId() + " after approval timeout", ex);
				}
			}
		}
		return count;
	}

	private PendingApproval toPending(ApprovalRecord a) {
		Optional<RunRecord> run = this.runs.find(a.runId());
		String stepName = this.steps.find(a.runId(), a.stepKey()).map(StepRecord::stepName).orElse(a.stepKey());
		return new PendingApproval(a.id(), a.runId(), run.map(RunRecord::agentName).orElse("unknown"), a.stepKey(), stepName,
				a.requiredRole(), a.description(), a.requestedAt(), a.expiresAt(),
				run.map(RunRecord::correlationId).orElse(null));
	}

}
