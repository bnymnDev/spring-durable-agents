package io.github.bnymndev.durableagents.examples.triage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import io.github.bnymndev.durableagents.RunResult;
import io.github.bnymndev.durableagents.test.DurableAgentTest;
import io.github.bnymndev.durableagents.test.DurableAgentTester;
import io.github.bnymndev.durableagents.test.FakeChatModel;

import static org.assertj.core.api.Assertions.assertThat;

@DurableAgentTest(includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TicketSystem.class))
class TicketTriageAgentTests {

	@Autowired
	DurableAgentTester agents;

	@Autowired
	FakeChatModel chat;

	@Autowired
	TicketSystem tickets;

	@BeforeEach
	void reset() {
		this.chat.reset();
	}

	@Test
	void crashAfterClassificationResumesWithoutCallingTheModelAgain() {
		this.chat.respondWithJson(new Classification("billing", Classification.Urgency.NORMAL, "wrong VAT on invoice"));
		Ticket ticket = new Ticket("T-1", "acme", "Invoice shows wrong VAT", "reverse charge customer");
		this.agents.crashAfter("classify");

		RunResult crashed = this.agents.run(TicketTriageAgent.class, ticket);
		this.agents.assertThatRun(crashed).crashed().hasSteps("classify");

		RunResult suspended = this.agents.resume(crashed.runId());
		this.agents.assertThatRun(suspended).suspended().hasSteps("classify", "findSimilar", "propose");
		assertThat(this.chat.calls()).isEqualTo(1);

		RunResult done = this.agents.approve(crashed.runId(), "lead");
		this.agents.assertThatRun(done).completed().hasSteps("classify", "findSimilar", "propose", "apply")
			.step("propose").wasApproved();
		assertThat(this.tickets.resolution("T-1")).isPresent()
			.get().extracting(Resolution::action).isEqualTo("apply known resolution: re-issued invoice with corrected VAT");
	}

	@Test
	void rejectedProposalLeavesTheTicketOpen() {
		this.chat.respondWithJson(new Classification("login", Classification.Urgency.HIGH, "cannot log in"));
		Ticket ticket = new Ticket("T-2", "acme", "Locked out", "MFA broken");
		this.tickets.open(ticket);

		RunResult suspended = this.agents.run(TicketTriageAgent.class, ticket);
		RunResult done = this.agents.reject(suspended.runId(), "lead", "call the customer first");

		this.agents.assertThatRun(done).failed().hasErrorContaining("rejected by lead");
		assertThat(this.tickets.find("T-2")).isPresent();
		assertThat(this.tickets.resolution("T-2")).isEmpty();
	}

}
