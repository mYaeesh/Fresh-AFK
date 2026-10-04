package dev.afkmod.logic;

/**
 * Counts failed movement-recovery attempts. An attempt fails when its walk fails (or it can't run: keys stuck,
 * unsafe path), or when the first full window after a "successful" walk is stuck again. {@code retries} retries
 * are allowed after the first failure, so {@code retries + 1} attempts in total. The count resets only when a full
 * window shows normal movement.
 */
public final class RecoveryAttempts {
	public enum Decision {
		/** Keep going (moving normally, or waiting for the next window). */
		NONE,
		/** Stuck: run a recovery attempt now. */
		START_ATTEMPT,
		/** Every attempt failed: release everything, turn off and disconnect. */
		GIVE_UP
	}

	private int failures;
	/** The last attempt's walk succeeded; the next full window tells whether it really worked. */
	private boolean awaitingVerification;

	/** A full window was evaluated in ACTIVE. */
	public Decision onWindow(boolean stuck, int retries) {
		if (!stuck) {
			failures = 0;
			awaitingVerification = false;
			return Decision.NONE;
		}
		if (awaitingVerification) {
			awaitingVerification = false;
			if (recordFailure(retries)) return Decision.GIVE_UP;
		}
		return Decision.START_ATTEMPT;
	}

	/** An attempt ended. A failure counts at once; a success is only confirmed by the next normal window. */
	public Decision onAttemptResult(boolean success, int retries) {
		if (success) {
			awaitingVerification = true;
			return Decision.NONE;
		}
		awaitingVerification = false;
		return recordFailure(retries) ? Decision.GIVE_UP : Decision.NONE;
	}

	public void reset() {
		failures = 0;
		awaitingVerification = false;
	}

	public int failures() {
		return failures;
	}

	/** The number of the attempt that would run next (1 = first). */
	public int nextAttemptNumber() {
		return failures + 1;
	}

	public boolean isAwaitingVerification() {
		return awaitingVerification;
	}

	/** Returns true when there are no retries left. */
	private boolean recordFailure(int retries) {
		failures++;
		return failures > Math.max(0, retries);
	}
}
