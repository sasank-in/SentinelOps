package io.sentinelops.payment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

	private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

	private final PaymentRepository repository;

	private final PaymentProperties properties;

	PaymentService(PaymentRepository repository, PaymentProperties properties) {
		this.repository = repository;
		this.properties = properties;
	}

	/**
	 * Authorizes a payment. Idempotent per order: a repeated request for the same order
	 * returns the original payment instead of charging twice.
	 */
	@Transactional
	public Result process(UUID orderId, BigDecimal amount, String currency) {
		Optional<Payment> existing = this.repository.findByOrderId(orderId);
		if (existing.isPresent()) {
			logger.info("Payment already exists for order {}, returning {}", orderId, existing.get().getId());
			return new Result(existing.get(), false);
		}
		if (!this.properties.fraudCheckQueryDelay().isZero()) {
			this.repository.runFraudCheck(this.properties.fraudCheckQueryDelay().toMillis() / 1000.0);
		}
		Payment payment = (amount.compareTo(this.properties.maxAmount()) > 0)
				? new Payment(orderId, amount, currency, PaymentStatus.DECLINED, "LIMIT_EXCEEDED")
				: new Payment(orderId, amount, currency, PaymentStatus.APPROVED, null);
		this.repository.saveAndFlush(payment);
		logger.info("Payment {} {} for order {}: amount={} {}", payment.getId(), payment.getStatus(), orderId,
				amount, currency);
		return new Result(payment, true);
	}

	@Transactional(readOnly = true)
	public Optional<Payment> find(UUID id) {
		return this.repository.findById(id);
	}

	@Transactional(readOnly = true)
	public Optional<Payment> findByOrder(UUID orderId) {
		return this.repository.findByOrderId(orderId);
	}

	public record Result(Payment payment, boolean created) {
	}

}
