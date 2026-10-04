package dev.afkmod.client.gui;

import dev.afkmod.client.AfkController;
import dev.afkmod.client.MovementRecovery;
import dev.afkmod.client.gui.Rows.HeaderRow;
import dev.afkmod.client.gui.Rows.TextRow;
import dev.afkmod.client.gui.SettingRows.NumberRow;
import dev.afkmod.client.gui.SettingRows.ToggleRow;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.logic.RecoverySequence;

import java.util.List;
import java.util.Locale;

import static dev.afkmod.client.gui.Rows.seg;

/** Stuck detection and the recovery walk, plus the last yaw turn and the last recovery result. */
final class RecoveryTab extends RowTab {

	RecoveryTab() {
		super("Recover", "Recovery");
		AfkConfig config = SettingRows.config();
		rows.add(new HeaderRow("Recovery"));
		rows.add(new TextRow(() -> {
			MovementRecovery.YawTurn turn = AfkController.get().recovery().lastYawTurn();
			if (turn == null) return List.of(seg("Last yaw turn: ", Ui.TEXT), seg("none yet", Ui.GREY));
			return List.of(seg("Last yaw turn: ", Ui.TEXT), seg(String.format(Locale.ROOT, "%s (%.1f to %.1f deg) in %.2f s",
					turn.direction(), turn.fromYaw(), turn.toYaw(), turn.seconds()), Ui.TEXT));
		}));
		rows.add(new TextRow(() -> {
			RecoverySequence.Outcome outcome = AfkController.get().recovery().lastOutcome();
			if (outcome == null) return List.of(seg("Last recovery result: ", Ui.TEXT), seg("none yet", Ui.GREY));
			return List.of(seg("Last recovery result: ", Ui.TEXT), seg(outcome.name(), outcome.isSuccess() ? Ui.GREEN : Ui.ERROR));
		}));
		rows.add(new ToggleRow(font, "movementCheckEnabled", config.movementCheckEnabled, v -> config.movementCheckEnabled = v));
		rows.add(NumberRow.whole(font, "stuckWindowSeconds", config.stuckWindowSeconds, v -> config.stuckWindowSeconds = v));
		rows.add(NumberRow.decimal(font, "stuckDistanceBlocks", config.stuckDistanceBlocks, v -> config.stuckDistanceBlocks = v));
		rows.add(NumberRow.decimal(font, "recoveryWalkBlocks", config.recoveryWalkBlocks, v -> config.recoveryWalkBlocks = v));
		rows.add(NumberRow.decimal(font, "recoveryWalkTimeoutSeconds", config.recoveryWalkTimeoutSeconds, v -> config.recoveryWalkTimeoutSeconds = v));
		rows.add(NumberRow.whole(font, "recoveryRetries", config.recoveryRetries, v -> config.recoveryRetries = v));
		rows.add(new ToggleRow(font, "recoveryEdgeCheck", config.recoveryEdgeCheck, v -> config.recoveryEdgeCheck = v));
		rows.add(NumberRow.decimal(font, "recoveryTurnSeconds", config.recoveryTurnSeconds, v -> config.recoveryTurnSeconds = v));
	}
}
