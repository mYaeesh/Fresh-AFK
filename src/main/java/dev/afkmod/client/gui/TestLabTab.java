package dev.afkmod.client.gui;

import dev.afkmod.client.AfkController;
import dev.afkmod.client.gui.Rows.ButtonsRow;
import dev.afkmod.client.gui.Rows.HeaderRow;
import dev.afkmod.client.gui.Rows.TextRow;
import dev.afkmod.client.gui.SettingRows.CycleRow;
import dev.afkmod.client.gui.SettingRows.NumberRow;
import dev.afkmod.client.gui.SettingRows.SettingRow;
import dev.afkmod.client.gui.SettingRows.ToggleRow;
import dev.afkmod.client.gui.SettingRows.Validator;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.gui.FieldValidator;
import dev.afkmod.gui.TooltipText;
import dev.afkmod.logic.MessageSource;
import dev.afkmod.testlab.MessageScenarios;
import dev.afkmod.testlab.Scenario;
import dev.afkmod.testlab.ScenarioInfo;
import dev.afkmod.testlab.TestLab;
import dev.afkmod.testlab.TestResult;
import dev.afkmod.testlab.Verdict;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static dev.afkmod.client.gui.Rows.seg;

/**
 * The Test Lab inside the settings screen. It reuses the existing scenario list, the {@link TestLab} API, the confirm
 * dialogs, the message tester, the results list, Abort, the self-test and the debug report; the only new code is the
 * layout. Every scenario, the self-test and every option field has an info icon. Buttons are disabled (with the reason
 * in a tooltip) when the mod is OFF and the scenario needs it ON, and scenarios that move the player or can disconnect
 * carry a warning tag and ask for confirmation first.
 */
final class TestLabTab extends RowTab {
	private final TestLabActions actions;
	private final int fixedRows;
	private int shownResults = -1;
	private @Nullable TestResult shownLast;

	TestLabTab(TestLabActions actions) {
		super("Lab", "Test Lab");
		this.actions = actions;
		AfkConfig config = SettingRows.config();

		rows.add(new HeaderRow("Test Lab"));
		rows.add(new TextRow(() -> {
			AfkController c = AfkController.get();
			TestLab lab = TestLabActions.lab();
			String running = lab.isRunning() ? "running " + lab.runningLabel() : "idle";
			return List.of(seg(String.format(Locale.ROOT, "Mod: %s  |  Test Lab: %s  |  Overrides: %s", c.machine().state(), running,
					lab.overrides().describe()), Ui.GREY));
		}));
		rows.add(new TextRow(() -> TestLabActions.status().isEmpty() ? List.of()
				: List.of(seg(TestLabActions.status(), TestLabActions.statusColor()))));
		Button abort = Button.builder(Component.literal("Abort test"), b -> actions.abort())
				.tooltip(Tooltip.create(Component.literal("Cancels the running scenario or self-test, clears the overrides and puts the mod back")))
				.bounds(0, 0, 100, Ui.ROW_H).build();
		Button report = Button.builder(Component.literal("Copy debug report"), b -> actions.copyReport())
				.tooltip(Tooltip.create(Component.literal("Copies the debug report to the clipboard and saves " + TestLab.REPORT_FILE)))
				.bounds(0, 0, 100, Ui.ROW_H).build();
		rows.add(ButtonsRow.of(List.of(abort, report)));

		// ---- message tester (A1) and presets (A2) ----
		rows.add(new HeaderRow("Message tester"));
		MessageFieldRow messageRow = new MessageFieldRow(font);
		rows.add(messageRow);
		CycleRow<MessageSource> typeRow = new CycleRow<>("testMessageType", TestLabActions.messageType,
				new MessageSource[]{MessageSource.SYSTEM, MessageSource.ACTION_BAR, MessageSource.TITLE, MessageSource.SUBTITLE, MessageSource.BOSS_BAR},
				s -> Component.literal(s.displayName()), v -> TestLabActions.messageType = v);
		rows.add(typeRow);
		Button explain = Button.builder(Component.literal("Explain"), b -> actions.runA1(false))
				.bounds(0, 0, 100, Ui.ROW_H).build();
		Button send = Button.builder(Component.literal("Send for real"), b -> actions.runA1(true))
				.bounds(0, 0, 100, Ui.ROW_H).build();
		Scenario a1 = TestLabActions.lab().find("A1");
		rows.add(new ButtonsRow(Ui.ROW_H, List.of(explain, send), new double[]{1, 1}, () -> true, () -> {
			explain.setTooltip(Tooltip.create(Component.literal("Evaluates the message without triggering anything (works with the mod OFF)")));
			String why = a1 == null ? null : TestLabActions.disabledReason(a1, true);
			send.active = why == null;
			send.setTooltip(Tooltip.create(Component.literal(why == null ? "Pushes it through the real handler (mod ON only)" : why)));
		}));
		List<Button> presetButtons = new ArrayList<>();
		for (MessageScenarios.Preset preset : MessageScenarios.PRESETS) {
			presetButtons.add(Button.builder(Component.literal(preset.label()), b -> {
						TestLabActions.messageText = preset.text();
						TestLabActions.messageType = preset.source();
						messageRow.setText(preset.text());
						typeRow.setValue(preset.source());
						actions.runA1(false);
					})
					.tooltip(Tooltip.create(Component.literal("\"" + preset.text() + "\" as " + preset.source().displayName() + ", explained")))
					.bounds(0, 0, 60, Ui.ROW_H).build());
		}
		rows.add(new PresetsRow(presetButtons));

		// ---- options ----
		rows.add(new HeaderRow("Options"));
		rows.add(new NumberRow(font, "testStartYaw", TestLabActions.yawText,
				Validator.of(FieldValidator::optionalDecimal, v -> {
				}), t -> TestLabActions.yawText = t));
		rows.add(new NumberRow(font, "testRestartSeconds", TestLabActions.restartSecondsText,
				Validator.of(t -> FieldValidator.wholeNumber(t, "testRestartSeconds"), v -> {
				}), t -> TestLabActions.restartSecondsText = t));
		rows.add(new NumberRow(font, "testTimerSeconds", TestLabActions.timerSecondsText,
				Validator.of(t -> FieldValidator.wholeNumber(t, "testTimerSeconds"), v -> {
				}), t -> TestLabActions.timerSecondsText = t));
		rows.add(new CycleRow<>("testFullTimerEnd", TestLabActions.fullTimerEnd, new Boolean[]{false, true},
				v -> Component.literal(v ? "Full" : "Dry end"), v -> TestLabActions.fullTimerEnd = v));
		rows.add(new ToggleRow(font, "testRealDisconnect", TestLabActions.realDisconnect, v -> TestLabActions.realDisconnect = v));
		rows.add(new ToggleRow(font, "reportRedactIdentity", config.reportRedactIdentity, v -> config.reportRedactIdentity = v));

		// ---- scenarios, grouped as in the spec ----
		Scenario.Group group = null;
		for (Scenario s : TestLabActions.lab().listScenarios()) {
			if (s.group() != group) {
				if (s.group() == Scenario.Group.I) {
					rows.add(new HeaderRow("H. Self-test"));
					rows.add(new ScenarioRow(font, "H1", "Self-test runner", null, "[auto]",
							() -> TooltipText.of(ScenarioInfo.SELF_TEST_DESCRIPTION, "PASS: " + ScenarioInfo.SELF_TEST_PASS),
							() -> TestLabActions.lab().isRunning() ? "A test is running: " + TestLabActions.lab().runningLabel() + ". Abort it first." : null,
							actions::runSelfTest));
				}
				group = s.group();
				rows.add(new HeaderRow(group.title()));
			}
			rows.add(scenarioRow(s));
		}

		rows.add(new HeaderRow("Results (newest first, hover for details)"));
		this.fixedRows = rows.size();
	}

	private ScenarioRow scenarioRow(Scenario s) {
		String tag = ScenarioInfo.tag(s.id());
		String flags = (s.requiresModOn() ? "[mod ON] " : "") + (s.safeForAuto() ? "[auto]" : "");
		Supplier<TooltipText> tip = () -> {
			String warning = ScenarioInfo.warning(s.id());
			String pass = ScenarioInfo.passMeaning(s.id());
			return TooltipText.of(s.description() + (warning == null ? "" : "\nWARNING: " + warning),
					pass == null ? null : "PASS: " + pass);
		};
		return new ScenarioRow(font, s.id(), s.name(), tag, flags.trim(), tip,
				() -> TestLabActions.disabledReason(s, false), () -> actions.runScenario(s));
	}

	@Override
	protected void refreshRows() {
		List<TestResult> results = TestLabActions.lab().getResults();
		TestResult last = results.isEmpty() ? null : results.get(results.size() - 1);
		if (results.size() == shownResults && last == shownLast) return;
		shownResults = results.size();
		shownLast = last;
		while (rows.size() > fixedRows) rows.remove(rows.size() - 1);
		if (results.isEmpty()) {
			rows.add(TextRow.of("(no results yet)", Ui.GREY));
			return;
		}
		for (int i = results.size() - 1; i >= 0; i--) {
			TestResult r = results.get(i);
			rows.add(new TextRow(() -> List.of(seg(r.line(), colorOf(r.verdict()))))
					.withTooltip(() -> TooltipText.plain(r.line().replace(" | ", "\n"))));
		}
	}

	private static int colorOf(Verdict verdict) {
		return switch (verdict) {
			case PASS -> Ui.GREEN;
			case FAIL -> Ui.ERROR;
			case INFO -> Ui.YELLOW;
		};
	}

	/** The A1 message text field (plain text, any content is valid). */
	private static final class MessageFieldRow extends SettingRow {
		private final EditBox field;

		MessageFieldRow(Font font) {
			super("testMessageText");
			this.field = new EditBox(font, 0, 0, 120, Ui.ROW_H, Component.literal(entry.displayName()));
			field.setMaxLength(256);
			field.setHint(Component.literal("e.g. Servers are updating"));
			field.setValue(TestLabActions.messageText);
			field.setResponder(text -> TestLabActions.messageText = text);
		}

		void setText(String text) {
			field.setValue(text);
		}

		@Override
		List<? extends AbstractWidget> controlWidgets() {
			return List.of(field);
		}

		@Override
		void layoutControl(Font font, int cx, int cy, int cw) {
			field.setX(cx);
			field.setY(cy);
			field.setWidth(Math.max(40, cw));
		}
	}

	/** "Presets": the four A2 example messages as buttons, with the A2 info icon. */
	private static final class PresetsRow extends Row {
		private final List<Button> buttons;
		private final InfoIcon icon;
		private final String label = "Presets";
		private int iconRight;

		PresetsRow(List<Button> buttons) {
			this.buttons = buttons;
			Scenario scenario = TestLabActions.lab().find("A2");
			this.icon = new InfoIcon("Presets", () -> presetTip(scenario));
		}

		private static TooltipText presetTip(@Nullable Scenario s) {
			if (s == null) return TooltipText.plain("The example messages from scenario A2.");
			String pass = ScenarioInfo.passMeaning(s.id());
			return TooltipText.of(s.description(), pass == null ? null : "PASS: " + pass);
		}

		@Override
		int height() {
			return Ui.ROW_H + Ui.GAP;
		}

		@Override
		List<AbstractWidget> widgets() {
			List<AbstractWidget> all = new ArrayList<>();
			all.add(icon);
			all.addAll(buttons);
			return all;
		}

		@Override
		void layout(Font font, int x, int y, int w) {
			super.layout(font, x, y, w);
			int labelWidth = Ui.labelWidth(w);
			int iconX = x + font.width(label) + 4;
			iconRight = iconX + InfoIcon.SIZE;
			icon.setX(iconX);
			icon.setY(y + (Ui.ROW_H - InfoIcon.SIZE) / 2);
			int cx = x + labelWidth;
			int available = w - labelWidth - 4 * (buttons.size() - 1);
			int bw = Math.max(20, available / buttons.size());
			for (Button button : buttons) {
				button.setX(cx);
				button.setY(y);
				button.setWidth(bw);
				cx += bw + 4;
			}
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
			graphics.text(font, label, x, y + 5, Ui.TEXT);
		}

		@Override
		@Nullable TooltipRequest tooltipAt(int mouseX, int mouseY) {
			if (mouseX >= x && mouseX < iconRight && mouseY >= y && mouseY < y + Ui.ROW_H) {
				return new TooltipRequest(icon.text(), mouseX, mouseY);
			}
			return null;
		}
	}

	/**
	 * One scenario: a Run button, the label with warning and mod-ON tags, and the info icon (description and what a PASS
	 * means). The button is disabled with the reason in its tooltip when the scenario can't run right now.
	 */
	private static final class ScenarioRow extends Row {
		private static final int RUN_WIDTH = 34;

		private final String label;
		private final @Nullable String tag;
		private final String flags;
		private final Supplier<@Nullable String> disabledReason;
		private final Supplier<TooltipText> tip;
		private final Button run;
		private final InfoIcon icon;
		private @Nullable String reason;
		private String shownLabel = "";
		private int tagX;
		private int iconRight;

		ScenarioRow(Font font, String id, String name, @Nullable String tag, String flags, Supplier<TooltipText> tip,
		            Supplier<@Nullable String> disabledReason, Runnable onRun) {
			this.label = id + " " + name;
			this.tag = tag;
			this.flags = flags;
			this.tip = tip;
			this.disabledReason = disabledReason;
			this.run = Button.builder(Component.literal("Run"), b -> onRun.run()).bounds(0, 0, RUN_WIDTH, Ui.ROW_H).build();
			this.icon = new InfoIcon(label, tip);
		}

		@Override
		void update() {
			reason = disabledReason.get();
			run.active = reason == null;
		}

		@Override
		int height() {
			return Ui.ROW_H + Ui.GAP;
		}

		@Override
		List<AbstractWidget> widgets() {
			return List.of(run, icon);
		}

		@Override
		void layout(Font font, int x, int y, int w) {
			super.layout(font, x, y, w);
			run.setX(x);
			run.setY(y);
			int textX = x + RUN_WIDTH + 6;
			// Leave room for the icon, and for the tag and flags when they fit.
			int room = w - RUN_WIDTH - 6 - InfoIcon.SIZE - 6;
			String extras = (tag == null ? "" : " (!) " + tag) + (flags.isEmpty() ? "" : " " + flags);
			shownLabel = Ui.fit(font, label, Math.max(30, room));
			int labelWidth = font.width(shownLabel);
			tagX = textX + labelWidth;
			int extraRoom = Math.max(0, room - labelWidth);
			int extrasWidth = Math.min(font.width(extras), extraRoom);
			int iconX = textX + labelWidth + extrasWidth + 4;
			iconRight = iconX + InfoIcon.SIZE;
			icon.setX(iconX);
			icon.setY(y + (Ui.ROW_H - InfoIcon.SIZE) / 2);
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
			int textX = x + RUN_WIDTH + 6;
			graphics.text(font, shownLabel, textX, y + 5, reason == null ? Ui.TEXT : Ui.GREY);
			int cx = tagX;
			int limit = icon.getX() - 3;
			if (tag != null) {
				String warn = " (!) " + tag;
				if (cx + font.width(warn) <= limit) {
					graphics.text(font, warn, cx, y + 5, Ui.ORANGE);
					cx += font.width(warn);
				}
			}
			if (!flags.isEmpty()) {
				String f = " " + flags;
				if (cx + font.width(f) <= limit) graphics.text(font, f, cx, y + 5, Ui.DARK_GREY);
			}
		}

		@Override
		@Nullable TooltipRequest tooltipAt(int mouseX, int mouseY) {
			if (mouseY < y || mouseY >= y + Ui.ROW_H) return null;
			// The disabled Run button explains why.
			if (reason != null && mouseX >= run.getX() && mouseX < run.getRight()) {
				return new TooltipRequest(TooltipText.plain(reason), mouseX, mouseY);
			}
			if (mouseX >= x + RUN_WIDTH + 6 && mouseX < iconRight) return new TooltipRequest(tip.get(), mouseX, mouseY);
			return null;
		}
	}
}
