package io.sentinelops.order;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.client.ResourceAccessException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderServiceApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private PaymentClient paymentClient;

	@Test
	void approvedPaymentMarksOrderPaid() throws Exception {
		UUID paymentId = UUID.randomUUID();
		given(this.paymentClient.authorize(any(), eq(new BigDecimal("59.97")), eq("USD")))
			.willReturn(new PaymentClient.PaymentResult(paymentId, "APPROVED", null));

		this.mvc.perform(order("cust-1"))
			.andExpect(status().isCreated())
			.andExpect(header().exists("Location"))
			.andExpect(jsonPath("$.status").value("PAID"))
			.andExpect(jsonPath("$.totalAmount").value(59.97))
			.andExpect(jsonPath("$.paymentId").value(paymentId.toString()));

		this.mvc.perform(get("/api/orders").param("customerId", "cust-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].items[0].sku").value("SKU-1"));
	}

	@Test
	void declinedPaymentIsRecorded() throws Exception {
		given(this.paymentClient.authorize(any(), any(), any()))
			.willReturn(new PaymentClient.PaymentResult(UUID.randomUUID(), "DECLINED", "LIMIT_EXCEEDED"));

		this.mvc.perform(order("cust-2"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PAYMENT_DECLINED"));
	}

	@Test
	void paymentServiceFailureReturns503AndKeepsFailedOrder() throws Exception {
		given(this.paymentClient.authorize(any(), any(), any()))
			.willThrow(new ResourceAccessException("Read timed out"));

		String orderId = com.jayway.jsonpath.JsonPath.read(this.mvc.perform(order("cust-3"))
			.andExpect(status().isServiceUnavailable())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.orderId");

		this.mvc.perform(get("/api/orders/{id}", orderId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));
	}

	@Test
	void rejectsOrderWithoutItems() throws Exception {
		this.mvc
			.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
				.content("{\"customerId\":\"c\",\"currency\":\"USD\",\"items\":[]}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void chaosEndpointsAreDisabledWithoutChaosProfile() throws Exception {
		this.mvc.perform(post("/failure/latency")).andExpect(status().isNotFound());
	}

	private static RequestBuilder order(String customerId) {
		return post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
				{"customerId":"%s","currency":"USD","items":[
				  {"sku":"SKU-1","quantity":2,"unitPrice":19.99},
				  {"sku":"SKU-2","quantity":1,"unitPrice":19.99}]}
				""".formatted(customerId));
	}

}
