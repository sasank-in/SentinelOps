package io.sentinelops.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
class PaymentController {

	private final PaymentService service;

	PaymentController(PaymentService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<PaymentResponse> create(@Valid @RequestBody PaymentRequest request) {
		PaymentService.Result result;
		try {
			result = this.service.process(request.orderId(), request.amount(), request.currency());
		}
		catch (DataIntegrityViolationException ex) {
			// A concurrent request for the same order won the unique constraint.
			return this.service.findByOrder(request.orderId())
				.map((payment) -> ResponseEntity.ok(PaymentResponse.of(payment)))
				.orElseThrow(() -> ex);
		}
		HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(PaymentResponse.of(result.payment()));
	}

	@GetMapping("/{id}")
	ResponseEntity<PaymentResponse> get(@PathVariable UUID id) {
		return ResponseEntity.of(this.service.find(id).map(PaymentResponse::of));
	}

	@GetMapping(params = "orderId")
	ResponseEntity<PaymentResponse> getByOrder(@RequestParam UUID orderId) {
		return ResponseEntity.of(this.service.findByOrder(orderId).map(PaymentResponse::of));
	}

	record PaymentRequest(@NotNull UUID orderId, @NotNull @DecimalMin("0.01") BigDecimal amount,
			@NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {
	}

	record PaymentResponse(UUID id, UUID orderId, BigDecimal amount, String currency, PaymentStatus status,
			String declineReason, Instant createdAt) {

		static PaymentResponse of(Payment payment) {
			return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmount(),
					payment.getCurrency(), payment.getStatus(), payment.getDeclineReason(), payment.getCreatedAt());
		}

	}

}
