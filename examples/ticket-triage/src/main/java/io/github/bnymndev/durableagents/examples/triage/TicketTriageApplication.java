package io.github.bnymndev.durableagents.examples.triage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference application: a ticket arrives, an LLM classifies it, similar tickets are looked up, a
 * support lead approves the proposed resolution, the resolution is applied. Every step is durable;
 * kill the process at any point and start it again.
 */
@SpringBootApplication
public class TicketTriageApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketTriageApplication.class, args);
	}

}
