package dev.afkmod.logic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Switches controls set to "Hold" (Options > Controls) to "Toggle" while the mod is on, and puts exactly those back to
 * "Hold" when it turns off. Controls that were already "Toggle" are never touched.
 *
 * <p>The switched controls are also written to a small file, one name per line, so a crash or quit while the mod is on
 * can still be undone on the next start ({@link #restoreLeftovers}). The file is deleted once they are restored.
 */
public final class HoldModeOverride {
	/** Reads and writes the player's toggle/hold setting for a control, by name. */
	public interface Modes {
		boolean isToggle(String control);

		void setToggle(String control, boolean toggle);
	}

	private final Path store;
	private final Set<String> switched = new LinkedHashSet<>();

	/** @param store where the switched controls are remembered, or null to keep them only in memory */
	public HoldModeOverride(Path store) {
		this.store = store;
	}

	/** Switches every "Hold" control in {@code controls} to "Toggle". Returns the ones it switched now. */
	public List<String> apply(Modes modes, List<String> controls) {
		List<String> now = new ArrayList<>();
		for (String control : controls) {
			if (switched.contains(control) || modes.isToggle(control)) continue;
			modes.setToggle(control, true);
			switched.add(control);
			now.add(control);
		}
		if (!now.isEmpty()) write();
		return now;
	}

	/** Puts every control this switched back to "Hold". Returns the ones it restored. */
	public List<String> restore(Modes modes) {
		List<String> restored = new ArrayList<>(switched);
		for (String control : restored) modes.setToggle(control, false);
		switched.clear();
		if (!restored.isEmpty()) delete();
		return restored;
	}

	/** True while some control is switched to "Toggle" by this. */
	public boolean isApplied() {
		return !switched.isEmpty();
	}

	/**
	 * Start-up: if the last session ended while controls were switched (crash, quit), puts them back to "Hold" and
	 * deletes the file. Returns the ones it restored.
	 */
	public List<String> restoreLeftovers(Modes modes) {
		if (store == null || !Files.exists(store)) return List.of();
		List<String> leftovers = new ArrayList<>();
		try {
			for (String line : Files.readAllLines(store, StandardCharsets.UTF_8)) {
				String control = line.strip();
				if (!control.isEmpty() && !leftovers.contains(control)) leftovers.add(control);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		for (String control : leftovers) modes.setToggle(control, false);
		delete();
		return leftovers;
	}

	private void write() {
		if (store == null) return;
		try {
			Files.createDirectories(store.toAbsolutePath().getParent());
			Files.write(store, switched, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void delete() {
		if (store == null) return;
		try {
			Files.deleteIfExists(store);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
