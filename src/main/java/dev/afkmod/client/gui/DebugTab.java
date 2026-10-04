package dev.afkmod.client.gui;

import dev.afkmod.AfkModClient;
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

	DebugTab(TestLabActions actions) {
		super("Debug", "Debug");
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
