package io.github.bnymndev.durableagents;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Decides approvals. Deciding an approval resumes the run; the approval step then executes (approved)
 * or fails (rejected). Authorization is the caller's responsibility: check that the deciding user
 * holds {@link PendingApproval#requiredRole()}. The REST endpoints in {@code durable-agents-approval}
 * do exactly that.
 */
public interface Approvals {

	/** Pending approvals, optionally filtered by role. */
	List<PendingApproval> pendingForRole(@Nullable String role);

	Optional<PendingApproval> pending(String approvalId);

	/** Approves and resumes the run. */
	RunHandle approve(String approvalId, String decidedBy, @Nullable String comment);

	/** Rejects and resumes the run, which then fails at the approval step. */
	RunHandle reject(String approvalId, String decidedBy, @Nullable String comment);

	/** Marks pending approvals past their expiry as expired and resumes their runs. Returns the number expired. */
	int expire();

}
