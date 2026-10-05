package dev.afkmod.client.gui;

import dev.afkmod.AfkModClient;
import dev.afkmod.client.AfkController;
import dev.afkmod.client.KeyControl;
import dev.afkmod.client.KeyControl.Action;
import dev.afkmod.client.MovementRecovery;
import dev.afkmod.client.gui.Rows.BarRow;
import dev.afkmod.client.gui.Rows.ButtonsRow;
import dev.afkmod.client.gui.Rows.HeaderRow;
import dev.afkmod.client.gui.Rows.Seg;
import dev.afkmod.client.gui.Rows.TextRow;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.logic.AfkStateMachine;
import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.logic.DurationParser;
import dev.afkmod.logic.HudText;
import dev.afkmod.logic.RecoverySequence;
import dev.afkmod.stats.SessionRecord;
import dev.afkmod.stats.StatsEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.afkmod.client.gui.Rows.seg;

/**
 * The live overview: ON/OFF button, state, timer, restarts, the three keys, movement, session summary and the last 8
 * events. Everything is read from the existing state every frame; the only controls are the ON/OFF button and, while a
 * Test Lab scenario runs, its Abort button.
 */
final class DashboardTab extends RowTab {
	private static final int EVENT_ROWS = 8;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

	private List<StatsEvent> events = List.of();

	DashboardTab() {
		super("Dash", "Dashboard");
		rows.add(new HeaderRow("Dashboard"));

		// A running Test Lab scenario: banner and Abort.
		rows.add(new TextRow(() -> TestLabActions.lab().isRunning()
				? List.of(seg("TEST RUNNING: " + TestLabActions.lab().runningLabel(), Ui.ORANGE)) : List.of()));
		Button abort = Button.builder(Component.literal("Abort test"), b -> TestLabActions.lab().abort())
				.bounds(0, 0, 100, Ui.ROW_H).build();
		rows.add(new ButtonsRow(Ui.ROW_H, List.of(abort), new double[]{1}, () -> TestLabActions.lab().isRunning(), () -> {
		}));

		// The big ON/OFF button.
		Button toggle = Button.builder(Component.empty(), b -> AfkController.get().toggleFromGui(Minecraft.getInstance()))
				.bounds(0, 0, 100, 24).build();
		rows.add(new ButtonsRow(24, List.of(toggle), new double[]{1}, () -> true, () -> {
			boolean on = machine().isOn();
			boolean inWorld = Minecraft.getInstance().player != null;
			toggle.active = on || inWorld;
			toggle.setMessage(on
					? Component.literal("AFK is ON - click to turn OFF").withStyle(ChatFormatting.GREEN)
					: Component.literal(inWorld ? "AFK is OFF - click to turn ON" : "AFK is OFF (join a world first)")
							.withStyle(ChatFormatting.RED));
		}));

		// State with its coloured dot.
		rows.add(new TextRow(() -> {
			State state = machine().state();
			List<Seg> line = new ArrayList<>();
			line.add(seg("● ", HudText.dotColor(state)));
			line.add(seg(HudText.label(state), state == State.OFF ? Ui.WHITE : HudText.labelColor(state)));
			if (state == State.OFF && machine().lastReason() != null) {
				line.add(seg("  (last: " + machine().lastReason().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ")", Ui.GREY));
			}
			return line;
		}));

		// Timer: remaining time and a progress bar.
		rows.add(new TextRow(DashboardTab::timerLine));
		rows.add(new BarRow(DashboardTab::timerFraction, DashboardTab::timerBarLabel,
				() -> machine().isTimerPaused() ? Ui.YELLOW : Ui.GREEN));

		// Restarts.
		rows.add(new TextRow(() -> {
			AfkStateMachine m = machine();
			String current = m.isInRestart() ? "current restart " + DurationParser.format(m.restartElapsedSeconds()) + " elapsed" : "no restart in progress";
			return List.of(seg("Restarts this session: " + m.restartCount(), Ui.TEXT), seg("  |  " + current, m.isInRestart() ? Ui.ERROR : Ui.GREY));
		}));

		// The three keys.
		rows.add(new TextRow(() -> List.of(seg("Keys:  ", Ui.TEXT), seg("Crouch ", Ui.TEXT), key(Action.CROUCH),
				seg("   Left click ", Ui.TEXT), key(Action.ATTACK), seg("   Right click ", Ui.TEXT), key(Action.USE))));

		// Movement.
		rows.add(new TextRow(DashboardTab::movementLine));
		rows.add(new TextRow(() -> {
			RecoverySequence.Outcome outcome = recovery().lastOutcome();
			return List.of(seg("Last recovery result: ", Ui.TEXT),
					outcome == null ? seg("none yet", Ui.GREY) : seg(outcome.name(), outcome.isSuccess() ? Ui.GREEN : Ui.ERROR));
		}));
		rows.add(new TextRow(() -> {
			MovementRecovery.YawTurn turn = recovery().lastYawTurn();
			return List.of(seg("Last yaw snap: ", Ui.TEXT), turn == null ? seg("none yet", Ui.GREY)
					: seg(String.format(Locale.ROOT, "%s (%.1f deg, %.2f s)", turn.direction(), turn.toYaw(), turn.seconds()), Ui.TEXT));
		}));

		// This session.
		rows.add(new HeaderRow("This session"));
		rows.add(new TextRow(() -> {
			SessionRecord s = AfkController.get().stats().getCurrentSession();
			if (s == null) return List.of(seg("No session: AFK is OFF", Ui.GREY));
			return List.of(seg("Active " + dur(s.activeMs) + "  |  restarts " + s.restarts + "  |  blocks mined " + s.blocksMined, Ui.TEXT));
		}));
		rows.add(new TextRow(() -> {
			SessionRecord s = AfkController.get().stats().getCurrentSession();
			if (s == null) return List.of();
			return List.of(seg("Stuck " + s.stuckDetections + "  |  recovery attempts " + s.recoveryAttempts + " (ok " + s.recoverySuccesses
					+ ", failed " + s.recoveryFailures + ")", Ui.TEXT));
		}));

		// The last 8 events, newest first.
		rows.add(new HeaderRow("Last " + EVENT_ROWS + " events"));
		for (int i = 0; i < EVENT_ROWS; i++) {
			int index = i;
			rows.add(new TextRow(() -> eventLine(index)));
		}
	}

	@Override
	protected void refreshRows() {
		events = AfkController.get().stats().getRecentEvents(EVENT_ROWS);
	}

	private List<Seg> eventLine(int newestFirstIndex) {
		int i = events.size() - 1 - newestFirstIndex;
		if (i < 0) return newestFirstIndex == 0 ? List.of(seg("(no events yet)", Ui.GREY)) : List.of();
		StatsEvent e = events.get(i);
		List<Seg> line = new ArrayList<>();
		line.add(seg(TIME.format(java.time.Instant.ofEpochMilli(e.timeMillis())) + " ", Ui.GREY));
		if (e.test()) line.add(seg("[TEST] ", Ui.ORANGE));
		line.add(seg(e.type().name().toLowerCase(Locale.ROOT).replace('_', ' '), Ui.TEXT));
		if (!e.text().isBlank()) line.add(seg("  " + e.text(), Ui.GREY));
		return line;
	}

	private static AfkStateMachine machine() {
		return AfkController.get().machine();
	}

	private static MovementRecovery recovery() {
		return AfkController.get().recovery();
	}

	private static String dur(long ms) {
		return DurationParser.format(ms / 1000);
	}

	private static List<Seg> timerLine() {
		AfkStateMachine m = machine();
		AfkConfig config = AfkModClient.config();
		if (!m.isOn() && !m.hasTimer()) {
			return List.of(seg(config.timerSeconds > 0
					? "Timer: " + DurationParser.format(config.timerSeconds) + " (starts when AFK turns ON)" : "Timer: none", Ui.TEXT));
		}
		if (!m.hasTimer()) return List.of(seg("Timer: none", Ui.TEXT));
		return List.of(seg("Timer: " + DurationParser.format(m.timerRemainingSeconds()) + " remaining", Ui.TEXT),
				seg(m.isTimerPaused() ? "  (paused)" : "", Ui.YELLOW));
	}

	private static double timerFraction() {
		AfkStateMachine m = machine();
		if (!m.hasTimer()) return 0;
		long total = AfkModClient.config().timerSeconds;
		long remaining = m.timerRemainingSeconds();
		return total <= 0 ? 1.0 : (double) remaining / total;
	}

	private static String timerBarLabel() {
		AfkStateMachine m = machine();
		if (!m.hasTimer()) return "no timer";
		return DurationParser.format(m.timerRemainingSeconds()) + (m.isTimerPaused() ? " paused" : "");
	}

	private static Seg key(Action action) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return seg("-", Ui.GREY);
		boolean active = KeyControl.isActive(mc, action);
		return seg("●", active ? Ui.GREEN : machine().isOn() ? Ui.ERROR : Ui.GREY);
	}

	private static List<Seg> movementLine() {
		AfkConfig config = AfkModClient.config();
		State state = machine().state();
		if (!config.movementCheckEnabled) return List.of(seg("Movement: stuck detection is off", Ui.GREY));
		if (state != State.ACTIVE && state != State.RECOVERING) return List.of(seg("Movement: not tracked while " + HudText.label(state).toLowerCase(Locale.ROOT), Ui.GREY));
		var detector = recovery().detector();
		double distance = detector.maxDistanceFromCurrent();
		boolean full = detector.isWindowFull(config.stuckWindowSeconds);
		boolean stuck = full && distance < config.stuckDistanceBlocks;
		return List.of(seg(String.format(Locale.ROOT, "Moved %.1f blocks in the last %d s (stuck below %s) - %s", distance,
				config.stuckWindowSeconds, SettingRows.num(config.stuckDistanceBlocks), full ? "window full" : "collecting"),
				stuck ? Ui.ORANGE : Ui.TEXT));
	}
}
