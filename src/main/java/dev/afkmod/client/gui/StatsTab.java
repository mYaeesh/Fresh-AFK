package dev.afkmod.client.gui;

import dev.afkmod.client.AfkController;
import dev.afkmod.client.gui.Rows.ButtonsRow;
import dev.afkmod.client.gui.Rows.HeaderRow;
import dev.afkmod.client.gui.Rows.Seg;
import dev.afkmod.client.gui.Rows.TextRow;
import dev.afkmod.gui.TooltipText;
import dev.afkmod.logic.DurationParser;
import dev.afkmod.stats.AfkStats;
import dev.afkmod.stats.LifetimeStats;
import dev.afkmod.stats.SessionRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.afkmod.client.gui.Rows.seg;

/**
 * The current session, the lifetime totals, the last 20 sessions (scroll the tab) and "Reset lifetime stats" with a
 * confirmation. Read-only otherwise: everything comes from {@link AfkStats}.
 */
final class StatsTab extends RowTab {
	private static final int SESSION_ROWS = 20;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

	private List<SessionRecord> recent = List.of();

	StatsTab(AfkMenuScreen screen) {
		super("Stats", "Stats");
		rows.add(new HeaderRow("Stats"));

		rows.add(new HeaderRow("Current session"));
		rows.add(new TextRow(() -> {
			SessionRecord s = stats().getCurrentSession();
			if (s == null) return List.of(seg("No session running (AFK is OFF)", Ui.GREY));
			return List.of(seg("Wall " + dur(s.wallMs) + "  |  active " + dur(s.activeMs) + "  |  restart time " + dur(s.restartMs), Ui.TEXT));
		}));
		rows.add(new TextRow(() -> {
			SessionRecord s = stats().getCurrentSession();
			if (s == null) return List.of();
			return List.of(seg("Restarts " + s.restarts + " (longest " + dur(s.longestRestartMs) + ", avg " + dur(s.averageRestartMs())
					+ ")  |  blocks mined " + s.blocksMined, Ui.TEXT));
		}));
		rows.add(new TextRow(() -> {
			SessionRecord s = stats().getCurrentSession();
			if (s == null) return List.of();
			return List.of(seg("Stuck " + s.stuckDetections + "  |  attempts " + s.recoveryAttempts + "  |  ok " + s.recoverySuccesses
					+ "  |  failed " + s.recoveryFailures, Ui.TEXT));
		}));

		rows.add(new HeaderRow("Lifetime"));
		rows.add(new TextRow(() -> {
			LifetimeStats l = stats().getLifetime();
			return List.of(seg(l.sessions + " sessions  |  wall " + dur(l.wallMs) + "  |  active " + dur(l.activeMs), Ui.TEXT));
		}));
		rows.add(new TextRow(() -> {
			LifetimeStats l = stats().getLifetime();
			return List.of(seg("Restarts " + l.restarts + " (longest " + dur(l.longestRestartMs) + ", avg " + dur(l.averageRestartMs())
					+ ")  |  restart time " + dur(l.restartMs), Ui.TEXT));
		}));
		rows.add(new TextRow(() -> {
			LifetimeStats l = stats().getLifetime();
			return List.of(seg("Stuck " + l.stuckDetections + "  |  attempts " + l.recoveryAttempts + "  |  ok " + l.recoverySuccesses
					+ "  |  failed " + l.recoveryFailures + "  |  logouts " + l.logouts, Ui.TEXT));
		}));
		rows.add(new TextRow(() -> List.of(seg("Blocks mined " + stats().getLifetime().blocksMined, Ui.TEXT), seg("  (approximate)", Ui.GREY)))
				.withTooltip(() -> TooltipText.plain("Blocks are counted when the client breaks them, so a block the server rolls "
						+ "back and that is broken again can be counted twice.")));
		rows.add(new TextRow(() -> {
			Map<String, Integer> reasons = stats().getLifetime().endReasons;
			if (reasons == null || reasons.isEmpty()) return List.of(seg("Ended by: nothing yet", Ui.GREY));
			StringBuilder sb = new StringBuilder("Ended by: ");
			reasons.forEach((reason, count) -> sb.append(pretty(reason)).append(' ').append(count).append("  "));
			return List.of(seg(sb.toString().trim(), Ui.GREY));
		}));

		Button reset = Button.builder(Component.literal("Reset lifetime stats"), b -> confirmReset(screen))
				.tooltip(Tooltip.create(Component.literal("Clears the lifetime totals and the session list (asks first)")))
				.bounds(0, 0, 140, Ui.ROW_H).build();
		rows.add(new ButtonsRow(Ui.ROW_H, List.of(reset), new double[]{1}, () -> true, () -> {
		}));

		rows.add(new HeaderRow("Last " + SESSION_ROWS + " sessions (newest first)"));
		rows.add(new TextRow(() -> recent.isEmpty() ? List.of(seg("(no finished sessions yet)", Ui.GREY)) : List.of()));
		for (int i = 0; i < SESSION_ROWS; i++) {
			int index = i;
			TextRow row = new TextRow(() -> sessionLine(index));
			row.withTooltip(() -> sessionTooltip(index));
			rows.add(row);
		}
	}

	@Override
	protected void refreshRows() {
		recent = stats().getRecentSessions();
	}

	private static AfkStats stats() {
		return AfkController.get().stats();
	}

	private static String dur(long ms) {
		return DurationParser.format(ms / 1000);
	}

	private static String pretty(String reason) {
		return reason.toLowerCase(Locale.ROOT).replace('_', ' ');
	}

	private List<Seg> sessionLine(int i) {
		if (i >= recent.size()) return List.of();
		SessionRecord s = recent.get(i);
		String reason = s.endReason == null ? "running" : pretty(s.endReason.name());
		return List.of(seg(DATE.format(Instant.ofEpochMilli(s.startEpochMs)) + "  ", Ui.GREY),
				seg(dur(s.wallMs) + " (active " + dur(s.activeMs) + ")  " + s.restarts + " restarts  " + s.blocksMined + " blocks  ", Ui.TEXT),
				seg(reason, Ui.GREY));
	}

	private TooltipText sessionTooltip(int i) {
		if (i >= recent.size()) return TooltipText.plain("");
		SessionRecord s = recent.get(i);
		return TooltipText.plain("Started " + DATE.format(Instant.ofEpochMilli(s.startEpochMs)) + "\nWall " + dur(s.wallMs) + ", active "
				+ dur(s.activeMs) + ", restart time " + dur(s.restartMs) + "\nRestarts " + s.restarts + " (longest " + dur(s.longestRestartMs)
				+ ")\nStuck " + s.stuckDetections + ", attempts " + s.recoveryAttempts + ", ok " + s.recoverySuccesses + ", failed "
				+ s.recoveryFailures + "\nBlocks mined " + s.blocksMined + "\nEnded: " + (s.endReason == null ? "running" : pretty(s.endReason.name()))
				+ (s.endedInLogout ? " (the mod logged out)" : ""));
	}

	private static void confirmReset(AfkMenuScreen screen) {
		Minecraft mc = Minecraft.getInstance();
		mc.setScreen(new ConfirmScreen(yes -> {
			if (yes) stats().resetLifetime();
			mc.setScreen(screen);
		}, Component.literal("Reset lifetime stats?"),
				Component.literal("This clears the lifetime totals and the list of past sessions. The running session is not affected. "
						+ "It cannot be undone.")));
	}
}
