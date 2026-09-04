package io.github.bnymndev.durableagents.examples.refund;

import java.math.BigDecimal;

/** Output of the agent. */
public record RefundResult(String orderNumber, boolean refunded, BigDecimal amount, String reservationId, String note) {
}
