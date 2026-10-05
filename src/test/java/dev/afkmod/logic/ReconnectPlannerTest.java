package dev.afkmod.logic;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconnectPlannerTest {
	private static long s(long seconds) {
		return TimeUnit.SECONDS.toNanos(seconds);
	}

	@Test
	void doesNothingBeforeBegin() {
		ReconnectPlanner planner = new ReconnectPlanner();
		assertFalse(planner.shouldAttempt(s(100), true, 5, 10, true));
	}

	@Test
	void firstAttemptComesAfterTheDelay() {
		ReconnectPlanner planner = new ReconnectPlanner();
		planner.begin(s(0));
		assertFalse(planner.shouldAttempt(s(9), true, 5, 10, true));
		assertTrue(planner.shouldAttempt(s(10), true, 5, 10, true));
		assertEquals(1, planner.attempts());
	}

	@Test
	void nextAttemptWaitsTheDelayFromThePreviousOne() {
		ReconnectPlanner planner = new ReconnectPlanner();
		planner.begin(s(0));
		assertTrue(planner.shouldAttempt(s(10), true, 5, 10, true));
		assertFalse(planner.shouldAttempt(s(19), true, 5, 10, true));
		assertTrue(planner.shouldAttempt(s(20), true, 5, 10, true));
		assertEquals(2, planner.attempts());
	}

	@Test
	void stopsAtTheMaximum() {
		ReconnectPlanner planner = new ReconnectPlanner();
		planner.begin(s(0));
		for (int i = 1; i <= 3; i++) assertTrue(planner.shouldAttempt(s(i * 10L), true, 3, 10, true));
		assertFalse(planner.shouldAttempt(s(1000), true, 3, 10, true));
		assertEquals(3, planner.attempts());
	}

	@Test
	void disabledOrBusyDoesNotAttemptOrCount() {
		ReconnectPlanner planner = new ReconnectPlanner();
		planner.begin(s(0));
		assertFalse(planner.shouldAttempt(s(50), false, 5, 10, true));
		assertFalse(planner.shouldAttempt(s(50), true, 5, 10, false));
		assertEquals(0, planner.attempts());
		assertTrue(planner.shouldAttempt(s(50), true, 5, 10, true));
	}

	@Test
	void resetEndsTheLossAndBeginStartsANewOne() {
		ReconnectPlanner planner = new ReconnectPlanner();
		planner.begin(s(0));
		assertTrue(planner.shouldAttempt(s(10), true, 5, 10, true));
		planner.reset();
		assertFalse(planner.isActive());
		assertFalse(planner.shouldAttempt(s(100), true, 5, 10, true));
		planner.begin(s(200));
		assertEquals(0, planner.attempts());
		assertTrue(planner.shouldAttempt(s(210), true, 5, 10, true));
	}
}
