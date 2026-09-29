package io.sentinelops.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class OrderService {

	private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

	private final OrderRepository repository;

	private final PaymentClient paymentClient;

	OrderService(OrderRepository repository, PaymentClient paymentClient) {
		this.repository = repository;
		this.paymentClient = paymentClient;
	}

	/**
	 * Saves the order and charges it. The payment call is made outside any database
	 * transaction so a slow payment-service cannot exhaust this service's connection pool.
	 * @throws PaymentUnavailableException if payment-service could not process the charge;
	 * the order is kept with status {@link OrderStatus#PAYMENT_FAILED}
	 */
	public Order placeOrder(String customerId, String currency, List<OrderItem> items) {
		Order order = this.repository.save(new Order(customerId, currency, items));
		PaymentClient.PaymentResult payment;
		try {
			payment = this.paymentClient.authorize(order.getId(), order.getTotalAmount(), currency);
		}
		catch (RestClientException ex) {
			logger.error("Payment call failed for order {}: {}", order.getId(), ex.getMessage());
			order.paymentFailed();
			this.repository.save(order);
			throw new PaymentUnavailableException(order.getId(), ex);
		}
		if (payment.approved()) {
			order.paymentApproved(payment.id());
		}
		else {
			order.paymentDeclined(payment.id());
		}
		order = this.repository.save(order);
		logger.info("Order {} {} for customer {}: total={} {}", order.getId(), order.getStatus(), customerId,
				order.getTotalAmount(), currency);
		return order;
	}

	public Optional<Order> find(UUID id) {
		return this.repository.findById(id);
	}

	public List<Order> findRecentForCustomer(String customerId) {
		return this.repository.findTop50ByCustomerIdOrderByCreatedAtDesc(customerId);
	}

}
