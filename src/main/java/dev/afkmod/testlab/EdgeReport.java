package dev.afkmod.testlab;

import dev.afkmod.logic.EdgeCheck;

import java.util.Locale;
import java.util.Objects;

/**
 * What the recovery's edge check sees in the 2 blocks ahead (D2), without moving. The verdict comes from the real
 * {@link EdgeCheck#check} over the cells the game reported.
 *
 * @param cells {@code cells[forward - 1][1 - dy]} for forward 1..2 and dy +1 (head), 0 (feet), -1, -2 (below)
 * @param names the block names for the same cells
 */
public record EdgeReport(int cardinalYaw, String direction, EdgeCheck.Cell[][] cells, String[][] names,
		EdgeCheck.Verdict verdict) {
	public static final int[] DYS = {1, 0, -1, -2};

	public EdgeReport {
		Objects.requireNonNull(cells);
		Objects.requireNonNull(names);
	}

	/** Builds the report and evaluates it with the real edge check. */
	public static EdgeReport of(int cardinalYaw, String direction, EdgeCheck.Cell[][] cells, String[][] names) {
		EdgeCheck.Verdict verdict = EdgeCheck.check((forward, dy) -> cells[forward - 1][1 - dy]);
		return new EdgeReport(cardinalYaw, direction, cells, names, verdict);
	}

	public String decision() {
		return verdict == EdgeCheck.Verdict.SAFE ? "would walk"
				: "would NOT walk (" + verdict + "), counted as a failed attempt";
	}

	public String describe() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format(Locale.ROOT, "Facing %s (%d). Edge check: %s, %s.", direction, cardinalYaw, verdict, decision()));
		for (int forward = 1; forward <= EdgeCheck.BLOCKS_AHEAD; forward++) {
			sb.append("\nBlock ").append(forward).append(" ahead: ");
			for (int i = 0; i < DYS.length; i++) {
				if (i > 0) sb.append(", ");
				sb.append(label(DYS[i])).append(' ').append(names[forward - 1][i]).append(" (")
						.append(cells[forward - 1][i]).append(')');
			}
		}
		return sb.toString();
	}

	private static String label(int dy) {
		return switch (dy) {
			case 1 -> "head";
			case 0 -> "feet";
			case -1 -> "below";
			default -> "2 below";
		};
	}
}
