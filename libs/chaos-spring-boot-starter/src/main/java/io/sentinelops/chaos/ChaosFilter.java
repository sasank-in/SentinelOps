package io.sentinelops.chaos;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies LATENCY and ERROR_RATE failures to incoming requests. Runs after the
 * observation filter so injected delays and 500s show up in {@code http.server.requests}
 * metrics exactly like real ones.
 */
public class ChaosFilter extends OncePerRequestFilter implements Ordered {

	/**
	 * Symptoms are logged under an application-style logger (not the chaos logger) so
	 * they look like real failures to the investigation tooling.
	 */
	private static final Logger errorLogger = LoggerFactory.getLogger("io.sentinelops.http.ServerErrors");

	private final ChaosEngine engine;

	public ChaosFilter(ChaosEngine engine) {
		this.engine = engine;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.startsWith("/failure") || path.startsWith("/actuator");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long delay = this.engine.requestDelayMillis();
		if (delay > 0) {
			try {
				Thread.sleep(delay);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
		if (this.engine.shouldFailRequest()) {
			errorLogger.error("Request failed: {} {} -> 500: java.lang.IllegalStateException: "
					+ "Internal processing error", request.getMethod(), request.getRequestURI());
			response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			response.getWriter()
				.write("{\"type\":\"about:blank\",\"title\":\"Internal Server Error\",\"status\":500,"
						+ "\"detail\":\"Internal processing error\",\"instance\":\"" + request.getRequestURI()
						+ "\"}");
			return;
		}
		chain.doFilter(request, response);
	}

	@Override
	public int getOrder() {
		return 0;
	}

}
