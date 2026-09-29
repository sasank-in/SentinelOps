package io.sentinelops.gateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

	/** Stands in for both downstream services and echoes the forwarded request. */
	static final HttpServer downstream = startDownstream();

	private final HttpClient client = HttpClient.newHttpClient();

	@LocalServerPort
	private int port;

	@DynamicPropertySource
	static void routes(DynamicPropertyRegistry registry) {
		String base = "http://localhost:" + downstream.getAddress().getPort();
		registry.add("ORDER_SERVICE_URL", () -> base);
		registry.add("PAYMENT_SERVICE_URL", () -> base);
	}

	@AfterAll
	static void stopDownstream() {
		downstream.stop(0);
	}

	@Test
	void routesOrdersToOrderService() throws Exception {
		HttpResponse<String> response = get("/api/orders/123");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("GET /api/orders/123");
	}

	@Test
	void routesPaymentsToPaymentService() throws Exception {
		HttpResponse<String> response = get("/api/payments?orderId=abc");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("GET /api/payments");
	}

	@Test
	void unknownPathIsNotRouted() throws Exception {
		assertThat(get("/api/unknown").statusCode()).isEqualTo(404);
	}

	private HttpResponse<String> get(String path) throws IOException, InterruptedException {
		return this.client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path)).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private static HttpServer startDownstream() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/", ApiGatewayApplicationTests::echo);
			server.start();
			return server;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void echo(HttpExchange exchange) throws IOException {
		byte[] body = (exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath())
			.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(200, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

}
