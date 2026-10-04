package dev.afkmod.gui;

import java.util.ArrayList;
import java.util.List;

/** Word wrapping by character count (used for tooltips, which wrap at about 40 characters). No Minecraft imports. */
public final class TextWrap {
	/** Tooltip lines are at most this many characters wide. */
	public static final int TOOLTIP_CHARS = 40;

	private TextWrap() {
	}

	/**
	 * Greedy word wrap. Explicit {@code \n} starts a new line; a word longer than {@code maxChars} is split.
	 * Never returns lines longer than {@code maxChars}; an empty text gives an empty list.
	 */
	public static List<String> wrap(String text, int maxChars) {
		if (maxChars < 1) throw new IllegalArgumentException("maxChars must be at least 1");
		List<String> lines = new ArrayList<>();
		if (text == null) return lines;
		for (String paragraph : text.split("\n", -1)) {
			StringBuilder line = new StringBuilder();
			for (String word : paragraph.trim().split("\\s+")) {
				if (word.isEmpty()) continue;
				while (word.length() > maxChars) {
					if (line.length() > 0) {
						lines.add(line.toString());
						line.setLength(0);
					}
					lines.add(word.substring(0, maxChars));
					word = word.substring(maxChars);
				}
				if (line.length() == 0) {
					line.append(word);
				} else if (line.length() + 1 + word.length() <= maxChars) {
					line.append(' ').append(word);
				} else {
					lines.add(line.toString());
					line.setLength(0);
					line.append(word);
				}
			}
			if (line.length() > 0) lines.add(line.toString());
		}
		return lines;
	}

	public static List<String> wrap(String text) {
		return wrap(text, TOOLTIP_CHARS);
	}
}
