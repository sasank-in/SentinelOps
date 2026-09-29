package io.sentinelops.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PaymentRepository extends JpaRepository<Payment, UUID> {

	Optional<Payment> findByOrderId(UUID orderId);

	/**
	 * Stand-in for a fraud-scoring query. Its duration is configurable so a release can
	 * make it slow; see {@link PaymentProperties#fraudCheckQueryDelay()}.
	 */
	@Query(value = "SELECT pg_sleep(:seconds)::text", nativeQuery = true)
	String runFraudCheck(double seconds);

}
