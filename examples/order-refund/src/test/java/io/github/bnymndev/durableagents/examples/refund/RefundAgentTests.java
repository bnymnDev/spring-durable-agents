package io.github.bnymndev.durableagents.examples.refund;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import io.github.bnymndev.durableagents.RunResult;
import io.github.bnymndev.durableagents.StepKind;
import io.github.bnymndev.durableagents.test.DurableAgentTest;
import io.github.bnymndev.durableagents.test.DurableAgentTester;
import io.github.bnymndev.durableagents.test.FakeChatModel;

import static org.assertj.core.api.Assertions.assertThat;

@DurableAgentTest(includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PaymentGateway.class))
@Import(RefundAgentTests.FakeShop.class)
class RefundAgentTests {

	@Autowired
	DurableAgentTester agents;

	@Autowired
	FakeChatModel chat;

	@Autowired
	PaymentGateway payments;

	@Autowired
	FakeShop shop;

	@BeforeEach
	void reset() {
		this.chat.reset();
		this.shop.transitions.clear();
		this.payments.clear();
	}

	@Test
	void smallRefundGoesThroughWithoutApproval() {
		this.chat.respondWithJson(new RefundAssessment(true, new BigDecimal("49.90"), "EUR", "damaged parcel"));

		RunResult done = this.agents.run(RefundAgent.class, new RefundRequest("10042", "damaged"));

		this.agents.assertThatRun(done).completed()
			.hasSteps("assess", "refundSteps.reserve", "refundSteps.transitionOrder", "notify")
			.step("refundSteps.transitionOrder").hasOutputContaining("refund");
		assertThat(this.shop.transitions).containsExactly("10042:refund");
		assertThat(this.payments.reservations()).hasSize(1);
	}

	@Test
	void largeRefundWaitsForFinanceAndSurvivesACrash() {
		this.chat.respondWithJson(new RefundAssessment(true, new BigDecimal("250.00"), "EUR", "wrong item shipped"));
		this.agents.crashAfter("refundSteps.reserve");

		RunResult crashed = this.agents.run(RefundAgent.class, new RefundRequest("10043", "wrong item"));
		this.agents.assertThatRun(crashed).crashed();

		RunResult suspended = this.agents.resume(crashed.runId());
		this.agents.assertThatRun(suspended).suspended().step("approveRefund").pendingApproval();
		assertThat(this.payments.reservations()).as("the reservation was replayed, not repeated").hasSize(1);
		assertThat(this.chat.calls()).isEqualTo(1);

		RunResult done = this.agents.approve(crashed.runId(), "finance");
		this.agents.assertThatRun(done).completed().step("approveRefund").hasKind(StepKind.APPROVAL).wasApproved();
		assertThat(this.shop.transitions).containsExactly("10043:refund");
	}

	@Test
	void rejectionReleasesTheReservation() {
		this.chat.respondWithJson(new RefundAssessment(true, new BigDecimal("999.00"), "EUR", "customer insists"));

		RunResult suspended = this.agents.run(RefundAgent.class, new RefundRequest("10044", "insists"));
		assertThat(this.payments.reservations()).hasSize(1);

		RunResult done = this.agents.reject(suspended.runId(), "finance", "no proof");

		this.agents.assertThatRun(done).failed().hasErrorContaining("rejected by finance");
		assertThat(this.agents.assertThatRun(done).steps()).extracting((s) -> s.stepName())
			.contains("compensate:refundSteps.reserve");
		assertThat(this.payments.reservations()).isEmpty();
		assertThat(this.shop.transitions).isEmpty();
	}

	@Test
	void ineligibleRequestEndsEarly() {
		this.chat.respondWithJson(new RefundAssessment(false, BigDecimal.ZERO, "EUR", "outside the return window"));

		RunResult done = this.agents.run(RefundAgent.class, new RefundRequest("10045", "changed my mind"));

		this.agents.assertThatRun(done).completed().hasSteps("assess");
		assertThat(done.outputAs(RefundResult.class).refunded()).isFalse();
	}

	/** Stands in for shopware-mcp behind agentgate. */
	@TestConfiguration(proxyBeanMethods = false)
	static class FakeShop {

		final List<String> transitions = new CopyOnWriteArrayList<>();

		@Bean
		ToolCallbackProvider toolCallbackProvider() {
			ToolCallback transition = new ToolCallback() {
				@Override
				public ToolDefinition getToolDefinition() {
					return ToolDefinition.builder().name("shopware_order_state_transition").description("Transition order state")
						.inputSchema("{\"type\":\"object\"}").build();
				}

				@Override
				public String call(String toolInput) {
					String order = toolInput.replaceAll(".*\"orderNumber\":\"([^\"]+)\".*", "$1");
					String transitionName = toolInput.replaceAll(".*\"transition\":\"([^\"]+)\".*", "$1");
					FakeShop.this.transitions.add(order + ":" + transitionName);
					return "{\"orderNumber\":\"" + order + "\",\"state\":\"" + transitionName + "ed\"}";
				}
			};
			return () -> new ToolCallback[] { transition };
		}

	}

}
