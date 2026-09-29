package io.sentinelops.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String customerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus status;

	@Column(nullable = false)
	private BigDecimal totalAmount;

	@Column(nullable = false, length = 3)
	private String currency;

	private UUID paymentId;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
	private List<OrderItem> items = new ArrayList<>();

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Order() {
	}

	Order(String customerId, String currency, List<OrderItem> items) {
		this.id = UUID.randomUUID();
		this.customerId = customerId;
		this.currency = currency;
		this.items = new ArrayList<>(items);
		this.totalAmount = items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
		this.status = OrderStatus.PENDING;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	void paymentApproved(UUID paymentId) {
		transition(OrderStatus.PAID);
		this.paymentId = paymentId;
	}

	void paymentDeclined(UUID paymentId) {
		transition(OrderStatus.PAYMENT_DECLINED);
		this.paymentId = paymentId;
	}

	void paymentFailed() {
		transition(OrderStatus.PAYMENT_FAILED);
	}

	private void transition(OrderStatus next) {
		if (this.status != OrderStatus.PENDING) {
			throw new IllegalStateException("Order " + this.id + " is already " + this.status);
		}
		this.status = next;
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return this.id;
	}

	public String getCustomerId() {
		return this.customerId;
	}

	public OrderStatus getStatus() {
		return this.status;
	}

	public BigDecimal getTotalAmount() {
		return this.totalAmount;
	}

	public String getCurrency() {
		return this.currency;
	}

	public UUID getPaymentId() {
		return this.paymentId;
	}

	public List<OrderItem> getItems() {
		return List.copyOf(this.items);
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

}
