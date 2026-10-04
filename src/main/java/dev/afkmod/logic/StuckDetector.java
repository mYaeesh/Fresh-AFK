package dev.afkmod.logic;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * Sliding window of horizontal player positions, one sample per periodic check. The player counts as stuck when
 * the window covers at least {@code windowSeconds} and every sample in it is within {@code distanceBlocks}
 * (x/z only) of the current position.
 *
 * <p>The window is cleared whenever old samples would be misleading (toggle on, restart, rejoin, death, screens,
 * after each recovery attempt). {@link #forceStuck()} makes the next evaluation report stuck regardless of the
 * samples (for a later Test Lab).
 */
public final class StuckDetector {
	public record Sample(long nanos, double x, double z) {
	}

	/** The result of one evaluation, with what the debug log needs. */
	public record Evaluation(boolean windowFull, boolean stuck, double maxDistance, Sample oldest, Sample current,
			int samples, boolean forced) {
	}

	private final Deque<Sample> samples = new ArrayDeque<>();
	private boolean forceStuck;

	/** Adds a sample and drops those no longer needed to cover the last {@code windowSeconds}. */
	public void addSample(long nanos, double x, double z, double windowSeconds) {
		samples.addLast(new Sample(nanos, x, z));
		long window = windowNanos(windowSeconds);
		// Keep the newest sample that is at least a full window old, so the window stays exactly covered.
		while (samples.size() >= 2 && nanos - second().nanos() >= window) samples.removeFirst();
	}

	/** Drops every sample and a pending {@link #forceStuck()}, so nothing left over can trigger a recovery later. */
	public void clear() {
		samples.clear();
		forceStuck = false;
	}

	public boolean isForcePending() {
		return forceStuck;
	}

	/** The next {@link #evaluate} reports stuck once, whatever the samples say. */
	public void forceStuck() {
		forceStuck = true;
	}

	public int size() {
		return samples.size();
	}

	/** True when the samples span at least {@code windowSeconds} (oldest to newest). */
	public boolean isWindowFull(double windowSeconds) {
		if (samples.size() < 2) return false;
		return samples.peekLast().nanos() - samples.peekFirst().nanos() >= windowNanos(windowSeconds);
	}

	/** Largest horizontal distance from the newest sample to any sample in the window (0 if empty). */
	public double maxDistanceFromCurrent() {
		Sample current = samples.peekLast();
		if (current == null) return 0.0;
		double max = 0.0;
		for (Sample s : samples) max = Math.max(max, Math.hypot(s.x() - current.x(), s.z() - current.z()));
		return max;
	}

	/** Stuck = the window is full and the player stayed within {@code distanceBlocks} of the current spot. */
	public Evaluation evaluate(double windowSeconds, double distanceBlocks) {
		boolean forced = forceStuck;
		forceStuck = false;
		boolean full = isWindowFull(windowSeconds);
		double max = maxDistanceFromCurrent();
		boolean stuck = forced || (full && max < distanceBlocks);
		return new Evaluation(full || forced, stuck, max, samples.peekFirst(), samples.peekLast(), samples.size(), forced);
	}

	private Sample second() {
		Iterator<Sample> it = samples.iterator();
		it.next();
		return it.next();
	}

	private static long windowNanos(double seconds) {
		return Math.round(Math.max(0.0, seconds) * TimeUnit.SECONDS.toNanos(1));
	}
}
