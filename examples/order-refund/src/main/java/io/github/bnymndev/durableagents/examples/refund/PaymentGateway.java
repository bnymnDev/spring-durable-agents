package io.github.bnymndev.durableagents.examples.refund;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Stands in for the payment provider. Reservations are idempotent on the key. */
@Component
public class PaymentGateway {

	private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();

	public record Reservation(String key, String orderNumber, BigDecimal amount) {
	}

	public String reserve(String key, String orderNumber, BigDecimal amount) {
		this.reservations.putIfAbsent(key, new Reservation(key, orderNumber, amount));
		return key;
	}

	public void releaseByOrder(String orderNumber) {
		this.reservations.values().removeIf((r) -> r.orderNumber().equals(orderNumber));
	}

	public Map<String, Reservation> reservations() {
		return Map.copyOf(this.reservations);
	}

	public void clear() {
		this.reservations.clear();
	}

}
