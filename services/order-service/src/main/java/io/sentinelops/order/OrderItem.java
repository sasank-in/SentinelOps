package io.sentinelops.order;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record OrderItem(@Column(nullable = false) String sku, @Column(nullable = false) int quantity,
		@Column(nullable = false) BigDecimal unitPrice) {

	BigDecimal lineTotal() {
		return this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
	}

}
