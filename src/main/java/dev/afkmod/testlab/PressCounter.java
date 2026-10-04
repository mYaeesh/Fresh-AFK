package dev.afkmod.testlab;

/**
 * Counts the simulated key presses the mod sends ({@code ensureActive}/{@code ensureInactive} each record one when
 * they actually press), per key, in total and per check interval. Used by the key-check scenario to prove the mod
 * never presses a key more than once per check (which would toggle it back off). Presses made by the Test Lab itself
 * are not counted ({@link #setMuted}).
 *
 * <p>Keys are indexed like {@link TestEnvironment.Key} (crouch, attack, use).
 */
public final class PressCounter {
	public static final int KEYS = 3;

	private final long[] total = new long[KEYS];
	private final int[] thisCheck = new int[KEYS];
	private final int[] maxPerCheck = new int[KEYS];
	private boolean muted;

	/** One press sent by the mod for {@code key}. */
	public void record(int key) {
		if (muted || key < 0 || key >= KEYS) return;
		total[key]++;
		thisCheck[key]++;
		maxPerCheck[key] = Math.max(maxPerCheck[key], thisCheck[key]);
	}

	/** A new check interval begins: the per-check counts start from zero. */
	public void beginCheck() {
		for (int i = 0; i < KEYS; i++) thisCheck[i] = 0;
	}

	/** Forgets the highest per-check count seen so far (start of a measurement). */
	public void resetMax() {
		for (int i = 0; i < KEYS; i++) maxPerCheck[i] = thisCheck[i];
	}

	/** While muted, presses are not counted (the Test Lab's own presses). */
	public void setMuted(boolean muted) {
		this.muted = muted;
	}

	public long total(int key) {
		return total[key];
	}

	public long totalAll() {
		long sum = 0;
		for (long t : total) sum += t;
		return sum;
	}

	/** The most presses of {@code key} within one check interval since the last {@link #resetMax()}. */
	public int maxPerCheck(int key) {
		return maxPerCheck[key];
	}
}
