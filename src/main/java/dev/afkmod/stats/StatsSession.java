package dev.afkmod.stats;

/**
 * The live accumulator for one session (also used for the test-data counters). Durations come from a monotonic
 * nano clock; only the start and end timestamps use wall-clock time. Not thread safe (client thread only).
 */
final class StatsSession {
	private final boolean test;
	private final long startEpochMs;
	private final long startNanos;

	private long restartOpenSince = -1;
	private long restartNanosClosed;
	private long longestRestartNanos;
	private int restarts;
	private int stuckDetections;
	private int recoveryAttempts;
	private int recoverySuccesses;
	private int recoveryFailures;
	private long blocksMined;

	StatsSession(boolean test, long startEpochMs, long startNanos) {
		this.test = test;
		this.startEpochMs = startEpochMs;
		this.startNanos = startNanos;
	}

	/** A new restart begins. Ignored while one is already open (a repeat is not a new restart). */
	void restartStarted(long nowNanos) {
		if (restartOpenSince >= 0) return;
		restartOpenSince = nowNanos;
		restarts++;
	}

	/** The open restart ends. Returns its duration in ms, or -1 if none was open. */
	long restartEnded(long nowNanos) {
		if (restartOpenSince < 0) return -1;
		long d = Math.max(0, nowNanos - restartOpenSince);
		restartNanosClosed += d;
		longestRestartNanos = Math.max(longestRestartNanos, d);
		restartOpenSince = -1;
		return toMs(d);
	}

	boolean restartOpen() {
		return restartOpenSince >= 0;
	}

	void stuckDetected() {
		stuckDetections++;
	}

	void recoveryAttempt() {
		recoveryAttempts++;
	}

	void recoveryResult(boolean success) {
		if (success) recoverySuccesses++;
		else recoveryFailures++;
	}

	void blockMined() {
		blocksMined++;
	}

	int restarts() {
		return restarts;
	}

	/**
	 * A snapshot as of {@code nowNanos}. An open restart counts up to now. With a null {@code reason} the session is
	 * shown as running (no end time).
	 */
	SessionRecord snapshot(long nowNanos, long nowEpochMs, EndReason reason) {
		long openNanos = restartOpenSince >= 0 ? Math.max(0, nowNanos - restartOpenSince) : 0;
		long restartNanos = restartNanosClosed + openNanos;
		long wallNanos = Math.max(0, nowNanos - startNanos);
		SessionRecord r = new SessionRecord();
		r.startEpochMs = startEpochMs;
		r.endEpochMs = reason == null ? 0 : nowEpochMs;
		r.wallMs = toMs(wallNanos);
		r.restartMs = Math.min(toMs(restartNanos), r.wallMs);
		r.activeMs = r.wallMs - r.restartMs;
		r.restarts = restarts;
		r.longestRestartMs = Math.min(toMs(Math.max(longestRestartNanos, openNanos)), r.wallMs);
		r.stuckDetections = stuckDetections;
		r.recoveryAttempts = recoveryAttempts;
		r.recoverySuccesses = recoverySuccesses;
		r.recoveryFailures = recoveryFailures;
		r.blocksMined = blocksMined;
		r.endReason = reason;
		r.endedInLogout = reason != null && reason.isLogout();
		r.test = test;
		return r;
	}

	private static long toMs(long nanos) {
		return nanos / 1_000_000L;
	}
}
