package dev.afkmod.stats;

import dev.afkmod.logic.AfkStateMachine.Reason;

/** Why a session ended (the mod turned OFF). */
public enum EndReason {
	TIMER_ENDED,
	MANUAL_TOGGLE,
	DEATH,
	MANUAL_DISCONNECT,
	RECOVERY_FAILED,
	RECONNECT_GRACE_EXPIRED,
	MAX_RESTART_WAIT_EXCEEDED,
	/** The session was still open when the game stopped or crashed. */
	INTERRUPTED,
	/** A Test Lab scenario turned the mod off and it couldn't be put back on (e.g. a confirmed real disconnect). */
	TEST_LAB;

	/** The end reason for a state-machine transition to OFF, or null if {@code reason} is not one that turns the mod off. */
	public static EndReason fromTransition(Reason reason) {
		if (reason == null) return null;
		return switch (reason) {
			case TOGGLED_OFF -> MANUAL_TOGGLE;
			case DEATH -> DEATH;
			case MANUAL_DISCONNECT -> MANUAL_DISCONNECT;
			case TIMER_END -> TIMER_ENDED;
			case RECOVERY_GAVE_UP -> RECOVERY_FAILED;
			case GRACE_EXPIRED -> RECONNECT_GRACE_EXPIRED;
			case RESTART_TIMEOUT -> MAX_RESTART_WAIT_EXCEEDED;
			default -> null;
		};
	}

	/** True when the mod itself disconnects to the server list after this end (timer end, recovery failed). */
	public boolean isLogout() {
		return this == TIMER_ENDED || this == RECOVERY_FAILED;
	}
}
