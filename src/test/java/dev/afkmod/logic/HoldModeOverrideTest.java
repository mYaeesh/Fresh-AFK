package dev.afkmod.logic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldModeOverrideTest {
	private static final List<String> CONTROLS = List.of("CROUCH", "ATTACK", "USE");

	@TempDir
	Path dir;

	/** The player's settings: true = Toggle. */
	private static final class FakeModes implements HoldModeOverride.Modes {
		final Map<String, Boolean> toggle = new HashMap<>();

		FakeModes(boolean crouch, boolean attack, boolean use) {
			toggle.put("CROUCH", crouch);
			toggle.put("ATTACK", attack);
			toggle.put("USE", use);
		}

		@Override
		public boolean isToggle(String control) {
			return toggle.get(control);
		}

		@Override
		public void setToggle(String control, boolean value) {
			toggle.put(control, value);
		}
	}

	@Test
	void switchesOnlyHoldControlsAndPutsThemBack() {
		FakeModes modes = new FakeModes(true, false, false);
		HoldModeOverride override = new HoldModeOverride(dir.resolve("holdmodes.txt"));

		assertEquals(List.of("ATTACK", "USE"), override.apply(modes, CONTROLS));
		assertTrue(modes.toggle.values().stream().allMatch(b -> b), "all three are Toggle while the mod is on");
		assertTrue(override.isApplied());

		assertEquals(List.of("ATTACK", "USE"), override.restore(modes));
		assertEquals(Map.of("CROUCH", true, "ATTACK", false, "USE", false), modes.toggle);
		assertFalse(override.isApplied());
	}

	@Test
	void allToggleAlreadyChangesNothingAndWritesNoFile() {
		FakeModes modes = new FakeModes(true, true, true);
		Path store = dir.resolve("holdmodes.txt");
		HoldModeOverride override = new HoldModeOverride(store);

		assertEquals(List.of(), override.apply(modes, CONTROLS));
		assertFalse(Files.exists(store));
		assertEquals(List.of(), override.restore(modes));
		assertEquals(Map.of("CROUCH", true, "ATTACK", true, "USE", true), modes.toggle);
	}

	@Test
	void applyingTwiceDoesNotForgetTheOriginalHoldSetting() {
		FakeModes modes = new FakeModes(false, true, true);
		HoldModeOverride override = new HoldModeOverride(null);
		override.apply(modes, CONTROLS);
		// Second turn-on without a turn-off: crouch now reads Toggle, but it must still go back to Hold.
		assertEquals(List.of(), override.apply(modes, CONTROLS));
		override.restore(modes);
		assertFalse(modes.toggle.get("CROUCH"));
	}

	@Test
	void fileRemembersSwitchedControlsUntilRestored() throws IOException {
		Path store = dir.resolve("holdmodes.txt");
		FakeModes modes = new FakeModes(false, true, false);
		HoldModeOverride override = new HoldModeOverride(store);

		override.apply(modes, CONTROLS);
		assertEquals(List.of("CROUCH", "USE"), Files.readAllLines(store));
		override.restore(modes);
		assertFalse(Files.exists(store));
	}

	@Test
	void leftoversFromACrashArePutBackOnStart() throws IOException {
		Path store = dir.resolve("holdmodes.txt");
		FakeModes before = new FakeModes(false, false, true);
		new HoldModeOverride(store).apply(before, CONTROLS); // the game "crashes" here, never restoring

		// Next start: options.txt may have been saved with Toggle in between.
		FakeModes after = new FakeModes(true, true, true);
		assertEquals(List.of("CROUCH", "ATTACK"), new HoldModeOverride(store).restoreLeftovers(after));
		assertEquals(Map.of("CROUCH", false, "ATTACK", false, "USE", true), after.toggle);
		assertFalse(Files.exists(store));
	}

	@Test
	void noLeftoverFileRestoresNothing() {
		FakeModes modes = new FakeModes(true, true, true);
		assertEquals(List.of(), new HoldModeOverride(dir.resolve("missing.txt")).restoreLeftovers(modes));
		assertEquals(List.of(), new HoldModeOverride(null).restoreLeftovers(modes));
		assertTrue(modes.toggle.values().stream().allMatch(b -> b));
	}
}
