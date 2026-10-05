package dev.afkmod.client.gui;

import dev.afkmod.config.TimerPreset;
import dev.afkmod.logic.DurationParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The timer preset rows of the Timer tab: one editable row per preset, and a row that adds a new one. */
final class PresetRows {
	private static final int GAP = 4;

	private PresetRows() {
	}

	/**
	 * One preset: name and length are text boxes that write into the preset as you type (an invalid entry shows a red
	 * message and leaves the preset as it was), Use applies the length to the timer, and X deletes the preset.
	 */
	static final class PresetRow extends Row {
		private final EditBox nameBox;
		private final EditBox lengthBox;
		private final Button useButton;
		private final Button deleteButton;
		private @Nullable String error;

		PresetRow(Font font, TimerPreset preset, Runnable onUse, Runnable onDelete) {
			this.nameBox = new EditBox(font, 0, 0, 80, Ui.ROW_H, Component.literal("Preset name"));
			nameBox.setMaxLength(TimerPreset.NAME_MAX);
			nameBox.setValue(preset.name);
			nameBox.setResponder(text -> {
				if (!text.isBlank()) preset.name = text.trim();
				error = problem();
			});
			this.lengthBox = new EditBox(font, 0, 0, 60, Ui.ROW_H, Component.literal("Preset length"));
			lengthBox.setMaxLength(16);
			lengthBox.setValue(DurationParser.formatUnits(preset.seconds));
			lengthBox.setResponder(text -> {
				try {
					long seconds = DurationParser.parse(text);
					if (seconds > 0) preset.seconds = seconds;
				} catch (DurationParser.DurationParseException e) {
					// Reported by problem() below; the preset keeps its last valid length.
				}
				error = problem();
			});
			this.useButton = Button.builder(Component.literal("Use"), b -> onUse.run()).bounds(0, 0, 36, Ui.ROW_H).build();
			this.deleteButton = Button.builder(Component.literal("X"), b -> onDelete.run()).bounds(0, 0, 20, Ui.ROW_H).build();
		}

		/** The first problem with the two boxes, or null. */
		private @Nullable String problem() {
			if (nameBox.getValue().isBlank()) return "The name can't be empty";
			try {
				return DurationParser.parse(lengthBox.getValue()) <= 0 ? "The length must be above 0" : null;
			} catch (DurationParser.DurationParseException e) {
				return e.getMessage();
			}
		}

		@Override
		int height() {
			return Ui.ROW_H + Ui.GAP + (error != null ? Ui.ERROR_H : 0);
		}

		@Override
		List<AbstractWidget> widgets() {
			return List.of(nameBox, lengthBox, useButton, deleteButton);
		}

		@Override
		void update() {
			nameBox.setTextColor(nameBox.getValue().isBlank() ? Ui.ERROR : Ui.TEXT);
			lengthBox.setTextColor(error != null && !nameBox.getValue().isBlank() ? Ui.ERROR : Ui.TEXT);
			useButton.active = error == null;
		}

		@Override
		void layout(Font font, int x, int y, int w) {
			super.layout(font, x, y, w);
			int fixed = useButton.getWidth() + deleteButton.getWidth() + 3 * GAP;
			int free = Math.max(60, w - fixed);
			int nameW = free * 55 / 100;
			int lengthW = free - nameW;
			place(nameBox, x, y, nameW);
			place(lengthBox, x + nameW + GAP, y, lengthW);
			place(useButton, x + nameW + lengthW + 2 * GAP, y, useButton.getWidth());
			place(deleteButton, x + w - deleteButton.getWidth(), y, deleteButton.getWidth());
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
			if (error != null) graphics.text(font, Ui.fit(font, error, w), x, y + Ui.ROW_H + 1, Ui.ERROR);
		}
	}

	/** A name box and an "Add preset" button; the length comes from the hour/minute/second fields above. */
	static final class AddPresetRow extends Row {
		private final EditBox nameBox;
		private final Button addButton;

		/** {@code onAdd} gets the typed name when the button is pressed. */
		AddPresetRow(Font font, Supplier<String> initialName, Consumer<String> keepName, Consumer<String> onAdd) {
			this.nameBox = new EditBox(font, 0, 0, 80, Ui.ROW_H, Component.literal("New preset name"));
			nameBox.setMaxLength(TimerPreset.NAME_MAX);
			nameBox.setHint(Component.literal("Name for the length above"));
			nameBox.setValue(initialName.get());
			nameBox.setResponder(keepName);
			this.addButton = Button.builder(Component.literal("Add preset"), b -> onAdd.accept(nameBox.getValue()))
					.bounds(0, 0, 80, Ui.ROW_H).build();
		}

		@Override
		int height() {
			return Ui.ROW_H + Ui.GAP;
		}

		@Override
		List<AbstractWidget> widgets() {
			return List.of(nameBox, addButton);
		}

		@Override
		void layout(Font font, int x, int y, int w) {
			super.layout(font, x, y, w);
			int buttonW = Math.min(90, Math.max(60, w / 3));
			place(nameBox, x, y, w - buttonW - GAP);
			place(addButton, x + w - buttonW, y, buttonW);
		}
	}

	private static void place(AbstractWidget widget, int x, int y, int width) {
		widget.setX(x);
		widget.setY(y);
		widget.setWidth(Math.max(10, width));
	}
}
