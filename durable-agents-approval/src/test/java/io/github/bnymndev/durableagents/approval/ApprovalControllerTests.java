package io.github.bnymndev.durableagents.approval;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.bnymndev.durableagents.Approvals;
import io.github.bnymndev.durableagents.PendingApproval;
import io.github.bnymndev.durableagents.RunHandle;
import io.github.bnymndev.durableagents.RunId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ApprovalControllerTests {

	private final Approvals approvals = mock(Approvals.class);

	private final RunId runId = RunId.next();

	private final PendingApproval lead = new PendingApproval("a-1", this.runId, "triage", this.runId + ":apply:0", "apply",
			"SUPPORT_LEAD", "refund 10 EUR", Instant.parse("2026-09-04T10:00:00Z"), Instant.parse("2026-09-05T10:00:00Z"), "T-1");

	private final PendingApproval cfo = new PendingApproval("a-2", this.runId, "triage", this.runId + ":pay:0", "pay", "CFO",
			null, Instant.parse("2026-09-04T10:00:00Z"), Instant.parse("2026-09-05T10:00:00Z"), null);

	private MockMvcTester mvc;

	@BeforeEach
	void setUp() {
		this.mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new ApprovalController(this.approvals, true))
			.addPlaceholderValue("durable-agents.approval.base-path", "/agents/approvals")
			.build());
		given(this.approvals.pendingForRole(null)).willReturn(List.of(this.lead, this.cfo));
		given(this.approvals.pendingForRole("CFO")).willReturn(List.of(this.cfo));
		given(this.approvals.pending("a-1")).willReturn(Optional.of(this.lead));
		given(this.approvals.pending("missing")).willReturn(Optional.empty());
		given(this.approvals.approve(any(), any(), any())).willReturn(new RunHandle(this.runId, new CompletableFuture<>()));
		given(this.approvals.reject(any(), any(), any())).willReturn(new RunHandle(this.runId, new CompletableFuture<>()));
	}

	@Test
	void listsOnlyApprovalsTheCallerMayDecide() {
		assertThat(this.mvc.get().uri("/agents/approvals").with(user("lead", "SUPPORT_LEAD")))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$[*].id")
			.asArray()
			.containsExactly("a-1");
	}

	@Test
	void listsByExplicitRole() {
		assertThat(this.mvc.get().uri("/agents/approvals?role=CFO").with(user("x", "NONE"))).hasStatusOk()
			.bodyJson().extractingPath("$[0].stepName").isEqualTo("pay");
	}

	@Test
	void approvesWithCommentAndPrincipal() {
		assertThat(this.mvc.post().uri("/agents/approvals/a-1/approve").with(user("alice", "SUPPORT_LEAD"))
			.contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"go\"}"))
			.hasStatus(202)
			.bodyJson().extractingPath("$.decision").isEqualTo("APPROVED");
		verify(this.approvals).approve("a-1", "alice", "go");
	}

	@Test
	void rejectsWithoutBody() {
		assertThat(this.mvc.post().uri("/agents/approvals/a-1/reject").with(user("bob", "SUPPORT_LEAD"))).hasStatus(202);
		verify(this.approvals).reject(eq("a-1"), eq("bob"), isNull());
	}

	@Test
	void forbidsCallersWithoutTheRole() {
		assertThat(this.mvc.post().uri("/agents/approvals/a-1/approve").with(user("eve", "INTERN"))).hasStatus(403);
		verify(this.approvals, never()).approve(any(), any(), any());
	}

	@Test
	void unknownApprovalIs404() {
		assertThat(this.mvc.post().uri("/agents/approvals/missing/approve").with(user("alice", "SUPPORT_LEAD"))).hasStatus(404);
	}

	@Test
	void alreadyDecidedIs409() {
		given(this.approvals.approve(any(), any(), any())).willThrow(new IllegalStateException("Approval a-1 was already APPROVED"));
		assertThat(this.mvc.post().uri("/agents/approvals/a-1/approve").with(user("alice", "SUPPORT_LEAD"))).hasStatus(409)
			.bodyJson().extractingPath("$.error").asString().contains("already");
	}

	@Test
	void rolesCanBeLeftToTheApplication() {
		MockMvcTester open = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new ApprovalController(this.approvals, false))
			.addPlaceholderValue("durable-agents.approval.base-path", "/agents/approvals")
			.build());
		assertThat(open.post().uri("/agents/approvals/a-1/approve")).hasStatus(202);
		verify(this.approvals).approve("a-1", "anonymous", null);
	}

	private static RequestPostProcessor user(String name, String... roles) {
		return (MockHttpServletRequest request) -> {
			request.setUserPrincipal(() -> name);
			for (String role : roles) {
				request.addUserRole(role);
			}
			return request;
		};
	}

}
