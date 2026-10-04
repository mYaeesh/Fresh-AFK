package dev.afkmod.logic;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Manually advanced nano clock. Starts at an arbitrary large value, like System.nanoTime may. */
final class FakeClock implements LongSupplier {
	private long now = 123_456_789_000L;

	@Override
	public long getAsLong() {
		return now;
	}

	void advanceSeconds(double seconds) {
		now += (long) (seconds * TimeUnit.SECONDS.toNanos(1));
	}
}
