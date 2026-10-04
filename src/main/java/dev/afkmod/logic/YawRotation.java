package dev.afkmod.logic;

import java.util.concurrent.TimeUnit;

/**
 * One smooth turn from a start yaw to a target yaw, driven by wall-clock time so it takes the same time at any
 * tick or frame rate. The motion is smootherstep ease-in-out: it starts from rest, is fastest at the middle and
 * comes to rest exactly on the target, with monotonic progress and no overshoot. Only the yaw is involved;
 * the pitch is never part of this.
 */
public final class YawRotation {
	private final float startYaw;
	/** The target in the start yaw's winding (an exact multiple of 90 for a cardinal target). */
	private final float endYaw;
	private final long startNanos;
	private final long durationNanos;

	/**
	 * @param startYaw        the player's yaw now (any winding)
	 * @param targetYaw       where to end, e.g. a cardinal from {@link YawMath#nearestCardinal}; any winding
	 * @param durationSeconds total turn time; 0 means an instant snap
	 */
	public YawRotation(float startYaw, double targetYaw, long startNanos, double durationSeconds) {
		this.startYaw = startYaw;
		this.endYaw = (float) YawMath.unwrapTarget(startYaw, targetYaw);
		this.startNanos = startNanos;
		this.durationNanos = Math.round(Math.max(0.0, durationSeconds) * TimeUnit.SECONDS.toNanos(1));
	}

	/** Smootherstep {@code 6t^5 - 15t^4 + 10t^3}: speed and acceleration are zero at both ends. */
	public static double ease(double t) {
		if (t <= 0.0) return 0.0;
		if (t >= 1.0) return 1.0;
		return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
	}

	/** Fraction of the duration elapsed at {@code now}, in [0, 1]. */
	public double timeFraction(long now) {
		if (durationNanos == 0) return 1.0;
		return Math.clamp((double) (now - startNanos) / durationNanos, 0.0, 1.0);
	}

	/** The yaw to show at {@code now}. Exactly {@link #endYaw()} once the duration has passed. */
	public float yawAt(long now) {
		double t = timeFraction(now);
		if (t >= 1.0) return endYaw;
		return (float) (startYaw + ((double) endYaw - startYaw) * ease(t));
	}

	public boolean isFinished(long now) {
		return timeFraction(now) >= 1.0;
	}

	public float startYaw() {
		return startYaw;
	}

	public float endYaw() {
		return endYaw;
	}

	/** The signed turn in degrees (short way round, at most 180 either way). */
	public double arcDegrees() {
		return (double) endYaw - startYaw;
	}

	public long startNanos() {
		return startNanos;
	}

	public long durationNanos() {
		return durationNanos;
	}
}
