package io.github.bnymndev.durableagents.examples.triage;

import java.time.Duration;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;

import io.github.bnymndev.durableagents.Agent;
import io.github.bnymndev.durableagents.DurableAgent;
import io.github.bnymndev.durableagents.Steps;
import io.github.bnymndev.durableagents.TypeRef;

/**
 * The agent. Four durable steps; the code between them is deterministic, everything else happens
 * inside a step. {@code run} is called once to start and once more per resume.
 */
@DurableAgent("ticket-triage")
public class TicketTriageAgent implements Agent<Ticket, Resolution> {

	private final ChatClient chat;

	private final TriageSteps triage;

	public TicketTriageAgent(ChatClient.Builder chatClientBuilder, TriageSteps triage) {
		this.chat = chatClientBuilder.build();
		this.triage = triage;
	}

	@Override
	public Resolution run(Ticket ticket, Steps steps) {
		// 1. LLM call: recorded with model, tokens and prompt hash; replayed on resume
		Classification classification = steps.llm("classify", Classification.class, () -> this.chat.prompt()
			.system("You triage support tickets. Answer with the requested JSON only.")
			.user((u) -> u.text("Subject: {subject}\n\n{body}").param("subject", ticket.subject()).param("body", ticket.body()))
			.call()
			.entity(Classification.class));

		// 2. @Step on another bean, retried up to three times; the generic return type survives replay
		List<SimilarTicket> similar = steps.retry(3, Duration.ofSeconds(2))
			.run("findSimilar", new TypeRef<List<SimilarTicket>>() {
			}, () -> this.triage.findSimilar(classification));

		// 3. a human decides before the proposed resolution is applied; the run suspends here
		Resolution resolution = steps.approval("SUPPORT_LEAD", Duration.ofDays(1))
			.description(classification.summary() + " (" + classification.urgency() + ")")
			.run("propose", Resolution.class, () -> this.triage.propose(ticket, classification, similar));

		// 4. the side effect, exactly once
		steps.run("apply", () -> this.triage.apply(resolution));
		return resolution;
	}

}
