package dev.afkmod.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Classifies incoming server text. Matching is a case-insensitive substring match on the text with
 * formatting codes removed. Order of precedence:
 * <ol>
 *   <li>ignore keywords (e.g. "whispered") win over everything</li>
 *   <li>restart-end keywords (checked before restart keywords, since an "all clear" message may also
 *       contain a restart keyword such as "servers")</li>
 *   <li>restart keywords</li>
 * </ol>
 */
public final class KeywordMatcher {
	public enum Result {
		NONE,
		IGNORED,
		RESTART,
		RESTART_END
	}

	/** Legacy section-sign formatting codes, including the {@code §x§r§r§g§g§b§b} hex form and stray signs. */
	private static final Pattern FORMATTING = Pattern.compile("§[0-9a-fk-orx]?", Pattern.CASE_INSENSITIVE);

	private KeywordMatcher() {
	}

	public static Result classify(String message, Collection<String> restartKeywords,
	                              Collection<String> ignoreKeywords, Collection<String> restartEndKeywords) {
		if (message == null) return Result.NONE;
		String text = normalize(message);
		if (containsAny(text, ignoreKeywords)) return Result.IGNORED;
		if (containsAny(text, restartEndKeywords)) return Result.RESTART_END;
		if (containsAny(text, restartKeywords)) return Result.RESTART;
		return Result.NONE;
	}

	/**
	 * The first keyword (as configured) that {@code message} contains, using the same rule as {@link #classify}
	 * (formatting stripped, case-insensitive, blank keywords skipped), or null if none does.
	 */
	public static String firstMatch(String message, Collection<String> keywords) {
		if (message == null || keywords == null) return null;
		String text = normalize(message);
		for (String keyword : keywords) {
			if (keyword == null || keyword.isBlank()) continue;
			if (text.contains(normalize(keyword.trim()))) return keyword;
		}
		return null;
	}

	public static String stripFormatting(String text) {
		return FORMATTING.matcher(text).replaceAll("");
	}

	/** Splits a comma-separated GUI field into trimmed, non-blank keywords. */
	public static List<String> parseList(String commaSeparated) {
		List<String> out = new ArrayList<>();
		if (commaSeparated == null) return out;
		for (String part : commaSeparated.split(",")) {
			String k = part.trim();
			if (!k.isEmpty()) out.add(k);
		}
		return out;
	}

	public static String joinList(Collection<String> keywords) {
		return String.join(", ", keywords);
	}

	private static String normalize(String s) {
		return stripFormatting(s).toLowerCase(Locale.ROOT);
	}

	private static boolean containsAny(String text, Collection<String> keywords) {
		if (keywords == null) return false;
		for (String keyword : keywords) {
			// A blank keyword would match every message, so skip it.
			if (keyword == null || keyword.isBlank()) continue;
			if (text.contains(normalize(keyword.trim()))) return true;
		}
		return false;
	}
}
