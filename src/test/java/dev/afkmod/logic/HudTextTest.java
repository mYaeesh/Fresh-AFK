package dev.afkmod.logic;

import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.logic.HudText.Segment;
import dev.afkmod.logic.HudText.Snapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudTextTest {
	private static Snapshot snap(State state, boolean hasTimer, boolean paused, long remaining, int restarts,
			boolean c, boolean l, boolean r) {
		return new Snapshot(state, hasTimer, paused, remaining, restarts, c, l, r, false);
	}

	private static String plain(List<Segment> line) {
		StringBuilder sb = new StringBuilder();
		for (Segment s : line) sb.append(s.text());
		return sb.toString();
	}

	@Test
	void compactMiningIsOneLine() {
		var lines = HudText.lines(snap(State.ACTIVE, true, false, 5025, 2, true, true, true), false);
		assertEquals(1, lines.size());
		assertEquals("● AFK Mining | 1:23:45 | C L R | ↻2", plain(lines.get(0)));
		assertEquals(HudText.GREEN, lines.get(0).get(0).color());
	}

	@Test
	void noTimerShowsInfinity() {
		var line = HudText.lines(snap(State.ACTIVE, false, false, 0, 0, true, true, true), false).get(0);
		assertEquals("● AFK Mining | ∞ | C L R | ↻0", plain(line));
	}

	@Test
	void keysAreGreenWhenActiveAndRedWhenNot() {
		var line = HudText.lines(snap(State.ACTIVE, false, false, 0, 0, true, false, true), false).get(0);
		int c = -1, l = -1, r = -1;
		for (Segment s : line) {
			if (s.text().equals("C")) c = s.color();
			if (s.text().equals("L")) l = s.color();
			if (s.text().equals("R")) r = s.color();
		}
		assertEquals(HudText.GREEN, c);
		assertEquals(HudText.RED, l);
		assertEquals(HudText.GREEN, r);
	}

	@Test
	void restartingLineIsRedAndShowsNoNumbers() {
		var lines = HudText.lines(snap(State.RESTARTING, true, true, 100, 3, true, false, false), false);
		assertEquals(1, lines.size());
		assertEquals("● Server restarting", plain(lines.get(0)));
		for (Segment s : lines.get(0)) assertEquals(HudText.RED, s.color());
	}

	@Test
	void waitingStatesAreGreyAndTimerShowsPaused() {
		var line = HudText.lines(snap(State.SETTLING, true, true, 90, 1, true, false, false), false).get(0);
		assertEquals(HudText.GREY, line.get(0).color());
		assertEquals("● Resuming | 1:30 (paused) | C L R | ↻1", plain(line));
	}

	@Test
	void detailedModeIsThreeShortLines() {
		var lines = HudText.lines(snap(State.ACTIVE, true, false, 60, 1, true, true, true), true);
		assertEquals(3, lines.size());
		assertEquals("● AFK Mining | 1:00", plain(lines.get(0)));
		assertEquals("Keys: C L R", plain(lines.get(1)));
		assertEquals("↻ 1 restart", plain(lines.get(2)));
	}

	@Test
	void detailedRestartingHasNoTimer() {
		var lines = HudText.lines(snap(State.RESTARTING, true, true, 60, 2, true, false, false), true);
		assertEquals("● Server restarting", plain(lines.get(0)));
		assertEquals("↻ 2 restarts", plain(lines.get(2)));
	}

	@Test
	void recoveringIsYellow() {
		var line = HudText.lines(snap(State.RECOVERING, false, false, 0, 0, false, false, false), false).get(0);
		assertEquals(HudText.YELLOW, line.get(0).color());
		assertEquals("● Recovering | ∞ | C L R | ↻0", plain(line));
	}
}
