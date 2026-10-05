package dev.afkmod.logic;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Countdown measured in wall-clock time (a {@code System.nanoTime}-style clock) that can be paused,
 * so time spent while the server restarts is not counted.
 */
public final class PausableTimer {
	private final LongSupplier nanoClock;

	private long durationNanos;
	private long elapsedBeforePauseNanos;
	private long runningSince;
	private boolean running;
	private boolean started;

	public PausableTimer(LongSupplier nanoClock) {
		this.nanoClock = nanoClock;
	}

	/** Starts (or restarts) the countdown from {@code seconds}. 0 means "no timer". Starts running unless {@code paused}. */
	public void start(long seconds, boolean paused) {
		durationNanos = TimeUnit.SECONDS.toNanos(Math.max(0, seconds));
		elapsedBeforePauseNanos = 0;
		started = true;
		running = !paused;
		runningSince = nanoClock.getAsLong();
	}

	/** Starts the countdown from {@code nanos} (used to put back a remaining time). */
	public void startNanos(long nanos, boolean paused) {
		start(0, paused);
		durationNanos = Math.max(0, nanos);
	}

	public void stop() {
		started = false;
		running = false;
		elapsedBeforePauseNanos = 0;
		durationNanos = 0;
	}

	public void pause() {
		if (running) {
			elapsedBeforePauseNanos += nanoClock.getAsLong() - runningSince;
			running = false;
		}
	}

	public void resume() {
		if (started && !running) {
			runningSince = nanoClock.getAsLong();
			running = true;
		}
	}

	/** True when started with a non-zero duration. */
	public boolean hasTimer() {
		return started && durationNanos > 0;
	}

	/** The full duration the timer was started with, in whole seconds (0 when none). */
	public long durationSeconds() {
		return started ? TimeUnit.NANOSECONDS.toSeconds(durationNanos) : 0;
	}

	public boolean isPaused() {
		return started && !running;
	}

	public long elapsedNanos() {
		long current = running ? nanoClock.getAsLong() - runningSince : 0;
		return elapsedBeforePauseNanos + current;
	}

	public long remainingNanos() {
		return hasTimer() ? Math.max(0, durationNanos - elapsedNanos()) : 0;
	}

	/** Remaining time rounded up, so the display reads 0:00 only once the timer has actually ended. */
	public long remainingSeconds() {
		long nanos = remainingNanos();
		long perSecond = TimeUnit.SECONDS.toNanos(1);
		return (nanos + perSecond - 1) / perSecond;
	}

	public boolean isExpired() {
		return hasTimer() && elapsedNanos() >= durationNanos;
	}
}
