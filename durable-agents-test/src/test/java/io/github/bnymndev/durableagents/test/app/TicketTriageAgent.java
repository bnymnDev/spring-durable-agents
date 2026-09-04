package io.github.bnymndev.durableagents.test.app;

import java.time.Duration;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;

import io.github.bnymndev.durableagents.Agent;
import io.github.bnymndev.durableagents.DurableAgent;
import io.github.bnymndev.durableagents.Steps;

@DurableAgent("ticket-triage")
public class TicketTriageAgent implements Agent<Ticket, Resolution> {

	private final ChatClient chat;

	private final TriageSteps triage;

	public TicketTriageAgent(ChatClient.Builder chat, TriageSteps triage) {
		this.chat = chat.build();
		this.triage = triage;
	}

	@Override
	public Resolution run(Ticket ticket, Steps steps) {
		Classification c = steps.llm("classify", Classification.class,
				() -> this.chat.prompt().user("Classify: " + ticket.body()).call().entity(Classification.class));
		List<String> similar = this.triage.findSimilar(c);
		return steps.approval("SUPPORT_LEAD", Duration.ofDays(1)).run("apply", Resolution.class,
				() -> new Resolution(c.category(), "Resolved like " + similar.get(0)));
	}

}
