package io.github.bnymndev.durableagents.approval;

import java.security.Principal;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.bnymndev.durableagents.Approvals;
import io.github.bnymndev.durableagents.PendingApproval;
import io.github.bnymndev.durableagents.RunHandle;

/**
 * REST endpoints for approvals under {@code durable-agents.approval.base-path}
 * (default {@code /agents/approvals}):
 * <ul>
 * <li>{@code GET} lists pending approvals, filtered by {@code ?role=} or by the caller's roles</li>
 * <li>{@code POST /{id}/approve} and {@code POST /{id}/reject} with an optional JSON body
 * {@code {"comment": "..."}}</li>
 * </ul>
 * The caller must hold the approval's required role (checked through
 * {@link HttpServletRequest#isUserInRole(String)}, which Spring Security backs) unless
 * {@code durable-agents.approval.enforce-roles=false}.
 */
@RestController
@RequestMapping("${durable-agents.approval.base-path:/agents/approvals}")
public class ApprovalController {

	private final Approvals approvals;

	private final boolean enforceRoles;

	public ApprovalController(Approvals approvals, boolean enforceRoles) {
		this.approvals = approvals;
		this.enforceRoles = enforceRoles;
	}

	@GetMapping
	public List<PendingApproval> pending(@RequestParam(name = "role", required = false) @Nullable String role,
			HttpServletRequest request) {
		List<PendingApproval> all = this.approvals.pendingForRole(role);
		if (!this.enforceRoles || role != null) {
			return all;
		}
		return all.stream().filter((a) -> request.isUserInRole(a.requiredRole())).toList();
	}

	@GetMapping("/{id}")
	public PendingApproval one(@PathVariable("id") String id) {
		return this.approvals.pending(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No pending approval " + id));
	}

	@PostMapping("/{id}/approve")
	public ResponseEntity<DecisionResponse> approve(@PathVariable("id") String id,
			@RequestBody(required = false) @Nullable DecisionRequest body, HttpServletRequest request,
			@Nullable Principal principal) {
		PendingApproval approval = authorize(id, request);
		RunHandle handle = this.approvals.approve(id, decidedBy(principal), (body != null) ? body.comment() : null);
		return ResponseEntity.accepted().body(new DecisionResponse(id, approval.runId().value(), "APPROVED", handle.runId().value()));
	}

	@PostMapping("/{id}/reject")
	public ResponseEntity<DecisionResponse> reject(@PathVariable("id") String id,
			@RequestBody(required = false) @Nullable DecisionRequest body, HttpServletRequest request,
			@Nullable Principal principal) {
		PendingApproval approval = authorize(id, request);
		RunHandle handle = this.approvals.reject(id, decidedBy(principal), (body != null) ? body.comment() : null);
		return ResponseEntity.accepted().body(new DecisionResponse(id, approval.runId().value(), "REJECTED", handle.runId().value()));
	}

	private PendingApproval authorize(String id, HttpServletRequest request) {
		PendingApproval approval = this.approvals.pending(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No pending approval " + id));
		if (this.enforceRoles && !request.isUserInRole(approval.requiredRole())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Deciding this approval requires role " + approval.requiredRole());
		}
		return approval;
	}

	private static String decidedBy(@Nullable Principal principal) {
		return (principal != null) ? principal.getName() : "anonymous";
	}

	@org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ErrorResponse conflict(IllegalStateException ex) {
		return new ErrorResponse(String.valueOf(ex.getMessage()));
	}

	/** Request body of a decision. */
	public record DecisionRequest(@Nullable String comment) {
	}

	/** Response of a decision: the run has been resumed. */
	public record DecisionResponse(String approvalId, String runId, String decision, String resumedRunId) {
	}

	/** Error body. */
	public record ErrorResponse(String error) {
	}

}
