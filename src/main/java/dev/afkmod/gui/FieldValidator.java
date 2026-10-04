package dev.afkmod.gui;

import dev.afkmod.config.SettingInfo;
import dev.afkmod.logic.KeywordMatcher;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Input validation for the GUI fields. Every method returns a {@link Parsed} (a value or a short error message) and
 * never throws, so bad input can only ever show a red message. Ranges come from {@link SettingInfo}. No Minecraft
 * imports.
 */
public final class FieldValidator {
	private static final Pattern WHOLE = Pattern.compile("[+-]?\\d{1,15}");
	private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d{1,15}(\\.\\d{0,9})?|\\.\\d{1,9})");

	private FieldValidator() {
	}

	/** A parsed value, or {@code error} (never both). */
	public record Parsed<T>(T value, String error) {
		public boolean ok() {
			return error == null;
		}

		static <T> Parsed<T> of(T value) {
			return new Parsed<>(value, null);
		}

		static <T> Parsed<T> fail(String error) {
			return new Parsed<>(null, error);
		}
	}

	/** A whole number inside the registered range of {@code key}. */
	public static Parsed<Integer> wholeNumber(String text, String key) {
		SettingInfo.Entry entry = SettingInfo.get(key);
		String t = text == null ? "" : text.trim();
		if (t.isEmpty()) return Parsed.fail("Enter a whole number" + rangeHint(entry));
		if (!WHOLE.matcher(t).matches()) return Parsed.fail("Not a whole number" + rangeHint(entry));
		long value = Long.parseLong(t.startsWith("+") ? t.substring(1) : t);
		if (entry.hasRange() && (value < entry.min() || value > entry.max())) {
			return Parsed.fail("Must be " + entry.rangeLine());
		}
		return Parsed.of((int) value);
	}

	/** A decimal number (dot as separator) inside the registered range of {@code key}. */
	public static Parsed<Double> decimal(String text, String key) {
		SettingInfo.Entry entry = SettingInfo.get(key);
		String t = text == null ? "" : text.trim();
		if (t.isEmpty()) return Parsed.fail("Enter a number" + rangeHint(entry));
		if (!DECIMAL.matcher(t).matches()) return Parsed.fail("Not a number" + rangeHint(entry));
		double value;
		try {
			value = Double.parseDouble(t);
		} catch (NumberFormatException e) {
			return Parsed.fail("Not a number" + rangeHint(entry));
		}
		if (entry.hasRange() && (value < entry.min() || value > entry.max())) {
			return Parsed.fail("Must be " + entry.rangeLine());
		}
		return Parsed.of(value);
	}

	/** An optional decimal with no range: an empty field gives a null value (no error). */
	public static Parsed<Double> optionalDecimal(String text) {
		String t = text == null ? "" : text.trim();
		if (t.isEmpty()) return Parsed.of(null);
		if (!DECIMAL.matcher(t).matches()) return Parsed.fail("Not a number");
		try {
			return Parsed.of(Double.parseDouble(t));
		} catch (NumberFormatException e) {
			return Parsed.fail("Not a number");
		}
	}

	/** A comma-separated word list. When {@code required}, at least one word must remain after trimming. */
	public static Parsed<List<String>> words(String text, boolean required) {
		List<String> list = KeywordMatcher.parseList(text == null ? "" : text);
		if (required && list.isEmpty()) return Parsed.fail("Enter at least one word, separated by commas");
		return Parsed.of(list);
	}

	/** A whole number from 0 up to {@code max} for the timer's hour, minute and second fields; blank counts as 0. */
	public static Parsed<Long> timePart(String text, String name, long max) {
		String t = text == null ? "" : text.trim();
		if (t.isEmpty()) return Parsed.of(0L);
		if (!WHOLE.matcher(t).matches() || t.startsWith("-")) return Parsed.fail(name + " must be a whole number");
		long value = Long.parseLong(t.startsWith("+") ? t.substring(1) : t);
		if (value > max) return Parsed.fail(name + " must be at most " + max);
		return Parsed.of(value);
	}

	private static String rangeHint(SettingInfo.Entry entry) {
		return entry.rangeLine() == null ? "" : " (" + entry.rangeLine() + ")";
	}
}
