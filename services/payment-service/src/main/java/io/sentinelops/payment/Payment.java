package io.sentinelops.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private UUID orderId;

	@Column(nullable = false)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	private String declineReason;

	@Column(nullable = false)
	private Instant createdAt;

	protected Payment() {
	}

	Payment(UUID orderId, BigDecimal amount, String currency, PaymentStatus status, String declineReason) {
		this.id = UUID.randomUUID();
		this.orderId = orderId;
		this.amount = amount;
		this.currency = currency;
		this.status = status;
		this.declineReason = declineReason;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getOrderId() {
		return this.orderId;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public String getCurrency() {
		return this.currency;
	}

	public PaymentStatus getStatus() {
		return this.status;
	}

	public String getDeclineReason() {
		return this.declineReason;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
