package dev.afkmod.client.gui;

import dev.afkmod.AfkModClient;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.config.SettingInfo;
import dev.afkmod.gui.FieldValidator;
import dev.afkmod.gui.TooltipText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The setting rows: a label with its info icon on the left and the control on the right (text field, number field,
 * checkbox, cycle button or slider), with an inline red error line when the input is invalid. The label, the icon and
 * the tooltip all come from {@link SettingInfo}; hovering the label or the icon (or focusing the icon with the keyboard)
 * shows the same tooltip. Invalid input never reaches the config.
 */
final class SettingRows {
	private SettingRows() {
	}

	/** A number as it should appear in a field: no exponent, no trailing zeros. */
	static String num(double value) {
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	/** Checks the text and, when {@code apply} is true and it is valid, applies it. Returns an error message or null. */
	@FunctionalInterface
	interface Validator {
		@Nullable String check(String text, boolean apply);

		/** Parses with {@code parser} and hands a valid value to {@code onValid}. */
		static <T> Validator of(Function<String, FieldValidator.Parsed<T>> parser, Consumer<T> onValid) {
			return (text, apply) -> {
				FieldValidator.Parsed<T> parsed = parser.apply(text);
				if (!parsed.ok()) return parsed.error();
				if (apply) onValid.accept(parsed.value());
				return null;
			};
		}
	}

	/** Label + info icon + control. Subclasses supply the control. */
	abstract static class SettingRow extends Row {
		protected final String key;
		protected final SettingInfo.Entry entry;
		protected final InfoIcon icon;
		protected @Nullable String error;
		private String labelText = "";
		private int labelWidth;
		private int iconRight;

		SettingRow(String key) {
			this.key = key;
			this.entry = SettingInfo.get(key);
			this.icon = new InfoIcon(entry.displayName(), () -> TooltipText.forSetting(key));
		}

		/** The control's own widgets (the icon is added by this class). */
		abstract List<? extends AbstractWidget> controlWidgets();

		abstract void layoutControl(Font font, int cx, int cy, int cw);

		int controlHeight() {
			return Ui.ROW_H;
		}

		@Override
		int height() {
			return controlHeight() + (error != null ? Ui.ERROR_H : 0) + Ui.GAP;
		}

		@Override
		List<AbstractWidget> widgets() {
			List<AbstractWidget> all = new ArrayList<>();
			all.add(icon);
			all.addAll(controlWidgets());
			return all;
		}

		@Override
		void layout(Font font, int x, int y, int w) {
			super.layout(font, x, y, w);
			labelWidth = Ui.labelWidth(w);
			labelText = Ui.fit(font, entry.displayName(), labelWidth - InfoIcon.SIZE - 6);
			int iconX = x + font.width(labelText) + 4;
			iconRight = iconX + InfoIcon.SIZE;
			icon.setX(iconX);
			icon.setY(y + (Ui.ROW_H - InfoIcon.SIZE) / 2);
			layoutControl(font, x + labelWidth, y, w - labelWidth);
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
			graphics.text(font, labelText, x, y + 5, Ui.TEXT);
			if (error != null) {
				graphics.text(font, Ui.fit(font, error, w - labelWidth), x + labelWidth, y + controlHeight() + 1, Ui.ERROR);
			}
		}

		@Override
		@Nullable TooltipRequest tooltipAt(int mouseX, int mouseY) {
			if (mouseX >= x && mouseX < iconRight && mouseY >= y && mouseY < y + Ui.ROW_H) {
				return new TooltipRequest(TooltipText.forSetting(key), mouseX, mouseY);
			}
			return null;
		}
	}

	/** An on/off setting as a vanilla checkbox. */
	static final class ToggleRow extends SettingRow {
		private final Checkbox box;

		ToggleRow(Font font, String key, boolean initial, Consumer<Boolean> onChange) {
			super(key);
			this.box = Checkbox.builder(Component.empty(), font).pos(0, 0).selected(initial)
					.onValueChange((checkbox, value) -> onChange.accept(value)).build();
		}

		@Override
		List<? extends AbstractWidget> controlWidgets() {
			return List.of(box);
		}

		@Override
		void layoutControl(Font font, int cx, int cy, int cw) {
			box.setX(cx);
			box.setY(cy);
		}
	}

	/** A number field: red text and an inline message when the value is not a number inside the registered range. */
	static final class NumberRow extends SettingRow {
		private final EditBox field;

		NumberRow(Font font, String key, String initial, Validator validator, Consumer<String> keepText) {
			super(key);
			this.field = new EditBox(font, 0, 0, 64, Ui.ROW_H, Component.literal(entry.displayName()));
			field.setMaxLength(14);
			field.setValue(initial);
			Runnable check = () -> {
				String text = field.getValue();
				keepText.accept(text);
				this.error = validator.check(text, true);
				field.setTextColor(error == null ? Ui.TEXT : Ui.ERROR);
			};
			field.setResponder(text -> check.run());
			// Show an error for a restored invalid text, without applying anything yet.
			this.error = validator.check(initial, false);
			field.setTextColor(error == null ? Ui.TEXT : Ui.ERROR);
		}

		/** A whole number from the registry's range, applied to the saved config. */
		static NumberRow whole(Font font, String key, int initial, Consumer<Integer> apply) {
			return new NumberRow(font, key, Integer.toString(initial),
					Validator.of(text -> FieldValidator.wholeNumber(text, key), apply), t -> {
					});
		}

		/** A decimal number from the registry's range, applied to the saved config. */
		static NumberRow decimal(Font font, String key, double initial, Consumer<Double> apply) {
			return new NumberRow(font, key, num(initial),
					Validator.of(text -> FieldValidator.decimal(text, key), apply), t -> {
					});
		}

		@Override
		List<? extends AbstractWidget> controlWidgets() {
			return List.of(field);
		}

		@Override
		void layoutControl(Font font, int cx, int cy, int cw) {
			field.setX(cx);
			field.setY(cy);
			field.setWidth(Math.max(24, Math.min(64, cw - 40)));
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
			super.draw(graphics, font, mouseX, mouseY);
			if (!entry.unit().isEmpty() && field.visible) {
				graphics.text(font, entry.unit(), field.getX() + field.getWidth() + 4, y + 5, Ui.GREY);
			}
		}
	}

	/** A text field for a comma-separated word list. Required lists must keep at least one word. */
	static final class WordsRow extends SettingRow {
		private final EditBox field;

		WordsRow(Font font, String key, boolean required, String initial, Consumer<String> keepText,
		         Consumer<List<String>> apply) {
			super(key);
			this.field = new EditBox(font, 0, 0, 120, Ui.ROW_H, Component.literal(entry.displayName()));
			field.setMaxLength(200);
			field.setValue(initial);
			Validator validator = Validator.of(text -> FieldValidator.words(text, required), apply);
			field.setResponder(text -> {
				keepText.accept(text);
				this.error = validator.check(text, true);
				field.setTextColor(error == null ? Ui.TEXT : Ui.ERROR);
			});
			this.error = validator.check(initial, false);
			field.setTextColor(error == null ? Ui.TEXT : Ui.ERROR);
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

	/** A cycle button over a fixed list of values. */
	static final class CycleRow<T> extends SettingRow {
		private final CycleButton<T> button;

		CycleRow(String key, T initial, T[] values, Function<T, Component> name, Consumer<T> onChange) {
			super(key);
			this.button = CycleButton.<T>builder(name, initial).withValues(values).displayOnlyValue()
					.create(0, 0, 120, Ui.ROW_H, Component.literal(entry.displayName()), (b, value) -> onChange.accept(value));
		}

		/** Selects a value from code (e.g. a preset button). */
		void setValue(T value) {
			button.setValue(value);
		}

		@Override
		List<? extends AbstractWidget> controlWidgets() {
			return List.of(button);
		}

		@Override
		void layoutControl(Font font, int cx, int cy, int cw) {
			button.setX(cx);
			button.setY(cy);
			button.setWidth(Math.max(40, cw));
		}
	}

	/** The HUD scale slider, in 0.05 steps inside the registered range. */
	static final class SliderRow extends SettingRow {
		private final ScaleSlider slider;

		SliderRow(String key, Consumer<Float> apply) {
			super(key);
			this.slider = new ScaleSlider(entry, apply);
		}

		@Override
		List<? extends AbstractWidget> controlWidgets() {
			return List.of(slider);
		}

		@Override
		void layoutControl(Font font, int cx, int cy, int cw) {
			slider.setX(cx);
			slider.setY(cy);
			slider.setWidth(Math.max(40, cw));
		}
	}

	private static final class ScaleSlider extends AbstractSliderButton {
		private static final float STEP = 0.05f;
		private final float min;
		private final float max;
		private final Consumer<Float> apply;
		private float current;

		ScaleSlider(SettingInfo.Entry entry, Consumer<Float> apply) {
			super(0, 0, 120, Ui.ROW_H, Component.empty(),
					(AfkModClient.savedConfig().hudScale - entry.min().floatValue()) / (entry.max().floatValue() - entry.min().floatValue()));
			this.min = entry.min().floatValue();
			this.max = entry.max().floatValue();
			this.apply = apply;
			this.current = AfkModClient.savedConfig().hudScale;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(String.format(Locale.ROOT, "%.2fx", current)));
		}

		@Override
		protected void applyValue() {
			float raw = min + (float) this.value * (max - min);
			current = Math.clamp(Math.round(raw / STEP) * STEP, min, max);
			apply.accept(current);
		}
	}

	/** Convenience: the saved config the GUI edits. */
	static AfkConfig config() {
		return AfkModClient.savedConfig();
	}
}
