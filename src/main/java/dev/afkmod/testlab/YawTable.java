package dev.afkmod.testlab;

import dev.afkmod.logic.YawMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The yaw snap calculator (D1): the normalized yaw, the direction and the exact target yaw the recovery turn would end
 * on, plus a built-in table of edge values checked against independently written expected results. The calculation
 * uses the same {@link YawMath} calls as the recovery ({@code nearestCardinal}, then {@code unwrapTarget} in the
 * player's winding, cast to float like {@code YawRotation}).
 */
public final class YawTable {
	/** The calculation for one yaw. */
	public record Calculation(double yaw, double normalized, int cardinal, String direction, float target) {
		public String describe() {
			return String.format(Locale.ROOT, "yaw %s -> normalized %.2f, %s (%d), exact target %s (turn %+.2f)",
					num(yaw), normalized, direction, cardinal, num(target), target - yaw);
		}
	}

	/** One row of the edge-value table. */
	public record Row(Calculation calc, float expectedTarget, String expectedDirection) {
		public boolean pass() {
			return Float.compare(calc.target(), expectedTarget) == 0 && calc.direction().equals(expectedDirection);
		}

		public String describe() {
			return String.format(Locale.ROOT, "%s: %s target %s (expected %s %s) %s", num(calc.yaw()), calc.direction(),
					num(calc.target()), expectedDirection, num(expectedTarget), pass() ? "PASS" : "FAIL");
		}
	}

	/** {yaw, expected exact target}. South = 0, west = 90, north = 180 (-180), east = 270 (-90). */
	private static final double[][] EDGE_VALUES = {
			{0, 0},
			{44.99, 0},
			{45, 90},
			{90, 90},
			{134.99, 90},
			{135, 180},
			{179.9, 180},
			{180, 180},
			{-179.9, -180},
			{-90, -90},
			{270, 270},
			{359.9, 360},
			{-0.1, 0},
	};

	private YawTable() {
	}

	public static Calculation calculate(double yaw) {
		int cardinal = YawMath.nearestCardinal(yaw);
		float target = (float) YawMath.unwrapTarget(yaw, cardinal);
		return new Calculation(yaw, YawMath.normalize(yaw), cardinal, YawMath.directionName(cardinal), target);
	}

	/** Every edge value with its expected result. */
	public static List<Row> check() {
		List<Row> rows = new ArrayList<>();
		for (double[] v : EDGE_VALUES) {
			float expected = (float) v[1];
			rows.add(new Row(calculate(v[0]), expected, expectedDirection(expected)));
		}
		return rows;
	}

	/** Written independently of {@link YawMath}: the direction of an exact multiple of 90. */
	private static String expectedDirection(float target) {
		int quarter = Math.floorMod(Math.round(target / 90f), 4);
		return switch (quarter) {
			case 0 -> "south";
			case 1 -> "west";
			case 2 -> "north";
			default -> "east";
		};
	}

	static String num(double v) {
		return v == Math.rint(v) ? String.format(Locale.ROOT, "%.1f", v) : String.format(Locale.ROOT, "%.2f", v);
	}
}
