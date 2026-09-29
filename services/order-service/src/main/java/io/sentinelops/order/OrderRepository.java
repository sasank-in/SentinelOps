package io.sentinelops.order;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrderRepository extends JpaRepository<Order, UUID> {

	List<Order> findTop50ByCustomerIdOrderByCreatedAtDesc(String customerId);

}
