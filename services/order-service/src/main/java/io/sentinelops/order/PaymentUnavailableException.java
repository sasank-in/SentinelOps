package io.sentinelops.order;

import java.util.UUID;

public class PaymentUnavailableException extends RuntimeException {

	private final UUID orderId;

	PaymentUnavailableException(UUID orderId, Throwable cause) {
		super("Payment could not be processed for order " + orderId, cause);
		this.orderId = orderId;
	}

	public UUID getOrderId() {
		return this.orderId;
	}

}
