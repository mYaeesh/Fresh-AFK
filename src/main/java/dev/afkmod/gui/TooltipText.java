package dev.afkmod.gui;

import dev.afkmod.config.SettingInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * The content of one info tooltip: body lines (the description, wrapped at about 40 characters) followed by footer
 * lines (the default/range line, or a scenario's PASS meaning). Built here from the registries so every tooltip in the
 * GUI comes from one place. No Minecraft imports.
 */
public record TooltipText(List<String> body, List<String> footer) {

	public TooltipText {
		body = List.copyOf(body);
		footer = List.copyOf(footer);
	}

	/** Description first, then the final {@code Default: ... - Range: ...} line. */
	public static TooltipText forSetting(String key) {
		SettingInfo.Entry entry = SettingInfo.get(key);
		return new TooltipText(TextWrap.wrap(entry.description()), TextWrap.wrap(entry.defaultLine()));
	}

	public static TooltipText of(String body, String footer) {
		return new TooltipText(TextWrap.wrap(body), footer == null || footer.isBlank() ? List.of() : TextWrap.wrap(footer));
	}

	public static TooltipText plain(String body) {
		return new TooltipText(TextWrap.wrap(body), List.of());
	}

	/** Every line, body then footer. */
	public List<String> allLines() {
		List<String> all = new ArrayList<>(body);
		all.addAll(footer);
		return all;
	}
}
