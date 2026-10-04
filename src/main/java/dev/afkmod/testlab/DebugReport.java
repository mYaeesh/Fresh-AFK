package dev.afkmod.testlab;

import dev.afkmod.stats.StatsEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The debug report (I1): versions, state, effective config and overrides, keybinds, the last events, the last test
 * results and the last debug log lines. The server address and the player name are redacted by default
 * ({@code reportRedactIdentity}).
 */
public final class DebugReport {
	public static final int MAX_EVENTS = 100;
	public static final int MAX_DEBUG_LINES = 50;
	public static final String SERVER_PLACEHOLDER = "<server>";
	public static final String PLAYER_PLACEHOLDER = "<player>";
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** Everything the report shows; {@code serverAddress} and {@code playerName} may be null. */
	public record Input(String modVersion, String minecraftVersion, String loaderVersion, String fabricApiVersion,
			String javaVersion, String os, String state, String configJson, String overrides, List<String> keybinds,
			List<StatsEvent> events, List<TestResult> results, List<String> debugLines, String serverAddress,
			String playerName, boolean redact, long generatedAtMs, ZoneId zone) {
		public Input {
			keybinds = List.copyOf(keybinds);
			events = List.copyOf(events);
			results = List.copyOf(results);
			debugLines = List.copyOf(debugLines);
			zone = zone == null ? ZoneId.systemDefault() : zone;
		}
	}

	private DebugReport() {
	}

	public static String build(Input in) {
		Objects.requireNonNull(in);
		StringBuilder sb = new StringBuilder();
		sb.append("AFK Mod debug report\n");
		sb.append("Generated: ").append(time(in.generatedAtMs(), in.zone())).append('\n');
		sb.append("Identity redacted: ").append(in.redact() ? "yes" : "no").append("\n\n");

		section(sb, "Versions");
		sb.append("AFK Mod: ").append(in.modVersion()).append('\n');
		sb.append("Minecraft: ").append(in.minecraftVersion()).append('\n');
		sb.append("Fabric Loader: ").append(in.loaderVersion()).append('\n');
		sb.append("Fabric API: ").append(in.fabricApiVersion()).append('\n');
		sb.append("Java: ").append(in.javaVersion()).append('\n');
		sb.append("OS: ").append(in.os()).append("\n\n");

		section(sb, "State");
		sb.append(in.state()).append("\n\n");

		section(sb, "Effective config");
		sb.append(in.configJson()).append('\n');
		sb.append("Active Test Lab overrides: ").append(in.overrides()).append("\n\n");

		section(sb, "Keybinds");
		for (String k : in.keybinds()) sb.append(k).append('\n');
		sb.append('\n');

		List<StatsEvent> events = last(in.events(), MAX_EVENTS);
		section(sb, "Last " + events.size() + " events");
		for (StatsEvent e : events) {
			sb.append(time(e.timeMillis(), in.zone())).append(' ').append(e.test() ? "[TEST] " : "")
					.append(e.type()).append(' ').append(e.text()).append('\n');
		}
		sb.append('\n');

		section(sb, "Last test results");
		if (in.results().isEmpty()) sb.append("(none)\n");
		for (TestResult r : in.results()) sb.append(time(r.epochMs(), in.zone())).append(' ').append(r.line()).append('\n');
		sb.append('\n');

		List<String> lines = last(in.debugLines(), MAX_DEBUG_LINES);
		section(sb, "Last " + lines.size() + " debug log lines");
		for (String line : lines) sb.append(line).append('\n');

		String text = sb.toString();
		return in.redact() ? redact(text, in.serverAddress(), in.playerName()) : text;
	}

	/**
	 * Replaces the server address (also without its port, case-insensitive) with {@value #SERVER_PLACEHOLDER} and the
	 * player name (as a whole word, case-insensitive) with {@value #PLAYER_PLACEHOLDER}. Null or blank values are skipped.
	 */
	public static String redact(String text, String serverAddress, String playerName) {
		String out = text;
		if (serverAddress != null && !serverAddress.isBlank()) {
			String address = serverAddress.trim();
			out = replaceLiteral(out, address, SERVER_PLACEHOLDER);
			int colon = address.lastIndexOf(':');
			String host = colon > 0 && address.indexOf(':') == colon ? address.substring(0, colon) : null;
			if (host != null && !host.isBlank()) out = replaceLiteral(out, host, SERVER_PLACEHOLDER);
		}
		if (playerName != null && !playerName.isBlank()) {
			Pattern p = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(playerName.trim()) + "(?![A-Za-z0-9_])",
					Pattern.CASE_INSENSITIVE);
			out = p.matcher(out).replaceAll(Matcher.quoteReplacement(PLAYER_PLACEHOLDER));
		}
		return out;
	}

	private static String replaceLiteral(String text, String literal, String replacement) {
		return Pattern.compile(Pattern.quote(literal), Pattern.CASE_INSENSITIVE).matcher(text)
				.replaceAll(Matcher.quoteReplacement(replacement));
	}

	private static void section(StringBuilder sb, String title) {
		sb.append("== ").append(title).append(" ==\n");
	}

	private static <T> List<T> last(List<T> list, int n) {
		return list.size() <= n ? list : list.subList(list.size() - n, list.size());
	}

	static String time(long epochMs, ZoneId zone) {
		return TIME.format(Instant.ofEpochMilli(epochMs).atZone(zone));
	}
}
