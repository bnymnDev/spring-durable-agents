package io.github.bnymndev.durableagents.examples.refund;

import java.math.BigDecimal;

import org.springframework.modulith.events.Externalized;

/** Published when a refund went through; externalizable to Kafka, AMQP, SQS or JMS through Spring Modulith. */
@Externalized("refunds.processed::#{#this.orderNumber()}")
public record RefundProcessed(String orderNumber, BigDecimal amount, String reservationId) {
}
