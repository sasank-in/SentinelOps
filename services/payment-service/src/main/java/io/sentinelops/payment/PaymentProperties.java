package io.sentinelops.payment;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Business rules for payment processing.
 *
 * @param maxAmount payments above this amount are declined
 * @param fraudCheckQueryDelay simulated duration of the fraud-check database query. Zero
 * in healthy releases; the "bad deployment" demo scenario raises it so each payment holds
 * a pool connection for longer.
 */
@ConfigurationProperties("payment")
public record PaymentProperties(BigDecimal maxAmount, Duration fraudCheckQueryDelay) {

	public PaymentProperties {
		maxAmount = (maxAmount != null) ? maxAmount : new BigDecimal("5000.00");
		fraudCheckQueryDelay = (fraudCheckQueryDelay != null) ? fraudCheckQueryDelay : Duration.ZERO;
	}

}
