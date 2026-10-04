package dev.afkmod.stats;

import java.util.LinkedHashMap;
import java.util.Map;

/** The sum of every finished real session. Plain fields for Gson. */
public final class LifetimeStats {
	public int sessions;
	public long wallMs;
	public long activeMs;
	public long restartMs;
	public int restarts;
	public long longestRestartMs;
	public int stuckDetections;
	public int recoveryAttempts;
	public int recoverySuccesses;
	public int recoveryFailures;
	public int logouts;
	public long blocksMined;
	/** Sessions per {@link EndReason} name. */
	public Map<String, Integer> endReasons = new LinkedHashMap<>();

	public long averageRestartMs() {
		return restarts == 0 ? 0 : restartMs / restarts;
	}

	/** Adds a finished session. Test sessions are refused (they must never reach the lifetime totals). */
	void add(SessionRecord s) {
		if (s.test) return;
		sessions++;
		wallMs += s.wallMs;
		activeMs += s.activeMs;
		restartMs += s.restartMs;
		restarts += s.restarts;
		longestRestartMs = Math.max(longestRestartMs, s.longestRestartMs);
		stuckDetections += s.stuckDetections;
		recoveryAttempts += s.recoveryAttempts;
		recoverySuccesses += s.recoverySuccesses;
		recoveryFailures += s.recoveryFailures;
		if (s.endedInLogout) logouts++;
		blocksMined += s.blocksMined;
		if (s.endReason != null) endReasons.merge(s.endReason.name(), 1, Integer::sum);
	}

	public LifetimeStats copy() {
		LifetimeStats c = new LifetimeStats();
		c.sessions = sessions;
		c.wallMs = wallMs;
		c.activeMs = activeMs;
		c.restartMs = restartMs;
		c.restarts = restarts;
		c.longestRestartMs = longestRestartMs;
		c.stuckDetections = stuckDetections;
		c.recoveryAttempts = recoveryAttempts;
		c.recoverySuccesses = recoverySuccesses;
		c.recoveryFailures = recoveryFailures;
		c.logouts = logouts;
		c.blocksMined = blocksMined;
		c.endReasons = new LinkedHashMap<>(endReasons == null ? Map.of() : endReasons);
		return c;
	}
}
