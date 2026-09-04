package io.github.bnymndev.durableagents.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import io.github.bnymndev.durableagents.RunResult;
import io.github.bnymndev.durableagents.RunStatus;
import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.test.app.Classification;
import io.github.bnymndev.durableagents.test.app.Resolution;
import io.github.bnymndev.durableagents.test.app.Ticket;
import io.github.bnymndev.durableagents.test.app.TicketTriageAgent;
import io.github.bnymndev.durableagents.test.app.TriageSteps;
import io.github.bnymndev.durableagents.test.app.UnrelatedComponent;

import static org.assertj.core.api.Assertions.assertThat;

@DurableAgentTest
class DurableAgentTestSliceTests {

	@Autowired
	DurableAgentTester agents;

	@Autowired
	FakeChatModel chat;

	@Autowired
	TriageSteps triage;

	@Autowired
	ApplicationContext context;

	@AfterEach
	void reset() {
		this.chat.reset();
	}

	@Test
	void sliceContainsAgentsAndStepBeansOnly() {
		assertThat(this.context.getBeanNamesForType(TicketTriageAgent.class)).hasSize(1);
		assertThat(this.context.getBeanNamesForType(UnrelatedComponent.class)).isEmpty();
	}

	@Test
	void crashResumeApprove() {
		this.chat.respondWithJson(new Classification("billing", false));
		this.agents.crashAfter("classify");

		RunResult crashed = this.agents.run(TicketTriageAgent.class, new Ticket("T-1", "Invoice wrong"));
		this.agents.assertThatRun(crashed).crashed().hasSteps("classify");
		assertThat(this.chat.calls()).isEqualTo(1);

		RunResult suspended = this.agents.resume(crashed.runId());
		this.agents.assertThatRun(suspended).suspended().hasSteps("classify", "triageSteps.findSimilar", "apply")
			.step("apply").pendingApproval();
		assertThat(this.chat.calls()).as("the LLM step was replayed, not repeated").isEqualTo(1);

		RunResult done = this.agents.approve(crashed.runId(), "lead");
		this.agents.assertThatRun(done).completed()
			.hasOutput(Resolution.class, new Resolution("billing", "Resolved like T-100"))
			.step("apply").wasApproved();
		this.agents.assertThatRun(done).step("llm").hasKind(StepKind.LLM).hasModel(FakeChatModel.MODEL_NAME);
		assertThat(this.triage.similarCalls()).isEqualTo(1);
	}

	@Test
	void rejectionFailsTheRun() {
		this.chat.respondWithJson(new Classification("spam", false));
		RunResult suspended = this.agents.run(TicketTriageAgent.class, new Ticket("T-2", "Buy now"));
		assertThat(suspended.status()).isEqualTo(RunStatus.SUSPENDED);

		RunResult done = this.agents.reject(suspended.runId(), "lead", "not worth it");

		this.agents.assertThatRun(done).failed().hasErrorContaining("rejected by lead: not worth it");
	}

}
