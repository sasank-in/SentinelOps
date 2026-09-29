package io.sentinelops.chaos;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ChaosEngineTests {

	private final CountDownLatch halted = new CountDownLatch(1);

	private final ChaosEngine engine = new ChaosEngine(new ChaosProperties(true, 60, 30, 10), () -> null,
			this.halted::countDown);

	@AfterEach
	void destroy() {
		this.engine.destroy();
	}

	@Test
	void startedFailureIsActiveUntilStopped() {
		this.engine.start(FailureType.LATENCY, new FailureRequest(30, 40));

		assertThat(this.engine.active()).singleElement().satisfies((failure) -> {
			assertThat(failure.type()).isEqualTo(FailureType.LATENCY);
			assertThat(failure.intensity()).isEqualTo(40);
		});
		assertThat(this.engine.stop(FailureType.LATENCY)).isTrue();
		assertThat(this.engine.active()).isEmpty();
		assertThat(this.engine.stop(FailureType.LATENCY)).isFalse();
	}

	@Test
	void failureExpiresAfterDuration() throws InterruptedException {
		this.engine.start(FailureType.ERROR_RATE, new FailureRequest(1, 100));
		assertThat(this.engine.shouldFailRequest()).isTrue();

		Thread.sleep(1_200);

		assertThat(this.engine.active()).isEmpty();
		assertThat(this.engine.shouldFailRequest()).isFalse();
	}

	@Test
	void startingSameTypeReplacesPreviousFailure() {
		this.engine.start(FailureType.LATENCY, new FailureRequest(30, 10));
		this.engine.start(FailureType.LATENCY, new FailureRequest(30, 90));

		assertThat(this.engine.active()).singleElement()
			.extracting(ActiveFailure::intensity)
			.isEqualTo(90);
	}

	@Test
	void latencyDelayScalesWithIntensityWithinJitter() {
		this.engine.start(FailureType.LATENCY, new FailureRequest(30, 50));

		// 50 intensity x 10 ms, +/-20%
		for (int i = 0; i < 50; i++) {
			assertThat(this.engine.requestDelayMillis()).isBetween(400L, 600L);
		}
	}

	@Test
	void noDelayOrErrorsWhenNothingIsActive() {
		assertThat(this.engine.requestDelayMillis()).isZero();
		assertThat(this.engine.shouldFailRequest()).isFalse();
	}

	@Test
	void defaultsApplyWhenRequestIsEmpty() {
		ActiveFailure failure = this.engine.start(FailureType.LATENCY, FailureRequest.defaults());

		assertThat(failure.intensity()).isEqualTo(FailureRequest.DEFAULT_INTENSITY);
		assertThat(failure.expiresAt()).isEqualTo(failure.startedAt().plusSeconds(30));
	}

	@Test
	void rejectsOutOfRangeValues() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.engine.start(FailureType.CPU, new FailureRequest(10, 0)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.engine.start(FailureType.CPU, new FailureRequest(10, 101)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.engine.start(FailureType.CPU, new FailureRequest(61, 50)));
		assertThat(this.engine.active()).isEmpty();
	}

	@Test
	void databaseFailureRequiresDataSource() {
		assertThatIllegalStateException()
			.isThrownBy(() -> this.engine.start(FailureType.DATABASE, FailureRequest.defaults()));
		assertThat(this.engine.active()).isEmpty();
	}

	@Test
	void crashHaltsAfterDelay() throws InterruptedException {
		this.engine.start(FailureType.CRASH, FailureRequest.defaults());

		assertThat(this.halted.await(5, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	void stoppingCrashBeforeDelayPreventsHalt() throws InterruptedException {
		this.engine.start(FailureType.CRASH, FailureRequest.defaults());
		this.engine.stop(FailureType.CRASH);

		assertThat(this.halted.await(3, TimeUnit.SECONDS)).isFalse();
	}

	@Test
	void parsesPathForm() {
		assertThat(FailureType.fromPath("error-rate")).isEqualTo(FailureType.ERROR_RATE);
		assertThat(FailureType.ERROR_RATE.toPath()).isEqualTo("error-rate");
		assertThatIllegalArgumentException().isThrownBy(() -> FailureType.fromPath("disk"));
	}

}
