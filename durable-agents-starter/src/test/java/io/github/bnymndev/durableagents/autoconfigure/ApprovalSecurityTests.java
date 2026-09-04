package io.github.bnymndev.durableagents.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

/** The autoconfigured filter chain protects the approval endpoints with HTTP Basic. */
@SpringBootTest(properties = { "durable-agents.store=memory", "durable-agents.scheduling.enabled=false",
		"spring.security.user.name=lead", "spring.security.user.password=secret", "spring.security.user.roles=SUPPORT_LEAD" })
@AutoConfigureMockMvc
class ApprovalSecurityTests {

	@Autowired
	MockMvcTester mvc;

	@Test
	void anonymousIsRejected() {
		assertThat(this.mvc.get().uri("/agents/approvals")).hasStatus(401);
	}

	@Test
	void authenticatedUserSeesPendingApprovals() {
		assertThat(this.mvc.get().uri("/agents/approvals").with(httpBasic("lead", "secret"))).hasStatusOk()
			.bodyJson().isLenientlyEqualTo("[]");
	}

	@Test
	void unknownApprovalIsNotFound() {
		assertThat(this.mvc.post().uri("/agents/approvals/nope/approve").with(httpBasic("lead", "secret"))).hasStatus(404);
	}

	@Test
	void actuatorEndpointAnswers() {
		assertThat(this.mvc.get().uri("/actuator/agents").with(httpBasic("lead", "secret"))).hasStatusOk()
			.bodyJson().extractingPath("$.pendingApprovals").isEqualTo(0);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class App {
	}

}
