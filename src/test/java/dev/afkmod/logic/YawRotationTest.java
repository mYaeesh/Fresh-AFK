package dev.afkmod.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YawRotationTest {
	private static final long T0 = 987_654_321_000L;
	private static final long HALF_SECOND = 500_000_000L;

	@Test
	void easeStartsAndEndsOnTheEndpoints() {
		assertEquals(0.0, YawRotation.ease(0.0));
		assertEquals(1.0, YawRotation.ease(1.0));
		assertEquals(0.5, YawRotation.ease(0.5), 1e-12);
		assertEquals(0.0, YawRotation.ease(-0.3));
		assertEquals(1.0, YawRotation.ease(1.7));
	}

	@Test
	void speedIsZeroAtBothEnds() {
		double h = 1e-4;
		double startSpeed = (YawRotation.ease(h) - YawRotation.ease(0)) / h;
		double endSpeed = (YawRotation.ease(1) - YawRotation.ease(1 - h)) / h;
		assertTrue(startSpeed < 1e-6, "accelerates from rest: " + startSpeed);
		assertTrue(endSpeed < 1e-6, "comes to rest: " + endSpeed);
	}

	@Test
	void speedHasASinglePeakInTheMiddle() {
		// Odd, so one step is centred on t = 0.5 (with an even count the two middle steps tie).
		int n = 1001;
		double[] speed = new double[n];
		for (int i = 0; i < n; i++) speed[i] = YawRotation.ease((i + 1.0) / n) - YawRotation.ease((double) i / n);
		int peak = 0;
		for (int i = 1; i < n; i++) if (speed[i] > speed[peak]) peak = i;
		assertTrue(Math.abs(peak - n / 2) <= 1, "peak at the middle, got step " + peak);
		for (int i = 1; i <= peak; i++) assertTrue(speed[i] > speed[i - 1], "accelerating at step " + i);
		for (int i = peak + 1; i < n; i++) assertTrue(speed[i] < speed[i - 1], "decelerating at step " + i);
	}

	@Test
	void progressIsMonotonicAndNeverOvershoots() {
		YawRotation r = new YawRotation(137.3f, 180, T0, 0.5);
		float prev = r.yawAt(T0);
		assertEquals(137.3f, prev);
		for (long t = T0; t <= T0 + HALF_SECOND + 10_000_000L; t += 1_000_000L) {
			float yaw = r.yawAt(t);
			assertTrue(yaw >= prev, "monotonic at " + (t - T0));
			assertTrue(yaw >= 137.3f && yaw <= 180.0f, "within start..target: " + yaw);
			prev = yaw;
		}
	}

	@Test
	void reachesTheTargetExactlyAtTheEnd() {
		YawRotation r = new YawRotation(-170.37f, YawMath.nearestCardinal(-170.37f), T0, 0.5);
		assertEquals(-180.0f, r.endYaw());
		assertFalse(r.isFinished(T0 + HALF_SECOND - 1));
		assertTrue(r.isFinished(T0 + HALF_SECOND));
		assertEquals(-180.0f, r.yawAt(T0 + HALF_SECOND));
		assertEquals(-180.0f, r.yawAt(T0 + 10 * HALF_SECOND));
	}

	@Test
	void totalDurationEqualsTheConfiguredSeconds() {
		assertEquals(HALF_SECOND, new YawRotation(0, 90, T0, 0.5).durationNanos());
		assertEquals(1_250_000_000L, new YawRotation(0, 90, T0, 1.25).durationNanos());
		YawRotation r = new YawRotation(10, 90, T0, 0.5);
		assertEquals(0.5, r.timeFraction(T0 + HALF_SECOND / 2), 1e-12);
		assertEquals(r.startYaw() + r.arcDegrees() / 2, r.yawAt(T0 + HALF_SECOND / 2), 1e-4, "halfway in time = halfway in angle");
	}

	@Test
	void shortestArcWithWraparound() {
		YawRotation a = new YawRotation(170, -180, T0, 0.5);
		assertEquals(10.0, a.arcDegrees(), 1e-9);
		assertEquals(180.0f, a.endYaw(), "north, in the same winding as 170");
		assertTrue(a.yawAt(T0 + HALF_SECOND / 2) > 170 && a.yawAt(T0 + HALF_SECOND / 2) < 180, "turns up through 175");

		YawRotation b = new YawRotation(350, 0, T0, 0.5);
		assertEquals(10.0, b.arcDegrees(), 1e-9);
		assertEquals(360.0f, b.endYaw(), "south, without spinning back through 180");
		assertTrue(b.yawAt(T0 + HALF_SECOND / 2) > 350);

		YawRotation c = new YawRotation(-179.9f, 180, T0, 0.5);
		assertEquals(-180.0f, c.endYaw());
		assertTrue(Math.abs(c.arcDegrees()) < 0.2);

		YawRotation d = new YawRotation(10, 350, T0, 0.5);
		assertEquals(-20.0, d.arcDegrees(), 1e-9);
	}

	@Test
	void neverTurnsMoreThan180() {
		for (float from = -720; from <= 720; from += 3.7f) {
			YawRotation r = new YawRotation(from, YawMath.nearestCardinal(from), T0, 0.5);
			assertTrue(Math.abs(r.arcDegrees()) <= 45.0 + 1e-3, "nearest cardinal is at most 45 away from " + from);
			assertEquals(0.0f, Math.abs(r.endYaw() % 90.0f), "ends exactly on a cardinal");
		}
		assertEquals(180.0, new YawRotation(0, 180, T0, 0.5).arcDegrees(), 1e-9);
	}

	@Test
	void zeroDurationIsAnInstantSnap() {
		YawRotation r = new YawRotation(100.5f, 90, T0, 0);
		assertEquals(0, r.durationNanos());
		assertTrue(r.isFinished(T0));
		assertEquals(90.0f, r.yawAt(T0));
	}

	@Test
	void sameAngleAtTheSameTimeWhateverTheUpdateRate() {
		YawRotation r = new YawRotation(20, 0, T0, 0.5);
		long t = T0 + 123_000_000L;
		// The angle depends only on wall-clock time, not on how many updates happened before.
		for (long s = T0; s < t; s += 50_000_000L) r.yawAt(s);
		float afterTicks = r.yawAt(t);
		for (long s = T0; s < t; s += 4_000_000L) r.yawAt(s);
		assertEquals(afterTicks, r.yawAt(t));
		assertEquals(new YawRotation(20, 0, T0, 0.5).yawAt(t), afterTicks);
	}
}
