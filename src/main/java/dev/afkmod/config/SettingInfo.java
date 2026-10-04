package dev.afkmod.config;

import dev.afkmod.testlab.TestOptions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The one registry of every setting: display name, description, unit and valid range. The GUI (labels, number
 * validation, info-icon tooltips), the README settings table and the unit tests all read from here, so they cannot
 * drift apart. Plain Java (no Minecraft imports).
 * <p>
 * The default of a config setting is read by reflection from a fresh {@link AfkConfig} (and the Test Lab options from
 * a fresh {@link TestOptions}), never typed in a second time, so a tooltip can never disagree with the code.
 * {@code SettingInfoTest} fails if a config field has no entry here or an empty description.
 */
public final class SettingInfo {
	/** What kind of control edits a setting; decides how the default is shown. */
	public enum Kind {
		TOGGLE, INTEGER, DECIMAL, WORDS, CHOICE, TEXT
	}

	/** Config settings are fields of {@link AfkConfig}; Test Lab options belong to the Test Lab screen only. */
	public enum Scope {
		CONFIG, TEST_LAB
	}

	/** One registry entry. {@code min}/{@code max} are null for settings without a numeric range. */
	public record Entry(String key, String displayName, String description, String unit, Double min, Double max,
	                    Kind kind, Scope scope, boolean unused, String defaultNote, String rangeText,
	                    Supplier<Object> defaultSupplier) {

		/** The real default, read from a fresh config (or fresh Test Lab options). */
		public Object defaultValue() {
			return defaultSupplier.get();
		}

		/** The default as shown to the user, without the unit, e.g. {@code 20}, {@code on}, {@code servers, restart queue}. */
		public String defaultValueText() {
			return format(defaultValue());
		}

		public boolean hasRange() {
			return min != null && max != null;
		}

		public boolean isNumeric() {
			return kind == Kind.INTEGER || kind == Kind.DECIMAL;
		}

		/** The range as shown to the user, e.g. {@code 5-120 s}, or null when the setting has none. */
		public String rangeLine() {
			if (rangeText != null) return rangeText;
			if (!hasRange()) return null;
			return number(min) + "-" + number(max) + unitSuffix();
		}

		private String unitSuffix() {
			return unit.isEmpty() ? "" : " " + unit;
		}

		/** The default with its unit and note, e.g. {@code 20 s} or {@code 0 s (no timer)}. */
		public String defaultWithUnit() {
			StringBuilder sb = new StringBuilder(defaultValueText());
			if (isNumeric() && defaultValue() != null) sb.append(unitSuffix());
			if (defaultNote != null) sb.append(" (").append(defaultNote).append(")");
			return sb.toString();
		}

		/** The final tooltip line, e.g. {@code Default: 20 s - Range: 5-120 s}. */
		public String defaultLine() {
			String range = rangeLine();
			return "Default: " + defaultWithUnit() + (range == null ? "" : " - Range: " + range);
		}

		/** Clamps a number to this entry's range (no change when there is none). */
		public double clamp(double value) {
			if (!hasRange()) return value;
			return Math.clamp(value, min, max);
		}
	}

	private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

	private SettingInfo() {
	}

	// ---- lookups ----

	/** The entry for a setting key (a config field name or a Test Lab option name); throws if there is none. */
	public static Entry get(String key) {
		Entry entry = ENTRIES.get(key);
		if (entry == null) throw new IllegalArgumentException("No SettingInfo entry for '" + key + "'");
		return entry;
	}

	public static Optional<Entry> find(String key) {
		return Optional.ofNullable(ENTRIES.get(key));
	}

	/** Every entry, in the order of the config class and then the Test Lab options. */
	public static List<Entry> all() {
		return List.copyOf(ENTRIES.values());
	}

	/** Clamps {@code value} to the range registered for {@code key}. */
	public static int clampInt(String key, int value) {
		return (int) get(key).clamp(value);
	}

	public static long clampLong(String key, long value) {
		return (long) get(key).clamp((double) value);
	}

	public static double clampDouble(String key, double value) {
		return get(key).clamp(value);
	}

	/** The names of the fields {@link AfkConfig} saves (public, non-static, non-transient). */
	public static List<String> configFieldNames() {
		List<String> names = new ArrayList<>();
		for (Field field : AfkConfig.class.getDeclaredFields()) {
			int mod = field.getModifiers();
			if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || !Modifier.isPublic(mod)) continue;
			names.add(field.getName());
		}
		return names;
	}

	// ---- formatting ----

	static String format(Object value) {
		if (value == null) return "none";
		if (value instanceof String s && s.isEmpty()) return "empty";
		if (value instanceof Boolean b) return b ? "on" : "off";
		if (value instanceof Float f) return number(f.doubleValue());
		if (value instanceof Double d) return number(d);
		if (value instanceof Number n) return Long.toString(n.longValue());
		if (value instanceof List<?> list) {
			if (list.isEmpty()) return "none";
			StringBuilder sb = new StringBuilder();
			for (Object o : list) {
				if (sb.length() > 0) sb.append(", ");
				sb.append(o);
			}
			return sb.toString();
		}
		if (value instanceof HudCorner corner) return corner.displayName();
		return value.toString();
	}

	/** 3.0 gives "3", 0.75 gives "0.75" (at most 2 decimals, no trailing zeros). */
	public static String number(double value) {
		if (value == Math.rint(value) && Math.abs(value) < 1e15) return Long.toString((long) value);
		String s = String.format(Locale.ROOT, "%.2f", value);
		if (s.indexOf('.') >= 0) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		return s;
	}

	// ---- the README table ----

	public static final String README_START = "<!-- SETTINGS-TABLE:START (generated from SettingInfo, see ReadmeSettingsTableTest) -->";
	public static final String README_END = "<!-- SETTINGS-TABLE:END -->";

	/** The markdown table of every config setting the GUI shows (the unused fields are listed by the README itself). */
	public static String markdownTable() {
		StringBuilder sb = new StringBuilder();
		sb.append("| Setting | Config key | Default | Range | What it does |\n");
		sb.append("|---|---|---|---|---|\n");
		for (Entry e : ENTRIES.values()) {
			if (e.scope() != Scope.CONFIG || e.unused()) continue;
			String range = e.rangeLine() == null ? "" : e.rangeLine();
			sb.append("| ").append(e.displayName()).append(" | `").append(e.key()).append("` | ")
					.append(escape(e.defaultWithUnit())).append(" | ").append(escape(range)).append(" | ")
					.append(escape(e.description())).append(" |\n");
		}
		return sb.toString();
	}

	private static String escape(String s) {
		return s.replace("|", "\\|").replace("\n", " ");
	}

	/** Prints the table, for pasting into the README. */
	public static void main(String[] args) {
		System.out.print(markdownTable());
	}

	// ---- the entries ----

	/** Registers a setting whose default is read from the config field {@code key}. */
	private static Builder config(String key, String displayName, Kind kind, String description) {
		return new Builder(key, displayName, kind, Scope.CONFIG, description, () -> readDefault(key));
	}

	private static Object readDefault(String key) {
		try {
			Field field = AfkConfig.class.getField(key);
			return field.get(new AfkConfig());
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("AfkConfig has no readable field '" + key + "'", e);
		}
	}

	private static Builder testLab(String key, String displayName, Kind kind, String description, Supplier<Object> def) {
		return new Builder(key, displayName, kind, Scope.TEST_LAB, description, def);
	}

	private static final class Builder {
		private final String key;
		private final String displayName;
		private final Kind kind;
		private final Scope scope;
		private final String description;
		private final Supplier<Object> def;
		private String unit = "";
		private Double min;
		private Double max;
		private boolean unused;
		private String defaultNote;
		private String rangeText;

		Builder(String key, String displayName, Kind kind, Scope scope, String description, Supplier<Object> def) {
			this.key = key;
			this.displayName = displayName;
			this.kind = kind;
			this.scope = scope;
			this.description = description;
			this.def = def;
		}

		Builder unit(String unit) {
			this.unit = unit;
			return this;
		}

		Builder range(double min, double max) {
			this.min = min;
			this.max = max;
			return this;
		}

		Builder rangeText(String text) {
			this.rangeText = text;
			return this;
		}

		Builder note(String note) {
			this.defaultNote = note;
			return this;
		}

		Builder unused() {
			this.unused = true;
			return this;
		}

		void add() {
			Entry entry = new Entry(key, displayName, description, unit, min, max, kind, scope, unused, defaultNote, rangeText, def);
			if (ENTRIES.put(key, entry) != null) throw new IllegalStateException("Duplicate SettingInfo key " + key);
		}
	}

	static {
		// Timing and detection
		config("checkIntervalTicks", "Check interval", Kind.INTEGER,
				"How often the mod re-checks crouch, clicking, movement and timers. 20 ticks = 1 second. "
						+ "Lower reacts faster but does more work; 20 is plenty.")
				.unit("ticks").range(1, 200).add();
		config("restartKeywords", "Restart keywords", Kind.WORDS,
				"Words that mark a message as a server-restart warning. If an incoming server message (chat, action bar, "
						+ "title...) contains any of them, the mod enters restart mode. Comma-separated, not case-sensitive, "
						+ "matches parts of words.").add();
		config("ignoreKeywords", "Ignore keywords", Kind.WORDS,
				"Messages containing any of these words are completely ignored, even if they also contain a restart keyword. "
						+ "Use it to block false triggers such as private messages.").add();
		config("queueKeywords", "Queue keywords", Kind.WORDS,
				"The text your server shows above the hotbar during a restart. The mod waits until this text has disappeared "
						+ "before it treats the restart as finished.").add();
		config("restartEndKeywords", "Restart-end keywords (unused)", Kind.WORDS,
				"Not used any more: the server sends no \"all clear\" message, so the end of a restart is detected from the "
						+ "queue text and the reconnect instead. Kept in the file so older config files stay valid.")
				.unused().add();
		config("restartClearTimeoutSeconds", "Restart clear timeout (unused)", Kind.INTEGER,
				"Not used any more: replaced by the queue-text and no-reconnect rules. Kept in the file so older config "
						+ "files stay valid.")
				.unit("s").unused().add();
		config("queueGoneSeconds", "Queue gone time", Kind.INTEGER,
				"How long the queue text must stay gone before the mod believes the restart is over. Prevents resuming "
						+ "during brief flickers. Raise it if the mod resumes too early.")
				.unit("s").range(0, 3600).add();
		config("settleDelaySeconds", "Settle delay", Kind.INTEGER,
				"Extra wait after the restart ends and you are back in the world, before the mod re-activates crouch and "
						+ "clicking. Gives the server a moment to settle.")
				.unit("s").range(0, 3600).add();
		config("postResumeCooldownSeconds", "Post-resume cooldown", Kind.INTEGER,
				"After resuming, restart messages are ignored for this long, so a leftover message can't start a second "
						+ "restart straight away.")
				.unit("s").range(0, 3600).add();
		config("noReconnectFallbackSeconds", "No-reconnect fallback", Kind.INTEGER,
				"If the queue text disappears but the server never reconnects you, the mod resumes by itself after this "
						+ "long.")
				.unit("s").range(0, 3600).add();
		config("reconnectGraceSeconds", "Reconnect grace", Kind.INTEGER,
				"If you get disconnected without a restart warning, the mod waits this long for you to rejoin before giving "
						+ "up and switching off.")
				.unit("s").range(0, 3600).add();
		config("maxRestartWaitMinutes", "Max restart wait", Kind.INTEGER,
				"The longest the mod will wait for a restart to finish. After that it releases everything and switches off.")
				.unit("min").range(1, 600).add();
		config("releaseCrouchOnRestart", "Release crouch on restart", Kind.TOGGLE,
				"Also stop crouching while the server restarts. By default only left and right click are released.").add();

		// Timer
		config("timerSeconds", "Timer", Kind.INTEGER,
				"The AFK countdown. When it reaches zero the mod stops everything and disconnects to the server list. Time "
						+ "spent in server restarts does not count. 0 means no timer.")
				.unit("s").range(0, 100 * 3600).rangeText("0 s-100 h").note("none").add();

		// Debug and HUD
		config("debugLogging", "Log all incoming messages", Kind.TOGGLE,
				"Writes every incoming message to afkmod-messages.log so you can see the exact text your server sends. "
						+ "Turn it on when setting up keywords.").add();
		config("hudEnabled", "Show HUD", Kind.TOGGLE, "Show or hide the on-screen status line.").add();
		config("hudDetailed", "Detailed HUD", Kind.TOGGLE, "Show 2-3 lines of status instead of one compact line.").add();
		config("hudCorner", "HUD corner", Kind.CHOICE, "Which screen corner the status line sits in.").add();
		config("hudScale", "HUD scale", Kind.DECIMAL, "Size of the status line. Smaller takes less screen space.")
				.unit("x").range(AfkConfig.HUD_SCALE_MIN, AfkConfig.HUD_SCALE_MAX).add();

		// Movement recovery
		config("movementCheckEnabled", "Stuck detection", Kind.TOGGLE,
				"Turns stuck detection on or off. When on, the mod notices if you have stopped moving and tries to get you "
						+ "going again.").add();
		config("stuckWindowSeconds", "Stuck window", Kind.INTEGER,
				"How long you must stay under the distance below before the mod decides you are stuck.")
				.unit("s").range(1, 600).add();
		config("stuckDistanceBlocks", "Stuck distance", Kind.DECIMAL,
				"If you moved less than this many blocks sideways (height is ignored) during the window, you count as stuck. "
						+ "Raise it if you get false alarms on a slow path.")
				.unit("blocks").range(0.1, 64).add();
		config("recoveryWalkBlocks", "Walk distance", Kind.DECIMAL,
				"How far the mod walks forward to get you un-stuck.")
				.unit("blocks").range(0.1, 64).add();
		config("recoveryWalkTimeoutSeconds", "Walk timeout", Kind.DECIMAL,
				"Gives up on a walk attempt if it hasn't covered the distance within this time. The turn before the walk "
						+ "doesn't count.")
				.unit("s").range(0.5, 120).add();
		config("recoveryRetries", "Retries", Kind.INTEGER,
				"How many more times to try after the first failed attempt. When every attempt fails, the mod stops and "
						+ "disconnects to the server list.")
				.range(0, 20).add();
		config("recoveryEdgeCheck", "Edge safety check", Kind.TOGGLE,
				"Before walking, checks the blocks ahead for drops, lava, fire or cactus and refuses to walk if it is "
						+ "unsafe, because you are not crouching during the walk. Turn it off only if you know the path is safe.")
				.add();
		config("recoveryTurnSeconds", "Turn duration", Kind.DECIMAL,
				"How long the turn to face the nearest north, east, south or west takes. The turn speeds up and slows down "
						+ "smoothly. Only the left-right direction changes, never up or down. 0 turns instantly.")
				.unit("s").range(0, 10).add();

		// Test Lab
		config("reportRedactIdentity", "Redact identity in report", Kind.TOGGLE,
				"Hides your server address and username in the debug report so you can share it safely.").add();
		testLab("testMessageText", "Message", Kind.TEXT,
				"Scenario A1: the text to test, exactly as the server would send it. Explain only shows what the mod would "
						+ "do; Send for real pushes it through the real handler.",
				() -> TestOptions.defaults().text).add();
		testLab("testMessageType", "Message type", Kind.CHOICE,
				"Scenario A1: where the message arrives from. Action bar text is also treated as queue text.",
				() -> TestOptions.defaults().messageType.displayName()).add();
		testLab("testRestartSeconds", "Restart length", Kind.INTEGER,
				"How long the simulated restart in the Test Lab keeps showing the fake queue text.",
				() -> TestOptions.defaults().testRestartSeconds)
				.unit("s").range(1, 600).add();
		testLab("testTimerSeconds", "Test timer", Kind.INTEGER,
				"Scenario E1: the length of the short timer that is run out through the real timer-end path.",
				() -> TestOptions.defaults().timerSeconds)
				.unit("s").range(1, 3600).add();
		testLab("testStartYaw", "Start yaw", Kind.DECIMAL,
				"Scenario D1: the yaw (left-right angle) to calculate the snap for. Scenario D6: the yaw to start the turn "
						+ "from. Leave it empty to use your current yaw.",
				() -> TestOptions.defaults().yaw)
				.unit("deg").note("your current yaw").add();
		testLab("testFullTimerEnd", "Timer end mode", Kind.CHOICE,
				"Scenario E1: Dry end runs the whole timer-end path except the final disconnect. Full also really "
						+ "disconnects you to the server list, and asks you to confirm first.",
				() -> TestOptions.defaults().fullTimerEnd ? "Full" : "Dry end").add();
		testLab("testRealDisconnect", "Real disconnect (D5)", Kind.TOGGLE,
				"Scenario D5: really disconnect you to the server list at the end of the failed-recovery test, instead of "
						+ "only logging WOULD DISCONNECT. Asks you to confirm a second time.",
				() -> TestOptions.defaults().allowRealDisconnect).add();
	}
}
