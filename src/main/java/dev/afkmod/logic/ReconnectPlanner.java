package dev.afkmod.logic;

import java.util.concurrent.TimeUnit;

/**
 * Decides when to try joining the server again after a connection loss. It only does the counting and timing; the
 * Minecraft layer does the connecting. Plain Java so it can be unit tested.
 *
 * <p>One loss is one {@link #begin}: the first attempt comes {@code delaySeconds} after it, the next ones
 * {@code delaySeconds} after the previous attempt, up to {@code maxAttempts} in all. {@link #reset} ends it.
 */
public final class ReconnectPlanner {
	private boolean active;
	private int attempts;
	/** When the loss happened, or when the last attempt was made. */
	private long lastEventAt;

	/** A connection was just lost: start counting from now. */
	public void begin(long nowNanos) {
		active = true;
		attempts = 0;
		lastEventAt = nowNanos;
	}

	/** The loss is over (rejoined, or the mod turned off). */
	public void reset() {
		active = false;
		attempts = 0;
	}

	public boolean isActive() {
		return active;
	}

	/** Attempts made for the current loss. */
	public int attempts() {
		return attempts;
	}

	/**
	 * True exactly when an attempt should be made now, and counts it. {@code canConnect} is false while a connection
	 * is already being made or there is nothing to connect to; no attempt is made (and none is counted) then.
	 */
	public boolean shouldAttempt(long nowNanos, boolean enabled, int maxAttempts, int delaySeconds, boolean canConnect) {
		if (!active || !enabled || !canConnect || attempts >= maxAttempts) return false;
		if (nowNanos - lastEventAt < TimeUnit.SECONDS.toNanos(delaySeconds)) return false;
		attempts++;
		lastEventAt = nowNanos;
		return true;
	}
}
