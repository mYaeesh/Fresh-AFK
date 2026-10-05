package dev.afkmod.config;

import dev.afkmod.logic.DurationParser;

import java.util.ArrayList;
import java.util.List;

/**
 * A named timer length the user can apply with one click. Stored in {@code config/afkmod.json}; field names are the
 * JSON keys, so do not rename them.
 */
public final class TimerPreset {
	public static final int NAME_MAX = 24;

	public String name = "";
	public long seconds;

	public TimerPreset() {
	}

	public TimerPreset(String name, long seconds) {
		this.name = name;
		this.seconds = seconds;
	}

	public TimerPreset copy() {
		return new TimerPreset(name, seconds);
	}

	/** The presets a fresh config starts with. */
	public static List<TimerPreset> defaults() {
		List<TimerPreset> list = new ArrayList<>();
		// Short names: the registry shows them as the default, and that line must fit in a tooltip.
		list.add(new TimerPreset("30 min", 30 * 60));
		list.add(new TimerPreset("1 hr", 3600));
		list.add(new TimerPreset("4 hr", 4 * 3600));
		list.add(new TimerPreset("8 hr", 8 * 3600));
		return list;
	}

	/**
	 * Cleans a loaded list: a missing list (null) becomes the defaults, entries without a name or with a length outside
	 * 1 s to {@link DurationParser#MAX_SECONDS} are dropped, names are trimmed and cut to {@link #NAME_MAX}. An explicit
	 * empty list is kept (the user deleted them all).
	 */
	public static List<TimerPreset> clean(List<TimerPreset> presets) {
		if (presets == null) return defaults();
		List<TimerPreset> out = new ArrayList<>();
		for (TimerPreset p : presets) {
			if (p == null || p.name == null || p.name.isBlank()) continue;
			if (p.seconds < 1 || p.seconds > DurationParser.MAX_SECONDS) continue;
			String name = p.name.trim();
			out.add(new TimerPreset(name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name, p.seconds));
		}
		return out;
	}

	public static List<TimerPreset> copyOf(List<TimerPreset> presets) {
		List<TimerPreset> out = new ArrayList<>();
		for (TimerPreset p : presets) out.add(p.copy());
		return out;
	}

	/** The name; the settings registry joins these to show the default list. */
	@Override
	public String toString() {
		return name;
	}
}
