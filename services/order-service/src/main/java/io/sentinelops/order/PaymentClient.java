package io.sentinelops.order;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Synchronous client for payment-service. Timeouts are deliberately tight so a slow
 * payment-service shows up as order failures rather than piling up blocked requests.
 */
@Component
public class PaymentClient {

	private final RestClient restClient;

	PaymentClient(RestClient.Builder builder, Properties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
	}

	/**
	 * Requests authorization of a payment.
	 * @throws RestClientException if payment-service is unreachable, times out, or fails
	 */
	public PaymentResult authorize(UUID orderId, BigDecimal amount, String currency) {
		return this.restClient.post()
			.uri("/api/payments")
			.body(new PaymentRequest(orderId, amount, currency))
			.retrieve()
			.body(PaymentResult.class);
	}

	record PaymentRequest(UUID orderId, BigDecimal amount, String currency) {
	}

	public record PaymentResult(UUID id, String status, String declineReason) {

		boolean approved() {
			return "APPROVED".equals(this.status);
		}

	}

	@ConfigurationProperties("services.payment")
	record Properties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

		Properties {
			baseUrl = (baseUrl != null) ? baseUrl : "http://localhost:8082";
			connectTimeout = (connectTimeout != null) ? connectTimeout : Duration.ofSeconds(2);
			readTimeout = (readTimeout != null) ? readTimeout : Duration.ofSeconds(5);
		}

	}

}
