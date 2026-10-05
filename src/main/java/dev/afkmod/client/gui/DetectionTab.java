package dev.afkmod.client.gui;

import dev.afkmod.client.gui.Rows.HeaderRow;
import dev.afkmod.client.gui.SettingRows.NumberRow;
import dev.afkmod.client.gui.SettingRows.ToggleRow;
import dev.afkmod.client.gui.SettingRows.WordsRow;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.logic.KeywordMatcher;

import java.util.ArrayList;

/**
 * Restart detection and the connection: the three keyword lists, the restart timings, losing the connection (grace and
 * auto reconnect), and what happens when the game window loses focus.
 */
final class DetectionTab extends RowTab {

	DetectionTab() {
		super("Detect", "Detection");
		AfkConfig config = SettingRows.config();
		rows.add(new HeaderRow("Restart keywords"));
		rows.add(new WordsRow(font, "restartKeywords", true, KeywordMatcher.joinList(config.restartKeywords), t -> {
		}, list -> config.restartKeywords = new ArrayList<>(list)));
		rows.add(new WordsRow(font, "ignoreKeywords", false, KeywordMatcher.joinList(config.ignoreKeywords), t -> {
		}, list -> config.ignoreKeywords = new ArrayList<>(list)));
		rows.add(new WordsRow(font, "queueKeywords", true, KeywordMatcher.joinList(config.queueKeywords), t -> {
		}, list -> config.queueKeywords = new ArrayList<>(list)));

		rows.add(new HeaderRow("Restart timing"));
		rows.add(NumberRow.whole(font, "postResumeCooldownSeconds", config.postResumeCooldownSeconds, v -> config.postResumeCooldownSeconds = v));
		rows.add(NumberRow.whole(font, "queueGoneSeconds", config.queueGoneSeconds, v -> config.queueGoneSeconds = v));
		rows.add(NumberRow.whole(font, "noReconnectFallbackSeconds", config.noReconnectFallbackSeconds, v -> config.noReconnectFallbackSeconds = v));
		rows.add(NumberRow.whole(font, "maxRestartWaitMinutes", config.maxRestartWaitMinutes, v -> config.maxRestartWaitMinutes = v));
		rows.add(NumberRow.whole(font, "settleDelaySeconds", config.settleDelaySeconds, v -> config.settleDelaySeconds = v));
		rows.add(new ToggleRow(font, "releaseCrouchOnRestart", config.releaseCrouchOnRestart, v -> config.releaseCrouchOnRestart = v));

		rows.add(new HeaderRow("Connection"));
		rows.add(NumberRow.whole(font, "reconnectGraceSeconds", config.reconnectGraceSeconds, v -> config.reconnectGraceSeconds = v));
		rows.add(new ToggleRow(font, "autoReconnect", config.autoReconnect, v -> config.autoReconnect = v));
		rows.add(NumberRow.whole(font, "reconnectAttempts", config.reconnectAttempts, v -> config.reconnectAttempts = v));
		rows.add(NumberRow.whole(font, "reconnectDelaySeconds", config.reconnectDelaySeconds, v -> config.reconnectDelaySeconds = v));

		rows.add(new HeaderRow("Window"));
		rows.add(new ToggleRow(font, "keepRunningUnfocused", config.keepRunningUnfocused, v -> config.keepRunningUnfocused = v));
	}
}
