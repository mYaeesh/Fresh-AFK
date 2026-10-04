package dev.afkmod.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PausableTimerTest {
	private final FakeClock clock = new FakeClock();
	private final PausableTimer timer = new PausableTimer(clock);

	@Test
	void countsDownInWallClockTime() {
		timer.start(60, false);
		clock.advanceSeconds(20);
		assertEquals(40, timer.remainingSeconds());
		assertFalse(timer.isExpired());
		clock.advanceSeconds(40);
		assertTrue(timer.isExpired());
		assertEquals(0, timer.remainingSeconds());
	}

	@Test
	void remainingSecondsRoundsUp() {
		timer.start(10, false);
		clock.advanceSeconds(0.5);
		assertEquals(10, timer.remainingSeconds());
		clock.advanceSeconds(9.4);
		assertEquals(1, timer.remainingSeconds());
	}

	@Test
	void pausedTimeDoesNotCount() {
		timer.start(60, false);
		clock.advanceSeconds(10);
		timer.pause();
		assertTrue(timer.isPaused());
		clock.advanceSeconds(1000);
		assertEquals(50, timer.remainingSeconds());
		assertFalse(timer.isExpired());
		timer.resume();
		clock.advanceSeconds(49);
		assertFalse(timer.isExpired());
		clock.advanceSeconds(1);
		assertTrue(timer.isExpired());
	}

	@Test
	void repeatedPauseAndResumeAreHarmless() {
		timer.start(30, false);
		clock.advanceSeconds(5);
		timer.pause();
		timer.pause();
		clock.advanceSeconds(5);
		timer.resume();
		timer.resume();
		clock.advanceSeconds(5);
		assertEquals(20, timer.remainingSeconds());
	}

	@Test
	void startPausedDoesNotRunUntilResumed() {
		timer.start(30, true);
		clock.advanceSeconds(100);
		assertEquals(30, timer.remainingSeconds());
		timer.resume();
		clock.advanceSeconds(10);
		assertEquals(20, timer.remainingSeconds());
	}

	@Test
	void restartFromNewValue() {
		timer.start(60, false);
		clock.advanceSeconds(50);
		timer.start(120, false);
		assertEquals(120, timer.remainingSeconds());
	}

	@Test
	void zeroMeansNoTimerAndNeverExpires() {
		timer.start(0, false);
		clock.advanceSeconds(1_000_000);
		assertFalse(timer.hasTimer());
		assertFalse(timer.isExpired());
		assertEquals(0, timer.remainingSeconds());
	}

	@Test
	void stopClearsTimer() {
		timer.start(10, false);
		timer.stop();
		clock.advanceSeconds(20);
		assertFalse(timer.hasTimer());
		assertFalse(timer.isExpired());
		timer.resume();
		assertFalse(timer.hasTimer());
	}
}
