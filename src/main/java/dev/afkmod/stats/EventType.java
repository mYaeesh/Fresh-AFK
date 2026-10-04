package dev.afkmod.stats;

public enum EventType {
	TOGGLED_ON,
	TOGGLED_OFF,
	RESTART_DETECTED,
	QUEUE_TEXT_GONE,
	/** Joined a world (the relog after a restart, or any rejoin while the mod is on). */
	RECONNECTED,
	/** Back to ACTIVE after a restart or relog. */
	RESUMED,
	STUCK_DETECTED,
	YAW_SNAP,
	RECOVERY_ATTEMPT,
	RECOVERY_RESULT,
	TIMER_SET,
	TIMER_ENDED,
	LOGOUT,
	/** A Test Lab scenario finished (always test-flagged). */
	TEST_RESULT
}
