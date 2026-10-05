package dev.afkmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.afkmod.logic.DurationParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent settings, stored as {@code config/afkmod.json}. Plain Java (Gson only) so it can be unit tested.
 * Field names are the JSON keys, so do not rename them.
 */
public final class AfkConfig {
	public static final String FILE_NAME = "afkmod.json";

	public static final float HUD_SCALE_MIN = 0.5f;
	public static final float HUD_SCALE_MAX = 2.0f;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public int checkIntervalTicks = 20;
	public List<String> restartKeywords = new ArrayList<>(List.of("servers", "restart queue"));
	public List<String> ignoreKeywords = new ArrayList<>(List.of("whispered"));
	/** Action bar text that is on screen while the server is frozen for a restart (e.g. "restart queue #3"). */
	public List<String> queueKeywords = new ArrayList<>(List.of("restart queue"));
	/** Unused: the server sends no "all clear" message. Kept so existing config files stay valid. */
	public List<String> restartEndKeywords = new ArrayList<>();
	/** Unused since the queue-text logic replaced the clear timeout. Kept so existing config files stay valid. */
	public int restartClearTimeoutSeconds = 10;
	/** The queue text must be continuously gone this long before it counts as gone. */
	public int queueGoneSeconds = 3;
	/** Queue text gone but no reconnect happened: resume after this long anyway. */
	public int noReconnectFallbackSeconds = 30;
	/** Still not resumed this long after the restart began: turn the mod off. */
	public int maxRestartWaitMinutes = 15;
	public int settleDelaySeconds = 3;
	public int postResumeCooldownSeconds = 15;
	public int reconnectGraceSeconds = 120;
	public boolean releaseCrouchOnRestart = false;
	/** 0 = no timer. */
	public long timerSeconds = 0;
	/** Named timer lengths shown as one-click buttons in the Timer tab. */
	public List<TimerPreset> timerPresets = TimerPreset.defaults();
	/** Keep the game running (not paused) while its window is in the background, so the mod works after alt-tab. */
	public boolean keepRunningUnfocused = true;
	/** After a connection loss while AFK is on, try to join the same server again. */
	public boolean autoReconnect = true;
	/** Most join attempts per connection loss. */
	public int reconnectAttempts = 5;
	/** Wait this long after a connection loss, and between attempts. */
	public int reconnectDelaySeconds = 10;
	public boolean debugLogging = false;
	public boolean hudEnabled = true;
	public boolean hudDetailed = false;
	public HudCorner hudCorner = HudCorner.TOP_LEFT;
	public float hudScale = 0.75f;

	// ---- movement recovery ----
	/** Detect "not moving" while ACTIVE and run the recovery. */
	public boolean movementCheckEnabled = true;
	/** The position window: stuck needs at least this many seconds of samples. */
	public int stuckWindowSeconds = 20;
	/** Stuck when every sample in the window is closer than this (x/z) to the current position. */
	public double stuckDistanceBlocks = 3.0;
	/** Duration of the smooth turn to the nearest cardinal direction; 0 = instant snap. */
	public double recoveryTurnSeconds = 0.5;
	/** The recovery walk succeeds after moving this far (x/z). */
	public double recoveryWalkBlocks = 2.0;
	/** The recovery walk fails after this long (the turn isn't counted). */
	public double recoveryWalkTimeoutSeconds = 5.0;
	/** Retries after the first failed attempt (so retries + 1 attempts in total). */
	public int recoveryRetries = 2;
	/** Don't walk towards a drop or harmful blocks. */
	public boolean recoveryEdgeCheck = true;

	// ---- Test Lab ----
	/** The Test Lab debug report hides the server address and the player name. */
	public boolean reportRedactIdentity = true;
	/**
	 * Test Lab override of {@link #maxRestartWaitMinutes} in seconds (0 = not set). Transient: never saved, and only
	 * ever set on the in-memory effective copy made by the Test Lab's override layer.
	 */
	public transient int maxRestartWaitSecondsOverride;

	/** The max restart wait in seconds: the Test Lab override if set, otherwise {@link #maxRestartWaitMinutes}. */
	public long maxRestartWaitSeconds() {
		return maxRestartWaitSecondsOverride > 0 ? maxRestartWaitSecondsOverride : maxRestartWaitMinutes * 60L;
	}

	/** Replaces missing or out-of-range values (e.g. from a hand-edited file) with safe ones. */
	public AfkConfig sanitize() {
		AfkConfig defaults = new AfkConfig();
		if (checkIntervalTicks < 1) checkIntervalTicks = defaults.checkIntervalTicks;
		restartKeywords = cleanList(restartKeywords, defaults.restartKeywords);
		ignoreKeywords = cleanList(ignoreKeywords, defaults.ignoreKeywords);
		queueKeywords = cleanList(queueKeywords, defaults.queueKeywords);
		restartEndKeywords = cleanList(restartEndKeywords, defaults.restartEndKeywords);
		restartClearTimeoutSeconds = Math.max(0, restartClearTimeoutSeconds);
		queueGoneSeconds = Math.max(0, queueGoneSeconds);
		noReconnectFallbackSeconds = Math.max(0, noReconnectFallbackSeconds);
		if (maxRestartWaitMinutes < 1) maxRestartWaitMinutes = defaults.maxRestartWaitMinutes;
		settleDelaySeconds = Math.max(0, settleDelaySeconds);
		postResumeCooldownSeconds = Math.max(0, postResumeCooldownSeconds);
		reconnectGraceSeconds = Math.max(0, reconnectGraceSeconds);
		timerSeconds = Math.clamp(timerSeconds, 0, DurationParser.MAX_SECONDS);
		timerPresets = TimerPreset.clean(timerPresets);
		if (hudCorner == null) hudCorner = defaults.hudCorner;
		if (Float.isNaN(hudScale)) hudScale = defaults.hudScale;
		hudScale = Math.clamp(hudScale, HUD_SCALE_MIN, HUD_SCALE_MAX);
		if (stuckWindowSeconds < 1) stuckWindowSeconds = defaults.stuckWindowSeconds;
		stuckDistanceBlocks = finiteClamp(stuckDistanceBlocks, defaults.stuckDistanceBlocks, 0.1, 64.0);
		recoveryTurnSeconds = finiteClamp(recoveryTurnSeconds, defaults.recoveryTurnSeconds, 0.0, 10.0);
		recoveryWalkBlocks = finiteClamp(recoveryWalkBlocks, defaults.recoveryWalkBlocks, 0.1, 64.0);
		recoveryWalkTimeoutSeconds = finiteClamp(recoveryWalkTimeoutSeconds, defaults.recoveryWalkTimeoutSeconds, 0.5, 120.0);
		recoveryRetries = Math.clamp(recoveryRetries, 0, 20);
		// Upper bounds come from the same registry that the GUI validates against and the tooltips show.
		checkIntervalTicks = SettingInfo.clampInt("checkIntervalTicks", checkIntervalTicks);
		queueGoneSeconds = SettingInfo.clampInt("queueGoneSeconds", queueGoneSeconds);
		noReconnectFallbackSeconds = SettingInfo.clampInt("noReconnectFallbackSeconds", noReconnectFallbackSeconds);
		maxRestartWaitMinutes = SettingInfo.clampInt("maxRestartWaitMinutes", maxRestartWaitMinutes);
		settleDelaySeconds = SettingInfo.clampInt("settleDelaySeconds", settleDelaySeconds);
		postResumeCooldownSeconds = SettingInfo.clampInt("postResumeCooldownSeconds", postResumeCooldownSeconds);
		reconnectGraceSeconds = SettingInfo.clampInt("reconnectGraceSeconds", reconnectGraceSeconds);
		stuckWindowSeconds = SettingInfo.clampInt("stuckWindowSeconds", stuckWindowSeconds);
		reconnectAttempts = SettingInfo.clampInt("reconnectAttempts", reconnectAttempts);
		reconnectDelaySeconds = SettingInfo.clampInt("reconnectDelaySeconds", reconnectDelaySeconds);
		return this;
	}

	private static double finiteClamp(double value, double fallback, double min, double max) {
		return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
	}

	/** A missing list (null) falls back to the default; blank entries are dropped. An explicit empty list is kept. */
	private static List<String> cleanList(List<String> list, List<String> fallback) {
		if (list == null) return new ArrayList<>(fallback);
		List<String> out = new ArrayList<>();
		for (String s : list) {
			if (s != null && !s.isBlank()) out.add(s.trim());
		}
		return out;
	}

	/** Copies every setting from {@code other} into this instance, keeping this object's identity. */
	public void copyFrom(AfkConfig other) {
		checkIntervalTicks = other.checkIntervalTicks;
		restartKeywords = new ArrayList<>(other.restartKeywords);
		ignoreKeywords = new ArrayList<>(other.ignoreKeywords);
		queueKeywords = new ArrayList<>(other.queueKeywords);
		restartEndKeywords = new ArrayList<>(other.restartEndKeywords);
		restartClearTimeoutSeconds = other.restartClearTimeoutSeconds;
		queueGoneSeconds = other.queueGoneSeconds;
		noReconnectFallbackSeconds = other.noReconnectFallbackSeconds;
		maxRestartWaitMinutes = other.maxRestartWaitMinutes;
		settleDelaySeconds = other.settleDelaySeconds;
		postResumeCooldownSeconds = other.postResumeCooldownSeconds;
		reconnectGraceSeconds = other.reconnectGraceSeconds;
		releaseCrouchOnRestart = other.releaseCrouchOnRestart;
		timerSeconds = other.timerSeconds;
		timerPresets = TimerPreset.copyOf(other.timerPresets);
		keepRunningUnfocused = other.keepRunningUnfocused;
		autoReconnect = other.autoReconnect;
		reconnectAttempts = other.reconnectAttempts;
		reconnectDelaySeconds = other.reconnectDelaySeconds;
		debugLogging = other.debugLogging;
		hudEnabled = other.hudEnabled;
		hudDetailed = other.hudDetailed;
		hudCorner = other.hudCorner;
		hudScale = other.hudScale;
		movementCheckEnabled = other.movementCheckEnabled;
		stuckWindowSeconds = other.stuckWindowSeconds;
		stuckDistanceBlocks = other.stuckDistanceBlocks;
		recoveryTurnSeconds = other.recoveryTurnSeconds;
		recoveryWalkBlocks = other.recoveryWalkBlocks;
		recoveryWalkTimeoutSeconds = other.recoveryWalkTimeoutSeconds;
		recoveryRetries = other.recoveryRetries;
		recoveryEdgeCheck = other.recoveryEdgeCheck;
		reportRedactIdentity = other.reportRedactIdentity;
		maxRestartWaitSecondsOverride = other.maxRestartWaitSecondsOverride;
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	public static AfkConfig fromJson(String json) {
		AfkConfig config = GSON.fromJson(json, AfkConfig.class);
		return (config == null ? new AfkConfig() : config).sanitize();
	}

	/**
	 * Loads the config, creating the file with defaults if it is missing. A corrupt file is moved
	 * aside to {@code afkmod.json.bak} and replaced with defaults rather than crashing the game.
	 */
	public static AfkConfig load(Path file) throws IOException {
		if (Files.notExists(file)) {
			AfkConfig config = new AfkConfig();
			config.save(file);
			return config;
		}
		AfkConfig config;
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			config = GSON.fromJson(reader, AfkConfig.class);
		} catch (JsonParseException e) {
			Files.move(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
			config = null;
		}
		config = (config == null ? new AfkConfig() : config).sanitize();
		// Write back so newly added fields appear in the file.
		config.save(file);
		return config;
	}

	public void save(Path file) throws IOException {
		Path dir = file.toAbsolutePath().getParent();
		if (dir != null) Files.createDirectories(dir);
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(this, writer);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
	}
}
