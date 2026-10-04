package dev.afkmod.logic;

/**
 * Decides whether walking forward during a recovery is safe. Crouch is off during the walk, so a drop or a
 * harmful block ahead must stop it. Pure logic: the Minecraft layer classifies each block through a
 * {@link Probe}.
 *
 * <p>For each of the 2 blocks ahead at the player's level, the column is safe when nothing harmful is at feet or
 * head height and there is solid ground within 1 to 2 blocks below (a solid block at feet height, i.e. a wall
 * or step, also counts as ground). Harmful ground (magma, lava below) is unsafe.
 */
public final class EdgeCheck {
	public static final int BLOCKS_AHEAD = 2;

	public enum Cell {
		/** Air, water, grass, flowers...: nothing to stand on, nothing harmful. */
		PASSABLE,
		/** Has a collision shape: something to stand on. */
		SOLID,
		/** Lava, fire, cactus, magma and other blocks that hurt. */
		HAZARD
	}

	/** The block {@code forward} blocks ahead (1 or 2) and {@code dy} blocks up from the player's feet (-2..1). */
	@FunctionalInterface
	public interface Probe {
		Cell at(int forward, int dy);
	}

	/** Why a path is unsafe, or {@link #SAFE}. */
	public enum Verdict {
		SAFE,
		HAZARD_ON_PATH,
		HAZARD_BELOW,
		NO_GROUND
	}

	private EdgeCheck() {
	}

	public static Verdict check(Probe probe) {
		for (int forward = 1; forward <= BLOCKS_AHEAD; forward++) {
			Verdict v = checkColumn(probe, forward);
			if (v != Verdict.SAFE) return v;
		}
		return Verdict.SAFE;
	}

	private static Verdict checkColumn(Probe probe, int forward) {
		if (probe.at(forward, 1) == Cell.HAZARD || probe.at(forward, 0) == Cell.HAZARD) return Verdict.HAZARD_ON_PATH;
		if (probe.at(forward, 0) == Cell.SOLID) return Verdict.SAFE;
		Cell below = probe.at(forward, -1);
		if (below == Cell.SOLID) return Verdict.SAFE;
		if (below == Cell.HAZARD) return Verdict.HAZARD_BELOW;
		Cell twoBelow = probe.at(forward, -2);
		if (twoBelow == Cell.SOLID) return Verdict.SAFE;
		return twoBelow == Cell.HAZARD ? Verdict.HAZARD_BELOW : Verdict.NO_GROUND;
	}
}
