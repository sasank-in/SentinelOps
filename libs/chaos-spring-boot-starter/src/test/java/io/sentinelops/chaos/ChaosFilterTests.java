package io.sentinelops.chaos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ChaosFilterTests {

	private final ChaosEngine engine = new ChaosEngine(new ChaosProperties(true, 60, 30, 10), () -> null);

	private final ChaosFilter filter = new ChaosFilter(this.engine);

	@AfterEach
	void destroy() {
		this.engine.destroy();
	}

	@Test
	void passesThroughWhenNoFailureIsActive() throws Exception {
		MockFilterChain chain = new MockFilterChain();
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(new MockHttpServletRequest("GET", "/api/orders"), response, chain);

		assertThat(chain.getRequest()).isNotNull();
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void failsRequestsWhenErrorRateIsActive() throws Exception {
		this.engine.start(FailureType.ERROR_RATE, new FailureRequest(30, 100));
		MockFilterChain chain = new MockFilterChain();
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(new MockHttpServletRequest("POST", "/api/payments"), response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(500);
		assertThat(response.getContentAsString()).contains("\"status\":500");
	}

	@Test
	void neverAffectsFailureOrActuatorEndpoints() throws Exception {
		this.engine.start(FailureType.ERROR_RATE, new FailureRequest(30, 100));

		for (String path : new String[] { "/failure", "/failure/latency", "/actuator/health" }) {
			MockFilterChain chain = new MockFilterChain();
			this.filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), chain);
			assertThat(chain.getRequest()).as(path).isNotNull();
		}
	}

}
