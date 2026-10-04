package dev.afkmod.logic;

import dev.afkmod.logic.EdgeCheck.Cell;
import dev.afkmod.logic.EdgeCheck.Verdict;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EdgeCheckTest {
	/** A small world ahead of the player: everything is passable (air) unless set. */
	private final Map<String, Cell> cells = new HashMap<>();

	private void flatFloor() {
		set(1, -1, Cell.SOLID);
		set(2, -1, Cell.SOLID);
	}

	private void set(int forward, int dy, Cell cell) {
		cells.put(forward + ":" + dy, cell);
	}

	private Verdict check() {
		return EdgeCheck.check((forward, dy) -> cells.getOrDefault(forward + ":" + dy, Cell.PASSABLE));
	}

	@Test
	void flatFloorIsSafe() {
		flatFloor();
		assertEquals(Verdict.SAFE, check());
	}

	@Test
	void oneBlockDropIsSafe() {
		set(1, -1, Cell.SOLID);
		set(2, -2, Cell.SOLID);
		assertEquals(Verdict.SAFE, check());
	}

	@Test
	void deeperDropIsUnsafe() {
		set(1, -1, Cell.SOLID);
		assertEquals(Verdict.NO_GROUND, check(), "nothing within 2 below the second block");
		cells.clear();
		set(2, -1, Cell.SOLID);
		assertEquals(Verdict.NO_GROUND, check(), "nothing within 2 below the first block");
	}

	@Test
	void hazardsOnThePathAreUnsafe() {
		flatFloor();
		set(1, 0, Cell.HAZARD); // lava / fire / cactus at feet
		assertEquals(Verdict.HAZARD_ON_PATH, check());
		cells.clear();
		flatFloor();
		set(2, 1, Cell.HAZARD); // at head height
		assertEquals(Verdict.HAZARD_ON_PATH, check());
	}

	@Test
	void hazardousGroundIsUnsafe() {
		flatFloor();
		set(2, -1, Cell.HAZARD); // magma
		assertEquals(Verdict.HAZARD_BELOW, check());
		cells.clear();
		set(1, -1, Cell.SOLID);
		set(2, -2, Cell.HAZARD); // lava one block down
		assertEquals(Verdict.HAZARD_BELOW, check());
	}

	@Test
	void aWallAheadIsNotADrop() {
		set(1, 0, Cell.SOLID);
		set(2, 0, Cell.SOLID);
		assertEquals(Verdict.SAFE, check());
	}
}
