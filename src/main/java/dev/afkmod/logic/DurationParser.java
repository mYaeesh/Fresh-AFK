package dev.afkmod.logic;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats timer durations. Accepted input (case-insensitive, spaces allowed between parts):
 * <ul>
 *   <li>unit form: {@code 2h30m}, {@code 1h 5m 3s}, {@code 45m}, {@code 90s} (each unit at most once, any order)</li>
 *   <li>clock form: {@code 1:30:00} (h:m:s) or {@code 30:00} (m:s)</li>
 *   <li>a bare number: seconds, e.g. {@code 90}</li>
 * </ul>
 * {@code 0} is valid and means "no timer".
 */
public final class DurationParser {
	/** 100 hours; keeps the HUD readable and arithmetic far from overflow. */
	public static final long MAX_SECONDS = 100L * 3600;

	private static final Pattern UNIT_PART = Pattern.compile("(\\d+)\\s*([hms])");
	private static final Pattern UNIT_FORM = Pattern.compile("(\\d+\\s*[hms]\\s*)+");
	private static final Pattern CLOCK_FORM = Pattern.compile("(\\d+):(\\d{1,2})(?::(\\d{1,2}))?");
	private static final Pattern NUMBER = Pattern.compile("\\d+");

	private DurationParser() {
	}

	/** @return the duration in seconds; never negative */
	public static long parse(String input) {
		if (input == null || input.isBlank()) throw new DurationParseException("Enter a duration, e.g. 2h30m");
		String s = input.trim().toLowerCase(Locale.ROOT);

		long seconds;
		if (NUMBER.matcher(s).matches()) {
			seconds = toLong(s);
		} else if (CLOCK_FORM.matcher(s).matches()) {
			seconds = parseClock(s);
		} else if (UNIT_FORM.matcher(s).matches()) {
			seconds = parseUnits(s);
		} else {
			throw new DurationParseException("Invalid duration \"" + input.trim() + "\" (use e.g. 2h30m, 45m or 1:30:00)");
		}
		return checkMax(seconds);
	}

	/** Builds a duration from separate hour/minute/second fields; blank fields count as 0. */
	public static long fromFields(String hours, String minutes, String seconds) {
		long h = field(hours, "Hours");
		long m = field(minutes, "Minutes");
		long sec = field(seconds, "Seconds");
		if (h > MAX_SECONDS / 3600 || m > MAX_SECONDS / 60) throw tooLong();
		return checkMax(h * 3600 + m * 60 + sec);
	}

	/** Formats as {@code H:MM:SS}, or {@code M:SS} under an hour. */
	public static String format(long totalSeconds) {
		long s = Math.max(0, totalSeconds);
		long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
		return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
	}

	private static long parseClock(String s) {
		Matcher m = CLOCK_FORM.matcher(s);
		if (!m.matches()) throw new IllegalStateException("CLOCK_FORM already matched");
		boolean hasHours = m.group(3) != null;
		long h = hasHours ? toLong(m.group(1)) : 0;
		long min = toLong(hasHours ? m.group(2) : m.group(1));
		long sec = toLong(hasHours ? m.group(3) : m.group(2));
		if (sec > 59 || hasHours && min > 59) {
			throw new DurationParseException("Minutes and seconds must be 0-59 in \"" + s + "\"");
		}
		if (h > MAX_SECONDS / 3600 || min > MAX_SECONDS / 60) throw tooLong();
		return h * 3600 + min * 60 + sec;
	}

	private static long parseUnits(String s) {
		Matcher m = UNIT_PART.matcher(s);
		long total = 0;
		boolean seenH = false, seenM = false, seenS = false;
		while (m.find()) {
			long value = toLong(m.group(1));
			switch (m.group(2)) {
				case "h" -> {
					if (seenH) throw duplicate("h");
					seenH = true;
					if (value > MAX_SECONDS / 3600) throw tooLong();
					total += value * 3600;
				}
				case "m" -> {
					if (seenM) throw duplicate("m");
					seenM = true;
					if (value > MAX_SECONDS / 60) throw tooLong();
					total += value * 60;
				}
				default -> {
					if (seenS) throw duplicate("s");
					seenS = true;
					total += value;
				}
			}
		}
		return total;
	}

	private static long field(String text, String name) {
		if (text == null || text.isBlank()) return 0;
		String t = text.trim();
		if (!NUMBER.matcher(t).matches()) throw new DurationParseException(name + " must be a whole number");
		return toLong(t);
	}

	private static long toLong(String digits) {
		// Anything longer than this is far beyond MAX_SECONDS anyway.
		if (digits.length() > 12) throw tooLong();
		return Long.parseLong(digits);
	}

	private static long checkMax(long seconds) {
		if (seconds > MAX_SECONDS) throw tooLong();
		return seconds;
	}

	private static DurationParseException duplicate(String unit) {
		return new DurationParseException("Unit \"" + unit + "\" used more than once");
	}

	private static DurationParseException tooLong() {
		return new DurationParseException("Duration too long (max " + MAX_SECONDS / 3600 + "h)");
	}

	/** Thrown with a short message suitable for showing in red in the GUI. */
	public static final class DurationParseException extends IllegalArgumentException {
		public DurationParseException(String message) {
			super(message);
		}
	}
}
