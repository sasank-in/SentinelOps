package io.sentinelops.order;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
class OrderController {

	private final OrderService service;

	OrderController(OrderService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
		List<OrderItem> items = request.items()
			.stream()
			.map((item) -> new OrderItem(item.sku(), item.quantity(), item.unitPrice()))
			.toList();
		Order order = this.service.placeOrder(request.customerId(), request.currency(), items);
		return ResponseEntity.created(URI.create("/api/orders/" + order.getId())).body(OrderResponse.of(order));
	}

	@GetMapping("/{id}")
	ResponseEntity<OrderResponse> get(@PathVariable UUID id) {
		return ResponseEntity.of(this.service.find(id).map(OrderResponse::of));
	}

	@GetMapping(params = "customerId")
	List<OrderResponse> listForCustomer(@RequestParam String customerId) {
		return this.service.findRecentForCustomer(customerId).stream().map(OrderResponse::of).toList();
	}

	record OrderRequest(@NotBlank @Size(max = 64) String customerId,
			@NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
			@NotEmpty @Size(max = 50) List<@Valid ItemRequest> items) {
	}

	record ItemRequest(@NotBlank @Size(max = 64) String sku, @Min(1) @Max(100) int quantity,
			@NotNull @DecimalMin("0.01") BigDecimal unitPrice) {
	}

	record OrderResponse(UUID id, String customerId, OrderStatus status, BigDecimal totalAmount, String currency,
			UUID paymentId, List<OrderItem> items, Instant createdAt, Instant updatedAt) {

		static OrderResponse of(Order order) {
			return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(), order.getTotalAmount(),
					order.getCurrency(), order.getPaymentId(), order.getItems(), order.getCreatedAt(),
					order.getUpdatedAt());
		}

	}

}
