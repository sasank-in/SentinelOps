package io.sentinelops.chaos;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.ClassUtils;

/**
 * Starts, tracks and expires injected failures. At most one failure of each
 * {@link FailureType} is active at a time; starting a type again replaces it.
 * <p>
 * Chaos actions are logged under the {@code io.sentinelops.chaos} logger so that the
 * investigation tooling can exclude them: the AI agent must diagnose the symptoms, not
 * read the answer from the injector's own log lines.
 */
public class ChaosEngine implements DisposableBean {

	private static final Logger logger = LoggerFactory.getLogger(ChaosEngine.class);

	private static final boolean HIKARI_PRESENT = ClassUtils.isPresent("com.zaxxer.hikari.HikariDataSource",
			ChaosEngine.class.getClassLoader());

	private static final int MEMORY_CHUNK_BYTES = 1024 * 1024;

	private static final Duration CRASH_DELAY = Duration.ofSeconds(2);

	private final ChaosProperties properties;

	private final Supplier<DataSource> dataSource;

	private final Runnable halt;

	private final ScheduledExecutorService scheduler;

	private final Map<FailureType, Running> running = new ConcurrentHashMap<>();

	public ChaosEngine(ChaosProperties properties, Supplier<DataSource> dataSource) {
		this(properties, dataSource, () -> Runtime.getRuntime().halt(1));
	}

	ChaosEngine(ChaosProperties properties, Supplier<DataSource> dataSource, Runnable halt) {
		this.properties = properties;
		this.dataSource = dataSource;
		this.halt = halt;
		AtomicInteger count = new AtomicInteger();
		this.scheduler = Executors.newScheduledThreadPool(1,
				(runnable) -> daemon(runnable, "chaos-scheduler-" + count.incrementAndGet()));
	}

	public synchronized ActiveFailure start(FailureType type, FailureRequest request) {
		int intensity = (request.intensity() != null) ? request.intensity() : FailureRequest.DEFAULT_INTENSITY;
		int duration = (request.durationSeconds() != null) ? request.durationSeconds()
				: this.properties.defaultDurationSeconds();
		if (intensity < 1 || intensity > 100) {
			throw new IllegalArgumentException("intensity must be between 1 and 100");
		}
		if (duration < 1 || duration > this.properties.maxDurationSeconds()) {
			throw new IllegalArgumentException(
					"duration_seconds must be between 1 and " + this.properties.maxDurationSeconds());
		}
		stop(type);
		Instant now = Instant.now();
		ActiveFailure failure = new ActiveFailure(type, intensity, now, now.plusSeconds(duration));
		Runnable stopAction = switch (type) {
			case CPU -> startCpu(failure);
			case MEMORY -> startMemory(failure);
			case DATABASE -> startDatabase(failure);
			case LATENCY, ERROR_RATE -> () -> {
			};
			case CRASH -> startCrash();
		};
		ScheduledFuture<?> expiry = this.scheduler.schedule(() -> expire(failure), duration, TimeUnit.SECONDS);
		this.running.put(type, new Running(failure, stopAction, expiry));
		logger.info("Chaos failure started: type={}, intensity={}, duration={}s", type.toPath(), intensity,
				duration);
		return failure;
	}

	public synchronized boolean stop(FailureType type) {
		Running current = this.running.remove(type);
		if (current == null) {
			return false;
		}
		current.expiry().cancel(false);
		current.stopAction().run();
		logger.info("Chaos failure stopped: type={}", type.toPath());
		return true;
	}

	public synchronized void stopAll() {
		for (FailureType type : List.copyOf(this.running.keySet())) {
			stop(type);
		}
	}

	public List<ActiveFailure> active() {
		Instant now = Instant.now();
		return this.running.values()
			.stream()
			.map(Running::failure)
			.filter((failure) -> !failure.isExpired(now))
			.sorted(Comparator.comparing(ActiveFailure::type))
			.toList();
	}

	public Optional<ActiveFailure> active(FailureType type) {
		Running current = this.running.get(type);
		if (current == null || current.failure().isExpired(Instant.now())) {
			return Optional.empty();
		}
		return Optional.of(current.failure());
	}

	/** Delay to add to the current request, or 0 when no LATENCY failure is active. */
	public long requestDelayMillis() {
		return active(FailureType.LATENCY).map((failure) -> {
			long base = (long) failure.intensity() * this.properties.latencyMsPerIntensity();
			// +/-20% jitter so latency percentiles look like a real degradation.
			double jitter = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
			return Math.round(base * jitter);
		}).orElse(0L);
	}

	/** Whether the current request should fail, based on any active ERROR_RATE failure. */
	public boolean shouldFailRequest() {
		return active(FailureType.ERROR_RATE)
			.map((failure) -> ThreadLocalRandom.current().nextInt(100) < failure.intensity())
			.orElse(false);
	}

	@Override
	public void destroy() {
		stopAll();
		this.scheduler.shutdownNow();
	}

	private synchronized void expire(ActiveFailure failure) {
		Running current = this.running.get(failure.type());
		if (current != null && current.failure() == failure) {
			this.running.remove(failure.type());
			current.stopAction().run();
			logger.info("Chaos failure expired: type={}", failure.type().toPath());
		}
	}

	private Runnable startCpu(ActiveFailure failure) {
		int cores = Runtime.getRuntime().availableProcessors();
		int threads = Math.max(1, Math.round(cores * failure.intensity() / 100f));
		AtomicBoolean stopped = new AtomicBoolean();
		List<Thread> workers = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			Thread worker = daemon(() -> burnCpu(stopped, failure.expiresAt()), "chaos-cpu-" + i);
			workers.add(worker);
			worker.start();
		}
		return () -> {
			stopped.set(true);
			workers.forEach(Thread::interrupt);
		};
	}

	private static void burnCpu(AtomicBoolean stopped, Instant expiresAt) {
		double value = 1;
		while (!stopped.get() && Instant.now().isBefore(expiresAt)) {
			for (int i = 0; i < 100_000; i++) {
				value = Math.sqrt(value + i) * 1.000001;
			}
		}
		if (value == 42) {
			// Prevents the JIT from eliminating the loop as dead code.
			logger.trace("unreachable");
		}
	}

	private Runnable startMemory(ActiveFailure failure) {
		MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		long max = (heap.getMax() > 0) ? heap.getMax() : Runtime.getRuntime().maxMemory();
		long target = Math.max(0, max * failure.intensity() / 100 - heap.getUsed());
		long durationSeconds = Duration.between(failure.startedAt(), failure.expiresAt()).toSeconds();
		// Grow steadily over up to 60s so it looks like a leak rather than a single spike.
		long steps = Math.max(1, Math.min(durationSeconds, 60));
		long chunksPerStep = Math.max(1, target / MEMORY_CHUNK_BYTES / steps);
		long maxChunks = target / MEMORY_CHUNK_BYTES;
		List<byte[]> retained = Collections.synchronizedList(new ArrayList<>());
		ScheduledFuture<?> grower = this.scheduler.scheduleAtFixedRate(() -> {
			for (int i = 0; i < chunksPerStep && retained.size() < maxChunks; i++) {
				retained.add(new byte[MEMORY_CHUNK_BYTES]);
			}
		}, 0, 1, TimeUnit.SECONDS);
		return () -> {
			grower.cancel(false);
			retained.clear();
		};
	}

	private Runnable startDatabase(ActiveFailure failure) {
		DataSource source = this.dataSource.get();
		if (source == null) {
			throw new IllegalStateException("database failure requires a DataSource, but this service has none");
		}
		int poolSize = poolSize(source);
		int connections = Math.max(1, (int) Math.ceil(poolSize * failure.intensity() / 100.0));
		AtomicBoolean stopped = new AtomicBoolean();
		List<Statement> statements = new CopyOnWriteArrayList<>();
		List<Thread> holders = new ArrayList<>();
		for (int i = 0; i < connections; i++) {
			Thread holder = daemon(() -> holdConnection(source, failure.expiresAt(), stopped, statements),
					"chaos-db-" + i);
			holders.add(holder);
			holder.start();
		}
		return () -> {
			stopped.set(true);
			for (Statement statement : statements) {
				try {
					statement.cancel();
				}
				catch (SQLException ex) {
					// Best effort: the connection is closed when the holder thread exits.
				}
			}
			holders.forEach(Thread::interrupt);
		};
	}

	private static void holdConnection(DataSource source, Instant expiresAt, AtomicBoolean stopped,
			List<Statement> statements) {
		try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
			statements.add(statement);
			long seconds = Math.max(1, Duration.between(Instant.now(), expiresAt).toSeconds());
			try {
				// Keeps the connection busy server-side too, so it shows as active in
				// pg_stat_activity like a real slow query would.
				statement.execute("SELECT pg_sleep(" + seconds + ")");
			}
			catch (SQLException ex) {
				if (!stopped.get()) {
					sleepUntil(expiresAt, stopped);
				}
			}
		}
		catch (SQLException ex) {
			if (!stopped.get()) {
				logger.debug("Chaos could not acquire a connection: {}", ex.getMessage());
			}
		}
	}

	private static int poolSize(DataSource source) {
		if (HIKARI_PRESENT) {
			Integer size = HikariPoolSize.get(source);
			if (size != null) {
				return size;
			}
		}
		return 10;
	}

	private Runnable startCrash() {
		ScheduledFuture<?> crash = this.scheduler.schedule(() -> {
			logger.info("Chaos crash: halting JVM");
			this.halt.run();
		}, CRASH_DELAY.toMillis(), TimeUnit.MILLISECONDS);
		return () -> crash.cancel(false);
	}

	private static void sleepUntil(Instant deadline, AtomicBoolean stopped) {
		try {
			while (!stopped.get() && Instant.now().isBefore(deadline)) {
				Thread.sleep(Math.min(1000, Math.max(1, Duration.between(Instant.now(), deadline).toMillis())));
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static Thread daemon(Runnable runnable, String name) {
		Thread thread = new Thread(runnable, name);
		thread.setDaemon(true);
		return thread;
	}

	private record Running(ActiveFailure failure, Runnable stopAction, ScheduledFuture<?> expiry) {
	}

	/** Isolated so HikariCP stays an optional dependency. */
	private static final class HikariPoolSize {

		static Integer get(DataSource source) {
			try {
				if (source.isWrapperFor(HikariDataSource.class)) {
					return source.unwrap(HikariDataSource.class).getMaximumPoolSize();
				}
			}
			catch (SQLException ex) {
				// Fall through to the default.
			}
			return null;
		}

	}

}
