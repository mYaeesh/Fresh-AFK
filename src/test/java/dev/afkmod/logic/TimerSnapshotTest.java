package dev.afkmod.logic;

import dev.afkmod.config.AfkConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The timer that persists across stop/resume must also survive the Test Lab's capture and restore. */
class TimerSnapshotTest {
	private final FakeClock clock = new FakeClock();
	private final AfkConfig config = new AfkConfig();
	private final AfkStateMachine sm = new AfkStateMachine(() -> config, clock);

	@Test
	void aRestoredTimerStillResumesAfterAStop() {
		config.timerSeconds = 100;
		sm.turnOn();
		clock.advanceSeconds(40);
		AfkStateMachine.TestSnapshot snapshot = sm.captureForTest();

		clock.advanceSeconds(10);
		sm.restoreAfterTest(snapshot);
		assertEquals(60, sm.timerRemainingSeconds());

		sm.turnOff();
		clock.advanceSeconds(500);
		sm.turnOn();
		assertEquals(60, sm.timerRemainingSeconds(), "kept, not restarted from 100");
		assertFalse(sm.isTimerPaused());
	}

	@Test
	void restoreWithNoTimerLeavesNoTimer() {
		sm.turnOn();
		AfkStateMachine.TestSnapshot snapshot = sm.captureForTest();
		sm.setTimerSeconds(30);
		assertTrue(sm.hasTimer());
		sm.restoreAfterTest(snapshot);
		assertFalse(sm.hasTimer());
	}

	@Test
	void aTimerOfADifferentLengthIsNotResumed() {
		config.timerSeconds = 100;
		sm.turnOn();
		clock.advanceSeconds(40);
		sm.turnOff();
		config.timerSeconds = 200;
		sm.turnOn();
		assertEquals(200, sm.timerRemainingSeconds());
	}
}
