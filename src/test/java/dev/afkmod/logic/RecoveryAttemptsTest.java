package dev.afkmod.logic;

import dev.afkmod.logic.RecoveryAttempts.Decision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryAttemptsTest {
	private static final int RETRIES = 2;
	private final RecoveryAttempts a = new RecoveryAttempts();

	@Test
	void threeFailedWalksGiveUp() {
		assertEquals(Decision.START_ATTEMPT, a.onWindow(true, RETRIES));
		assertEquals(1, a.nextAttemptNumber());
		assertEquals(Decision.NONE, a.onAttemptResult(false, RETRIES));
		assertEquals(Decision.START_ATTEMPT, a.onWindow(true, RETRIES), "a stuck window after a failed walk is not a 2nd failure");
		assertEquals(2, a.nextAttemptNumber());
		assertEquals(Decision.NONE, a.onAttemptResult(false, RETRIES));
		assertEquals(Decision.START_ATTEMPT, a.onWindow(true, RETRIES));
		assertEquals(3, a.nextAttemptNumber());
		assertEquals(Decision.GIVE_UP, a.onAttemptResult(false, RETRIES));
		assertEquals(3, a.failures());
	}

	@Test
	void stuckAgainAfterASuccessCountsAsAFailure() {
		a.onWindow(true, RETRIES);
		assertEquals(Decision.NONE, a.onAttemptResult(true, RETRIES));
		assertTrue(a.isAwaitingVerification());
		assertEquals(0, a.failures(), "a success isn't confirmed or failed yet");
		assertEquals(Decision.START_ATTEMPT, a.onWindow(true, RETRIES));
		assertEquals(1, a.failures());
		a.onAttemptResult(true, RETRIES);
		assertEquals(Decision.START_ATTEMPT, a.onWindow(true, RETRIES));
		assertEquals(2, a.failures());
		a.onAttemptResult(true, RETRIES);
		assertEquals(Decision.GIVE_UP, a.onWindow(true, RETRIES), "the 3rd attempt didn't help either");
	}

	@Test
	void mixedFailuresAddUp() {
		a.onWindow(true, RETRIES);
		a.onAttemptResult(true, RETRIES);
		a.onWindow(true, RETRIES);             // failure 1 (stuck again)
		a.onAttemptResult(false, RETRIES);     // failure 2 (walk)
		a.onWindow(true, RETRIES);
		assertEquals(Decision.GIVE_UP, a.onAttemptResult(false, RETRIES)); // failure 3
	}

	@Test
	void aNormalWindowResetsTheCounter() {
		a.onWindow(true, RETRIES);
		a.onAttemptResult(false, RETRIES);
		a.onWindow(true, RETRIES);
		a.onAttemptResult(false, RETRIES);
		assertEquals(2, a.failures());
		assertEquals(Decision.NONE, a.onWindow(false, RETRIES));
		assertEquals(0, a.failures());
		assertEquals(1, a.nextAttemptNumber());
	}

	@Test
	void aNormalWindowConfirmsASuccess() {
		a.onWindow(true, RETRIES);
		a.onAttemptResult(true, RETRIES);
		assertEquals(Decision.NONE, a.onWindow(false, RETRIES));
		assertFalse(a.isAwaitingVerification());
		assertEquals(0, a.failures());
	}

	@Test
	void zeroRetriesGivesUpOnTheFirstFailure() {
		a.onWindow(true, 0);
		assertEquals(Decision.GIVE_UP, a.onAttemptResult(false, 0));
	}

	@Test
	void resetClearsEverything() {
		a.onWindow(true, RETRIES);
		a.onAttemptResult(true, RETRIES);
		a.reset();
		assertFalse(a.isAwaitingVerification());
		assertEquals(Decision.START_ATTEMPT, a.onWindow(true, RETRIES));
		assertEquals(0, a.failures());
	}
}
