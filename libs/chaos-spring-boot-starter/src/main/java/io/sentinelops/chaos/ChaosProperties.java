package io.sentinelops.chaos;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for failure injection. Disabled by default; services enable it through
 * their {@code chaos} Spring profile.
 *
 * @param enabled whether the /failure endpoints and request filter are registered
 * @param maxDurationSeconds upper bound for any single injected failure
 * @param defaultDurationSeconds duration used when a request does not specify one
 * @param latencyMsPerIntensity added delay per intensity point for LATENCY failures
 */
@ConfigurationProperties("sentinelops.chaos")
public record ChaosProperties(boolean enabled, Integer maxDurationSeconds, Integer defaultDurationSeconds,
		Integer latencyMsPerIntensity) {

	public ChaosProperties {
		maxDurationSeconds = (maxDurationSeconds != null) ? maxDurationSeconds : 900;
		defaultDurationSeconds = (defaultDurationSeconds != null) ? defaultDurationSeconds : 180;
		latencyMsPerIntensity = (latencyMsPerIntensity != null) ? latencyMsPerIntensity : 50;
	}

}
