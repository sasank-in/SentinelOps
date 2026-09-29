package io.sentinelops.order;

public enum OrderStatus {

	/** Saved, payment not yet attempted or in flight. */
	PENDING,

	/** Payment approved. */
	PAID,

	/** Payment service answered and declined the charge. */
	PAYMENT_DECLINED,

	/** Payment service could not be reached or returned an error. */
	PAYMENT_FAILED

}
