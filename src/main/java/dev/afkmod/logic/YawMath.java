package dev.afkmod.logic;

/**
 * Yaw helpers for the movement recovery turn. Minecraft's convention (verified in the 26.1.2 sources,
 * {@code Direction.toYRot()} and {@code Direction.fromYRot()}): south = 0, west = 90, north = 180 (or -180),
 * east = 270 (or -90). The player's yaw is not wrapped by the game, so it can be any value (e.g. 725 after
 * spinning around twice).
 */
public final class YawMath {
	private YawMath() {
	}

	/** {@code yaw} mapped to [0, 360). */
	public static double normalize(double yaw) {
		double n = yaw % 360.0;
		if (n < 0) n += 360.0;
		// A tiny negative value plus 360 can round to exactly 360.0.
		return n >= 360.0 ? 0.0 : n;
	}

	/**
	 * The nearest cardinal yaw in {0, 90, 180, 270}. A yaw exactly on a 45-degree boundary rounds up
	 * (45 -> 90, 135 -> 180, 225 -> 270, 315 -> 0), like {@code Direction.fromYRot}.
	 */
	public static int nearestCardinal(double yaw) {
		int quarter = (int) Math.floor(normalize(yaw) / 90.0 + 0.5);
		return (quarter & 3) * 90;
	}

	/** "south", "west", "north" or "east" for a yaw (rounded to the nearest cardinal). */
	public static String directionName(double yaw) {
		return switch (nearestCardinal(yaw)) {
			case 0 -> "south";
			case 90 -> "west";
			case 180 -> "north";
			default -> "east";
		};
	}

	/** The signed short-way turn from {@code from} to {@code to}, in (-180, 180]. */
	public static double shortestDelta(double from, double to) {
		double d = normalize(to - from);
		return d > 180.0 ? d - 360.0 : d;
	}

	/**
	 * {@code target} expressed in the same winding as {@code from}: the value {@code target + 360k} reached by
	 * turning the short way from {@code from}. Exact for whole-degree targets (no accumulated float maths), so a
	 * cardinal target stays an exact multiple of 90. Agrees with {@link #shortestDelta}, including +180 for an
	 * exact half turn.
	 */
	public static double unwrapTarget(double from, double target) {
		double end = target + 360.0 * Math.round((from - target) / 360.0);
		if (end - from > 180.0) end -= 360.0;
		else if (end - from <= -180.0) end += 360.0;
		return end;
	}
}
