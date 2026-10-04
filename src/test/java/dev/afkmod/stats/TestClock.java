package dev.afkmod.stats;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** A controllable monotonic and wall clock shared by the stats tests. */
final class TestClock {
	long nanos = 1_000_000_000L;
	long wallMs = 1_700_000_000_000L;

	void advanceSeconds(long seconds) {
		nanos += TimeUnit.SECONDS.toNanos(seconds);
		wallMs += TimeUnit.SECONDS.toMillis(seconds);
	}

	long nanoTime() {
		return nanos;
	}

	long wallTime() {
		return wallMs;
	}

	AfkStats stats(Path file) {
		return new AfkStats(file, this::nanoTime, this::wallTime);
	}
}
