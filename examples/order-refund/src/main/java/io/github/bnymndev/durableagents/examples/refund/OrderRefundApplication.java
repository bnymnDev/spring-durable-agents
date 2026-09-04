package io.github.bnymndev.durableagents.examples.refund;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Approval-heavy example: the model reads the order through MCP tools (shopware-mcp behind
 * agentgate), proposes a refund, finance approves amounts above a threshold, the order is
 * transitioned through an MCP tool, and a compensation releases the reservation if anything after
 * it fails. Demonstrates the three repositories together.
 */
@SpringBootApplication
public class OrderRefundApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderRefundApplication.class, args);
	}

}
