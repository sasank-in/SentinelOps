package io.sentinelops.chaos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of {@code POST /failure/{type}}. Both fields are optional.
 *
 * @param durationSeconds how long the failure stays active
 * @param intensity 1-100; meaning depends on the {@link FailureType}
 */
public record FailureRequest(@JsonProperty("duration_seconds") Integer durationSeconds, Integer intensity) {

	public static final int DEFAULT_INTENSITY = 50;

	public static FailureRequest defaults() {
		return new FailureRequest(null, null);
	}

}
