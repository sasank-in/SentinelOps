package io.sentinelops.chaos;

import java.util.Locale;

/**
 * Failure modes that can be injected into a running service.
 */
public enum FailureType {

	/** Busy-loop threads proportional to intensity (% of available cores). */
	CPU,

	/** Gradually retain heap until intensity % of max heap is used (simulated leak). */
	MEMORY,

	/** Hold intensity % of the JDBC pool's connections with long-running queries. */
	DATABASE,

	/** Add intensity x latency-ms-per-intensity of delay to every request. */
	LATENCY,

	/** Fail intensity % of requests with HTTP 500. */
	ERROR_RATE,

	/** Halt the JVM so the orchestrator restarts the container / pod. */
	CRASH;

	/** Parses the path form used by the REST API, e.g. {@code error-rate}. */
	public static FailureType fromPath(String value) {
		return FailureType.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
	}

	public String toPath() {
		return name().toLowerCase(Locale.ROOT).replace('_', '-');
	}

}
