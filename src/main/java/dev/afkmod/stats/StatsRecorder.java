package dev.afkmod.stats;

import dev.afkmod.logic.AfkStateMachine.Reason;
import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.logic.DurationParser;

import java.util.Objects;

/**
 * Turns state-machine transitions into stats updates and events: starts and ends sessions, opens and closes restarts.
 * Pure Java so the mapping is unit tested. The recovery events (stuck, attempts, yaw snap) are recorded by
 * {@code MovementRecovery}, which knows the details.
 *
 * <p>In {@linkplain #setTestMode test mode} (a Test Lab scenario is running) every update and event is test-flagged and
 * the real session is never started or ended, so nothing a test does reaches the real counters, lifetime or history.
 */
public final class StatsRecorder {
	private final AfkStats stats;
	private boolean testMode;

	public StatsRecorder(AfkStats stats) {
		this.stats = Objects.requireNonNull(stats);
	}

	/** While true, everything recorded is test data and the real session is left alone. */
	public void setTestMode(boolean testMode) {
		this.testMode = testMode;
	}

	public boolean isTestMode() {
		return testMode;
	}

	public void onTransition(State from, State to, Reason reason) {
		if (reason == Reason.TOGGLED_ON) {
			if (!testMode) stats.startSession();
			stats.event(EventType.TOGGLED_ON, "AFK mining started", testMode);
			return;
		}
		if (to == State.OFF) {
			EndReason end = EndReason.fromTransition(reason);
			if (end == null) end = EndReason.INTERRUPTED;
			if (end == EndReason.TIMER_ENDED) stats.event(EventType.TIMER_ENDED, "Timer ended", testMode);
			stats.event(EventType.TOGGLED_OFF, "AFK mining stopped: " + end, testMode);
			if (!testMode) stats.endSession(end);
			return;
		}
		if (to == State.RESTARTING && reason == Reason.RESTART_DETECTED
				&& (from == State.ACTIVE || from == State.RECOVERING)) {
			stats.restartStarted(testMode);
			stats.event(EventType.RESTART_DETECTED, "Server restart detected", testMode);
			return;
		}
		if (from == State.SETTLING && to == State.ACTIVE) {
			long ms = stats.restartEnded(testMode);
			stats.event(EventType.RESUMED, ms >= 0
					? "Resumed after a restart of " + DurationParser.format(Math.round(ms / 1000.0))
					: "Resumed after a reconnect", testMode);
		}
	}

	/** A join while the mod is on. */
	public void onJoin(State state) {
		if (state == State.OFF) return;
		stats.event(EventType.RECONNECTED, "Joined the world while " + state, testMode);
	}

	/** The restart queue text went away while the mod is on. */
	public void onQueueTextGone(State state) {
		if (state == State.OFF) return;
		stats.event(EventType.QUEUE_TEXT_GONE, "Restart queue text gone", testMode);
	}

	/** The timer was set (0 = none). */
	public void onTimerSet(long seconds) {
		stats.event(EventType.TIMER_SET, seconds > 0 ? "Timer set to " + DurationParser.format(seconds) : "Timer cleared", testMode);
	}

	/** The mod disconnected to the server list (or, in a test, would have). */
	public void onLogout(String reason) {
		stats.event(EventType.LOGOUT, "Logged out: " + reason, testMode);
	}
}
