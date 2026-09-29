package io.sentinelops.chaos;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Snapshot of a currently injected failure, as returned by {@code GET /failure}.
 */
public record ActiveFailure(FailureType type, int intensity, @JsonProperty("started_at") Instant startedAt,
		@JsonProperty("expires_at") Instant expiresAt) {

	public boolean isExpired(Instant now) {
		return !now.isBefore(this.expiresAt);
	}

}
