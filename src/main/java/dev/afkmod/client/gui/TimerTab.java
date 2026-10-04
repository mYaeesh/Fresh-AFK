package dev.afkmod.client.gui;

import dev.afkmod.AfkModClient;
import dev.afkmod.client.AfkController;
import dev.afkmod.client.gui.Rows.ButtonsRow;
import dev.afkmod.client.gui.Rows.HeaderRow;
import dev.afkmod.client.gui.Rows.TextRow;
import dev.afkmod.client.gui.SettingRows.SettingRow;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.gui.FieldValidator;
import dev.afkmod.logic.AfkStateMachine;
import dev.afkmod.logic.DurationParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static dev.afkmod.client.gui.Rows.seg;

/**
 * The timer: hour, minute and second fields with "Set timer" and "No timer". Setting it goes through the state machine
 * like before, so a running countdown restarts from the new value. Invalid input shows a red message under the fields.
 */
final class TimerTab extends RowTab {
	/** What was typed, so it survives the screen being rebuilt (e.g. after a confirm dialog). */
	private static String hours = "";
	private static String minutes = "";
	private static String seconds = "";
	private static String message = "";
	private static int messageColor = Ui.GREEN;

	/** Called when the settings screen is opened fresh: start from the current timer. */
	static void resetTyped() {
		long total = AfkModClient.savedConfig().timerSeconds;
		hours = total >= 3600 ? Long.toString(total / 3600) : "";
		minutes = total % 3600 >= 60 ? Long.toString(total % 3600 / 60) : "";
		seconds = total % 60 > 0 ? Long.toString(total % 60) : "";
		message = "";
	}

	private final TimerFieldsRow fields;

	TimerTab() {
		super("Timer", "Timer");
		rows.add(new HeaderRow("Timer"));
		rows.add(new TextRow(() -> {
			AfkStateMachine m = AfkController.get().machine();
			long saved = AfkModClient.savedConfig().timerSeconds;
			if (m.isOn() && m.hasTimer()) {
				return List.of(seg("Running: " + DurationParser.format(m.timerRemainingSeconds()) + " remaining"
						+ (m.isTimerPaused() ? " (paused)" : ""), Ui.TEXT));
			}
			return List.of(seg(saved > 0 ? "Set to " + DurationParser.format(saved) + (m.isOn() ? "" : " (starts when AFK turns ON)")
					: "No timer", Ui.TEXT));
		}));
		this.fields = new TimerFieldsRow(font);
		rows.add(fields);
		Button set = Button.builder(Component.literal("Set timer"), b -> setTimer()).bounds(0, 0, 100, Ui.ROW_H).build();
		Button none = Button.builder(Component.literal("No timer"), b -> clearTimer()).bounds(0, 0, 100, Ui.ROW_H).build();
		rows.add(ButtonsRow.of(List.of(set, none)));
		rows.add(new TextRow(() -> message.isEmpty() ? List.of() : List.of(seg(message, messageColor))));
	}

	private void setTimer() {
		String problem = fields.problem();
		if (problem != null) {
			show(problem, Ui.ERROR);
			return;
		}
		long total = DurationParser.fromFields(hours, minutes, seconds);
		if (total <= 0) {
			show("Enter a duration above 0, or press No timer", Ui.ERROR);
			return;
		}
		apply(total);
		show("Timer set to " + DurationParser.format(total), Ui.GREEN);
	}

	private void clearTimer() {
		hours = "";
		minutes = "";
		seconds = "";
		fields.setTexts("", "", "");
		apply(0);
		show("No timer", Ui.GREEN);
	}

	/** Goes through the state machine so a running countdown restarts from the new value. */
	private static void apply(long totalSeconds) {
		AfkConfig saved = AfkModClient.savedConfig();
		// The saved value is written too, in case a Test Lab override makes the machine's config a temporary copy.
		saved.timerSeconds = totalSeconds;
		AfkController.get().machine().setTimerSeconds(totalSeconds);
	}

	private static void show(String text, int color) {
		message = text;
		messageColor = color;
	}

	/** The label with its info icon and the three number fields. */
	private static final class TimerFieldsRow extends SettingRow {
		private final EditBox hoursBox;
		private final EditBox minutesBox;
		private final EditBox secondsBox;

		TimerFieldsRow(Font font) {
			super("timerSeconds");
			this.hoursBox = box(font, "Hours", hours, t -> hours = t);
			this.minutesBox = box(font, "Minutes", minutes, t -> minutes = t);
			this.secondsBox = box(font, "Seconds", seconds, t -> seconds = t);
			this.error = problem();
		}

		private EditBox box(Font font, String name, String initial, java.util.function.Consumer<String> keep) {
			EditBox box = new EditBox(font, 0, 0, 40, Ui.ROW_H, Component.literal(name));
			box.setMaxLength(6);
			box.setValue(initial);
			box.setResponder(text -> {
				keep.accept(text);
				this.error = problem();
				recolor();
			});
			return box;
		}

		void setTexts(String h, String m, String s) {
			hoursBox.setValue(h);
			minutesBox.setValue(m);
			secondsBox.setValue(s);
		}

		/** The first problem with the three fields, or null when they make a valid duration. */
		@Nullable String problem() {
			FieldValidator.Parsed<Long> h = FieldValidator.timePart(hours, "Hours", 100);
			if (!h.ok()) return h.error();
			FieldValidator.Parsed<Long> m = FieldValidator.timePart(minutes, "Minutes", 6000);
			if (!m.ok()) return m.error();
			FieldValidator.Parsed<Long> s = FieldValidator.timePart(seconds, "Seconds", 360000);
			if (!s.ok()) return s.error();
			try {
				DurationParser.fromFields(hours, minutes, seconds);
				return null;
			} catch (DurationParser.DurationParseException e) {
				return e.getMessage();
			}
		}

		private void recolor() {
			int color = error == null ? Ui.TEXT : Ui.ERROR;
			hoursBox.setTextColor(color);
			minutesBox.setTextColor(color);
			secondsBox.setTextColor(color);
		}

		@Override
		List<? extends AbstractWidget> controlWidgets() {
			return List.of(hoursBox, minutesBox, secondsBox);
		}

		@Override
		void layoutControl(Font font, int cx, int cy, int cw) {
			int letters = font.width("h") + font.width("m") + font.width("s") + 3 * 4;
			int boxWidth = Math.max(22, Math.min(44, (cw - letters - 2 * 6) / 3));
			int x = cx;
			for (EditBox box : new EditBox[]{hoursBox, minutesBox, secondsBox}) {
				box.setX(x);
				box.setY(cy);
				box.setWidth(boxWidth);
				x += boxWidth + 4 + font.width("m") + 6;
			}
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
			super.draw(graphics, font, mouseX, mouseY);
			if (!hoursBox.visible) return;
			graphics.text(font, "h", hoursBox.getX() + hoursBox.getWidth() + 3, y + 5, Ui.GREY);
			graphics.text(font, "m", minutesBox.getX() + minutesBox.getWidth() + 3, y + 5, Ui.GREY);
			graphics.text(font, "s", secondsBox.getX() + secondsBox.getWidth() + 3, y + 5, Ui.GREY);
		}
	}
}
