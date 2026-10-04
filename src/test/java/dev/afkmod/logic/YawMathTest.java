package dev.afkmod.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YawMathTest {
	@Test
	void normalizesToZeroUpTo360() {
		assertEquals(0.0, YawMath.normalize(0));
		assertEquals(0.0, YawMath.normalize(360));
		assertEquals(270.0, YawMath.normalize(-90));
		assertEquals(5.0, YawMath.normalize(725));
		assertEquals(180.0, YawMath.normalize(-180));
		double tiny = YawMath.normalize(-1e-13);
		assertTrue(tiny >= 0 && tiny < 360, "always in [0, 360): " + tiny);
	}

	@Test
	void nearestCardinalFollowsMinecraftConvention() {
		// south = 0, west = 90, north = 180, east = 270 (Direction.toYRot in 26.1.2)
		assertEquals(0, YawMath.nearestCardinal(0));
		assertEquals(90, YawMath.nearestCardinal(90));
		assertEquals(180, YawMath.nearestCardinal(180));
		assertEquals(180, YawMath.nearestCardinal(-180));
		assertEquals(270, YawMath.nearestCardinal(-90));
		assertEquals(270, YawMath.nearestCardinal(270));
	}

	@Test
	void nearestCardinalEdgeValues() {
		assertEquals(180, YawMath.nearestCardinal(179.9));
		assertEquals(180, YawMath.nearestCardinal(-179.9));
		assertEquals(0, YawMath.nearestCardinal(44.99));
		assertEquals(0, YawMath.nearestCardinal(359));
		assertEquals(0, YawMath.nearestCardinal(-1));
		assertEquals(90, YawMath.nearestCardinal(89.5));
		assertEquals(180, YawMath.nearestCardinal(720 + 181));
		assertEquals(270, YawMath.nearestCardinal(-100));
	}

	@Test
	void exactly45DegreeBoundariesRoundUp() {
		assertEquals(90, YawMath.nearestCardinal(45));
		assertEquals(180, YawMath.nearestCardinal(135));
		assertEquals(270, YawMath.nearestCardinal(225));
		assertEquals(0, YawMath.nearestCardinal(315));
		// Negative yaws are normalized first: -45 = 315 -> 0, -135 = 225 -> 270.
		assertEquals(0, YawMath.nearestCardinal(-45));
		assertEquals(270, YawMath.nearestCardinal(-135));
	}

	@Test
	void mostlyNorthIsNorth() {
		assertEquals("north", YawMath.directionName(160));
		assertEquals("north", YawMath.directionName(-170));
		assertEquals("south", YawMath.directionName(10));
		assertEquals("west", YawMath.directionName(100));
		assertEquals("east", YawMath.directionName(-80));
	}

	@Test
	void shortestDeltaHandlesWraparound() {
		assertEquals(10.0, YawMath.shortestDelta(170, -180), 1e-9);
		assertEquals(10.0, YawMath.shortestDelta(350, 0), 1e-9);
		assertEquals(-20.0, YawMath.shortestDelta(10, 350), 1e-9);
		assertEquals(-0.1, YawMath.shortestDelta(-179.9, 180), 1e-9);
		assertEquals(180.0, YawMath.shortestDelta(0, 180), 1e-9);
		assertEquals(-5.0, YawMath.shortestDelta(725, 0), 1e-9);
	}

	@Test
	void unwrapTargetKeepsTheWindingAndIsExact() {
		assertEquals(180.0, YawMath.unwrapTarget(170, -180));
		assertEquals(360.0, YawMath.unwrapTarget(350, 0));
		assertEquals(-180.0, YawMath.unwrapTarget(-179.9, 180));
		assertEquals(720.0, YawMath.unwrapTarget(725, 0));
		assertEquals(-90.0, YawMath.unwrapTarget(-100, 270));
		for (double from = -1000; from <= 1000; from += 7.3) {
			for (int target : new int[] {0, 90, 180, 270}) {
				double end = YawMath.unwrapTarget(from, target);
				assertEquals(0.0, YawMath.normalize(end - target), 1e-9, "same direction");
				assertEquals(YawMath.shortestDelta(from, target), end - from, 1e-9, "short way from " + from);
				assertEquals(0.0, Math.abs(end % 90.0), "exact multiple of 90");
			}
		}
	}
}
