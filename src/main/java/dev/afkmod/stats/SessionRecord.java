package dev.afkmod.stats;

/**
 * A snapshot of one session (running or finished). Plain fields so Gson can read and write it; durations are in
 * milliseconds. {@code endEpochMs} is 0 and {@code endReason} is null while the session is still running.
 */
public final class SessionRecord {
	public long startEpochMs;
	public long endEpochMs;
	public long wallMs;
	/** Wall time minus restart time. */
	public long activeMs;
	public long restartMs;
	public int restarts;
	public long longestRestartMs;
	public int stuckDetections;
	public int recoveryAttempts;
	public int recoverySuccesses;
	public int recoveryFailures;
	/** The mod itself disconnected to the server list at the end (timer end or recovery failed). */
	public boolean endedInLogout;
	public long blocksMined;
	public EndReason endReason;
	/** True only for sessions injected by the Test Lab; these never reach lifetime totals or history. */
	public boolean test;

	public long averageRestartMs() {
		return restarts == 0 ? 0 : restartMs / restarts;
	}

	public boolean isRunning() {
		return endReason == null;
	}

	public SessionRecord copy() {
		SessionRecord c = new SessionRecord();
		c.startEpochMs = startEpochMs;
		c.endEpochMs = endEpochMs;
		c.wallMs = wallMs;
		c.activeMs = activeMs;
		c.restartMs = restartMs;
		c.restarts = restarts;
		c.longestRestartMs = longestRestartMs;
		c.stuckDetections = stuckDetections;
		c.recoveryAttempts = recoveryAttempts;
		c.recoverySuccesses = recoverySuccesses;
		c.recoveryFailures = recoveryFailures;
		c.endedInLogout = endedInLogout;
		c.blocksMined = blocksMined;
		c.endReason = endReason;
		c.test = test;
		return c;
	}
}
