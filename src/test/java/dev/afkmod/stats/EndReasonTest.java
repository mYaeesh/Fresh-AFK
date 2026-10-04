package dev.afkmod.stats;

import dev.afkmod.logic.AfkStateMachine.Reason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndReasonTest {
	@Test
	void everyTransitionThatTurnsTheModOffMapsToAnEndReason() {
		assertEquals(EndReason.MANUAL_TOGGLE, EndReason.fromTransition(Reason.TOGGLED_OFF));
		assertEquals(EndReason.DEATH, EndReason.fromTransition(Reason.DEATH));
		assertEquals(EndReason.MANUAL_DISCONNECT, EndReason.fromTransition(Reason.MANUAL_DISCONNECT));
		assertEquals(EndReason.TIMER_ENDED, EndReason.fromTransition(Reason.TIMER_END));
		assertEquals(EndReason.RECOVERY_FAILED, EndReason.fromTransition(Reason.RECOVERY_GAVE_UP));
		assertEquals(EndReason.RECONNECT_GRACE_EXPIRED, EndReason.fromTransition(Reason.GRACE_EXPIRED));
		assertEquals(EndReason.MAX_RESTART_WAIT_EXCEEDED, EndReason.fromTransition(Reason.RESTART_TIMEOUT));
	}

	@Test
	void reasonsThatDoNotEndASessionMapToNull() {
		assertNull(EndReason.fromTransition(Reason.TOGGLED_ON));
		assertNull(EndReason.fromTransition(Reason.RESTART_DETECTED));
		assertNull(EndReason.fromTransition(Reason.SETTLED));
		assertNull(EndReason.fromTransition(Reason.RECOVERED));
		assertNull(EndReason.fromTransition(null));
	}

	@Test
	void onlyTimerEndAndRecoveryFailureAreLogouts() {
		for (EndReason reason : EndReason.values()) {
			boolean expected = reason == EndReason.TIMER_ENDED || reason == EndReason.RECOVERY_FAILED;
			assertEquals(expected, reason.isLogout(), reason.name());
		}
		assertTrue(EndReason.TIMER_ENDED.isLogout());
		assertFalse(EndReason.DEATH.isLogout());
	}
}
