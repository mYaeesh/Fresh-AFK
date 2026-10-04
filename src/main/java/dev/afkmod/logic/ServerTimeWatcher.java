package dev.afkmod.logic;

/**
 * Tells whether the server is ticking, from the game time the server sends (about once a second).
 *
 * <p>The client keeps advancing its own copy of the game time even when the server is frozen, so only the
 * server-sent value is trustworthy. During a restart freeze it stops advancing; once it moves again the
 * world is responding.
 */
public final class ServerTimeWatcher {
	private boolean hasTime;
	private long latest;
	private boolean hasChecked;
	private long atLastCheck;

	/** Records a game time received from the server. */
	public void onServerTime(long gameTime) {
		latest = gameTime;
		hasTime = true;
	}

	/**
	 * True if the server-sent game time advanced since the previous call. The first call after a reset only
	 * takes a baseline and returns false.
	 */
	public boolean advancedSinceLastCheck() {
		if (!hasTime) return false;
		boolean advanced = hasChecked && latest > atLastCheck;
		atLastCheck = latest;
		hasChecked = true;
		return advanced;
	}

	/** Forgets everything, e.g. on joining a world (the game time may jump). */
	public void reset() {
		hasTime = false;
		hasChecked = false;
	}
}
