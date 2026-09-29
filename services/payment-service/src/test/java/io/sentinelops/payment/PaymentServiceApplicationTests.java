package io.sentinelops.payment;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("chaos")
@Testcontainers
class PaymentServiceApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	private MockMvc mvc;

	@Test
	void approvesPaymentWithinLimit() throws Exception {
		UUID orderId = UUID.randomUUID();

		this.mvc.perform(pay(orderId, "120.50"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.orderId").value(orderId.toString()));

		this.mvc.perform(get("/api/payments").param("orderId", orderId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(120.50));
	}

	@Test
	void declinesPaymentOverLimit() throws Exception {
		this.mvc.perform(pay(UUID.randomUUID(), "9000.00"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DECLINED"))
			.andExpect(jsonPath("$.declineReason").value("LIMIT_EXCEEDED"));
	}

	@Test
	void repeatedRequestForSameOrderIsIdempotent() throws Exception {
		UUID orderId = UUID.randomUUID();
		String id = com.jayway.jsonpath.JsonPath.read(
				this.mvc.perform(pay(orderId, "10.00")).andReturn().getResponse().getContentAsString(), "$.id");

		this.mvc.perform(pay(orderId, "10.00")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
	}

	@Test
	void rejectsInvalidRequest() throws Exception {
		this.mvc
			.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
				.content("{\"orderId\":\"" + UUID.randomUUID() + "\",\"amount\":-1,\"currency\":\"usd\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void unknownPaymentIsNotFound() throws Exception {
		this.mvc.perform(get("/api/payments/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void injectedErrorRateFailsApiButNotHealth() throws Exception {
		this.mvc
			.perform(post("/failure/error-rate").contentType(MediaType.APPLICATION_JSON)
				.content("{\"duration_seconds\":30,\"intensity\":100}"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.type").value("ERROR_RATE"));
		try {
			this.mvc.perform(pay(UUID.randomUUID(), "10.00")).andExpect(status().isInternalServerError());
			this.mvc.perform(get("/actuator/health")).andExpect(status().isOk());
			this.mvc.perform(get("/failure")).andExpect(jsonPath("$.active.length()").value(1));
		}
		finally {
			this.mvc.perform(delete("/failure")).andExpect(status().isNoContent());
		}
	}

	@Test
	void databaseFailureHoldsPoolConnections() throws Exception {
		this.mvc
			.perform(post("/failure/database").contentType(MediaType.APPLICATION_JSON)
				.content("{\"duration_seconds\":30,\"intensity\":100}"))
			.andExpect(status().isAccepted());
		try {
			Thread.sleep(500);
			// Every connection is held, so the request times out acquiring one.
			this.mvc.perform(pay(UUID.randomUUID(), "10.00")).andExpect(status().is5xxServerError());
		}
		finally {
			this.mvc.perform(delete("/failure/database")).andExpect(status().isNoContent());
		}
		Thread.sleep(500);
		this.mvc.perform(pay(UUID.randomUUID(), "10.00")).andExpect(status().isCreated());
	}

	private static org.springframework.test.web.servlet.RequestBuilder pay(UUID orderId, String amount) {
		return post("/api/payments").contentType(MediaType.APPLICATION_JSON)
			.content("{\"orderId\":\"%s\",\"amount\":%s,\"currency\":\"USD\"}".formatted(orderId, amount));
	}

}
