package dev.afkmod.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StuckDetectorTest {
	private static final double WINDOW = 20;
	private static final double DISTANCE = 3;
	private static final long SECOND = 1_000_000_000L;
	private static final long T0 = 555_000_000_000L;

	private final StuckDetector d = new StuckDetector();

	private void sample(int second, double x, double z) {
		d.addSample(T0 + second * SECOND, x, z, WINDOW);
	}

	@Test
	void notStuckUntilTheWindowIsFull() {
		for (int s = 0; s < 20; s++) {
			sample(s, 0, 0);
			assertFalse(d.evaluate(WINDOW, DISTANCE).stuck(), "only " + s + " s of samples");
		}
		sample(20, 0, 0);
		StuckDetector.Evaluation e = d.evaluate(WINDOW, DISTANCE);
		assertTrue(e.windowFull());
		assertTrue(e.stuck());
		assertEquals(0.0, e.maxDistance());
	}

	@Test
	void movingThreeBlocksOrMoreIsNotStuck() {
		for (int s = 0; s <= 20; s++) sample(s, s * 0.2, 0); // 4 blocks over the window
		StuckDetector.Evaluation e = d.evaluate(WINDOW, DISTANCE);
		assertTrue(e.windowFull());
		assertFalse(e.stuck());
		assertEquals(4.0, e.maxDistance(), 1e-9);
	}

	@Test
	void exactlyTheDistanceIsNotStuck() {
		sample(0, 0, 0);
		sample(20, 3, 0);
		assertFalse(d.evaluate(WINDOW, DISTANCE).stuck(), "stuck needs strictly less than 3");
	}

	@Test
	void distanceIsHorizontalXzOnly() {
		sample(0, 0, 0);
		sample(20, 1.8, 2.4); // hypot = 3.0
		assertEquals(3.0, d.maxDistanceFromCurrent(), 1e-9);
		d.clear();
		sample(0, 0, 0);
		sample(20, 1.2, 1.6); // hypot = 2.0
		assertTrue(d.evaluate(WINDOW, DISTANCE).stuck());
	}

	@Test
	void anySampleInTheWindowCountsNotOnlyTheOldest() {
		// Went 5 blocks out and came back: the far sample keeps it "moving".
		sample(0, 0, 0);
		sample(10, 5, 0);
		sample(20, 0.5, 0);
		StuckDetector.Evaluation e = d.evaluate(WINDOW, DISTANCE);
		assertEquals(4.5, e.maxDistance(), 1e-9);
		assertFalse(e.stuck());
	}

	@Test
	void windowSlidesSoOldMovementIsForgotten() {
		sample(0, 0, 0);
		sample(1, 10, 0); // the last move, between 0 s and 1 s
		for (int s = 2; s <= 20; s++) {
			sample(s, 10, 0);
			assertFalse(d.evaluate(WINDOW, DISTANCE).stuck(), "the 0 s sample is still in the window at " + s);
		}
		sample(21, 10, 0);
		assertTrue(d.evaluate(WINDOW, DISTANCE).stuck(), "20 s without moving (1 s to 21 s)");
		assertEquals(21, d.size(), "the 0 s sample was dropped");
	}

	@Test
	void sampleCountStaysBounded() {
		for (int s = 0; s < 1000; s++) sample(s, 0, 0);
		assertEquals(21, d.size(), "20 s at one sample per second plus the edge sample");
	}

	@Test
	void clearEmptiesTheWindow() {
		for (int s = 0; s <= 20; s++) sample(s, 0, 0);
		d.clear();
		assertEquals(0, d.size());
		sample(21, 0, 0);
		assertFalse(d.evaluate(WINDOW, DISTANCE).stuck());
		assertFalse(d.isWindowFull(WINDOW));
	}

	@Test
	void forceStuckReportsStuckOnce() {
		sample(0, 0, 0);
		d.forceStuck();
		StuckDetector.Evaluation forced = d.evaluate(WINDOW, DISTANCE);
		assertTrue(forced.stuck());
		assertTrue(forced.forced());
		assertTrue(forced.windowFull(), "a forced result is acted on like a full window");
		assertFalse(d.evaluate(WINDOW, DISTANCE).stuck());
	}

	@Test
	void irregularSampleTimingIsHandled() {
		// A lagging server: checks every 1 to 3 s of wall-clock time.
		d.addSample(T0, 0, 0, WINDOW);
		d.addSample(T0 + 3 * SECOND, 0, 0, WINDOW);
		d.addSample(T0 + 19_500_000_000L, 0, 0, WINDOW);
		assertFalse(d.isWindowFull(WINDOW));
		d.addSample(T0 + 20 * SECOND, 0, 0, WINDOW);
		assertTrue(d.isWindowFull(WINDOW));
	}
}
