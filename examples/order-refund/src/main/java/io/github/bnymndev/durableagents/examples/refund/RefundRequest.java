package io.github.bnymndev.durableagents.examples.refund;

/** Input: which order, why. */
public record RefundRequest(String orderNumber, String reason) {
}
