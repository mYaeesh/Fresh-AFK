package dev.afkmod.client.gui;

import dev.afkmod.AfkModClient;
import dev.afkmod.client.AfkController;
import dev.afkmod.client.MessageLog;
import dev.afkmod.client.gui.Rows.ButtonsRow;
import dev.afkmod.client.gui.Rows.HeaderRow;
import dev.afkmod.client.gui.Rows.TextRow;
import dev.afkmod.client.gui.SettingRows.ToggleRow;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.testlab.TestLab;
import dev.afkmod.testlab.TestResult;
import dev.afkmod.testlab.Verdict;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.afkmod.client.gui.Rows.seg;

/** Message logging, the log folder, and the debug report (the Test Lab's I1, so there is one implementation). */
final class DebugTab extends RowTab {

	/** The file the settings are exported to and imported from, next to the config file. */
	static final String EXPORT_FILE = "afkmod-export.json";

	private final Runnable rebuild;

	DebugTab(TestLabActions actions, Runnable rebuild) {
		super("Debug", "Debug");
		this.rebuild = rebuild;
		AfkConfig config = SettingRows.config();
		rows.add(new HeaderRow("Debug"));
		rows.add(new ToggleRow(font, "debugLogging", config.debugLogging, v -> config.debugLogging = v));

		Button openFolder = Button.builder(Component.literal("Open log folder"), b -> openLogFolder())
				.tooltip(Tooltip.create(Component.literal("Opens the config folder, which holds afkmod-messages.log")))
				.bounds(0, 0, 100, Ui.ROW_H).build();
		Button report = Button.builder(Component.literal("Copy debug report"), b -> actions.copyReport())
				.tooltip(Tooltip.create(Component.literal("Copies the debug report to the clipboard and saves " + TestLab.REPORT_FILE)))
				.bounds(0, 0, 100, Ui.ROW_H).build();
		rows.add(ButtonsRow.of(List.of(openFolder, report)));
		Button export = Button.builder(Component.literal("Export settings"), b -> exportSettings())
				.tooltip(Tooltip.create(Component.literal("Saves all settings (and timer presets) to " + EXPORT_FILE
						+ " in the config folder, to copy to another computer")))
				.bounds(0, 0, 100, Ui.ROW_H).build();
		Button importButton = Button.builder(Component.literal("Import settings"), b -> importSettings())
				.tooltip(Tooltip.create(Component.literal("Replaces all settings with the ones in " + EXPORT_FILE
						+ ". Values out of range are corrected. Cancel does not undo it.")))
				.bounds(0, 0, 100, Ui.ROW_H).build();
		rows.add(ButtonsRow.of(List.of(export, importButton)));
		rows.add(new TextRow(DebugTab::reportStatus));
		rows.add(TextRow.of("Log file: " + MessageLog.file().getFileName() + " (in the config folder)", Ui.GREY));
	}

	/** The last debug-report result, or the last status message (e.g. why a run was refused). */
	private static List<Rows.Seg> reportStatus() {
		TestResult last = TestLabActions.lab().lastResult();
		if (last != null && "I1".equals(last.scenarioId())) {
			return List.of(seg(last.reason(), last.verdict() == Verdict.FAIL ? Ui.ERROR : Ui.GREEN));
		}
		String status = TestLabActions.status();
		return status.isEmpty() ? List.of() : List.of(seg(status, TestLabActions.statusColor()));
	}

	private static Path exportFile() {
		return AfkModClient.configFile().resolveSibling(EXPORT_FILE);
	}

	private static void exportSettings() {
		try {
			AfkModClient.savedConfig().save(exportFile());
			TestLabActions.setStatus("Settings exported to " + EXPORT_FILE, Ui.GREEN);
		} catch (IOException e) {
			AfkModClient.LOGGER.error("Could not export the settings to {}", exportFile(), e);
			TestLabActions.setStatus("Could not export: " + e.getMessage(), Ui.ERROR);
		}
	}

	/** Replaces the settings with the exported file's (clamped like any loaded config) and redraws the screen. */
	private void importSettings() {
		Path file = exportFile();
		if (Files.notExists(file)) {
			TestLabActions.setStatus(EXPORT_FILE + " not found in the config folder", Ui.ERROR);
			return;
		}
		try {
			AfkConfig imported = AfkConfig.fromJson(Files.readString(file, java.nio.charset.StandardCharsets.UTF_8));
			AfkModClient.savedConfig().copyFrom(imported);
			// The timer length is part of the settings: put the countdown back to the imported length.
			AfkController.get().machine().setTimerSeconds(imported.timerSeconds);
			TestLabActions.setStatus("Settings imported from " + EXPORT_FILE, Ui.GREEN);
			rebuild.run();
		} catch (IOException | com.google.gson.JsonParseException e) {
			AfkModClient.LOGGER.error("Could not import the settings from {}", file, e);
			TestLabActions.setStatus("Could not import: " + e.getMessage(), Ui.ERROR);
		}
	}

	private static void openLogFolder() {
		Path folder = MessageLog.file().getParent();
		try {
			Files.createDirectories(folder);
			Util.getPlatform().openPath(folder);
		} catch (IOException e) {
			AfkModClient.LOGGER.error("Could not open {}", folder, e);
			TestLabActions.setStatus("Could not open the folder: " + e.getMessage(), Ui.ERROR);
		}
	}
}
