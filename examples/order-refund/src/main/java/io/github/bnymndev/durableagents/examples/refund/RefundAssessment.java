package io.github.bnymndev.durableagents.examples.refund;

import java.math.BigDecimal;

/** Structured output of the assessment prompt. */
public record RefundAssessment(boolean eligible, BigDecimal amount, String currency, String reasoning) {
}
