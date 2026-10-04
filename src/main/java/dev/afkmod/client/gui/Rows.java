package dev.afkmod.client.gui;

import dev.afkmod.gui.TooltipText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** The plain (non-setting) rows: headings, live text, progress bars and button rows. */
final class Rows {
	private Rows() {
	}

	/** A piece of coloured text. */
	record Seg(String text, int color) {
	}

	static Seg seg(String text, int color) {
		return new Seg(text, color);
	}

	/** A heading with a thin rule under it. */
	static final class HeaderRow extends Row {
		private final String text;

		HeaderRow(String text) {
			this.text = text;
		}

		@Override
		int height() {
			return 16;
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
			graphics.text(font, Ui.fit(font, text, w), x, y + 3, Ui.HEADER);
			graphics.fill(x, y + 13, x + w, y + 14, 0x40FFFFFF);
		}
	}

	/** One line of live text made of coloured segments. A null or empty result hides the row (height 0). */
	static final class TextRow extends Row {
		private final Supplier<List<Seg>> supplier;
		private @Nullable Supplier<TooltipText> tooltip;
		private List<Seg> segments = List.of();

		TextRow(Supplier<List<Seg>> supplier) {
			this.supplier = supplier;
		}

		static TextRow of(String text, int color) {
			return new TextRow(() -> List.of(seg(text, color)));
		}

		/** A tooltip for hovering the line. */
		TextRow withTooltip(Supplier<TooltipText> tooltip) {
			this.tooltip = tooltip;
			return this;
		}

		@Override
		void update() {
			List<Seg> next = supplier.get();
			segments = next == null ? List.of() : next;
		}

		@Override
		int height() {
			return segments.isEmpty() ? 0 : Ui.LINE_H;
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
			int cx = x;
			int right = x + w;
			for (Seg segment : segments) {
				if (cx >= right) break;
				String text = Ui.fit(font, segment.text(), right - cx);
				graphics.text(font, text, cx, y + 1, segment.color());
				cx += font.width(segment.text());
			}
		}

		@Override
		@Nullable TooltipRequest tooltipAt(int mouseX, int mouseY) {
			if (tooltip == null || segments.isEmpty()) return null;
			if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + Ui.LINE_H) {
				return new TooltipRequest(tooltip.get(), mouseX, mouseY);
			}
			return null;
		}
	}

	/** A horizontal progress bar with a label inside. */
	static final class BarRow extends Row {
		private final DoubleSupplier fraction;
		private final Supplier<String> label;
		private final IntSupplier color;
		private double value;
		private String text = "";
		private int fillColor = Ui.GREY;

		BarRow(DoubleSupplier fraction, Supplier<String> label, IntSupplier color) {
			this.fraction = fraction;
			this.label = label;
			this.color = color;
		}

		@Override
		void update() {
			double f = fraction.getAsDouble();
			value = Double.isNaN(f) ? 0 : Math.max(0, Math.min(1, f));
			text = label.get();
			fillColor = color.getAsInt();
		}

		@Override
		int height() {
			return 14;
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
			graphics.fill(x, y, x + w, y + 12, 0xFF202020);
			int filled = (int) Math.round((w - 2) * value);
			if (filled > 0) graphics.fill(x + 1, y + 1, x + 1 + filled, y + 11, fillColor & 0xAAFFFFFF | 0xAA000000);
			graphics.outline(x, y, w, 12, 0xFF555555);
			String shown = Ui.fit(font, text, w - 4);
			graphics.text(font, shown, x + (w - font.width(shown)) / 2, y + 2, Ui.WHITE);
		}
	}

	/** A row of buttons that share the width by weight. It can hide itself (height 0) and refresh its buttons. */
	static final class ButtonsRow extends Row {
		private final List<? extends AbstractWidget> buttons;
		private final double[] weights;
		private final int height;
		private final BooleanSupplier shownWhen;
		private final Runnable refresher;
		private boolean show = true;

		ButtonsRow(int height, List<? extends AbstractWidget> buttons, double[] weights, BooleanSupplier shownWhen, Runnable refresher) {
			this.height = height;
			this.buttons = buttons;
			this.weights = weights;
			this.shownWhen = shownWhen;
			this.refresher = refresher;
		}

		/** Equal-width buttons, always shown. */
		static ButtonsRow of(List<? extends AbstractWidget> buttons) {
			double[] weights = new double[buttons.size()];
			java.util.Arrays.fill(weights, 1.0);
			return new ButtonsRow(Ui.ROW_H, buttons, weights, () -> true, () -> {
			});
		}

		@Override
		void update() {
			show = shownWhen.getAsBoolean();
			if (show) refresher.run();
		}

		@Override
		int height() {
			return show ? height + Ui.GAP : 0;
		}

		@Override
		List<AbstractWidget> widgets() {
			return List.copyOf(buttons);
		}

		@Override
		void applyVisibility(boolean shown) {
			for (AbstractWidget button : buttons) button.visible = shown && show;
		}

		@Override
		void layout(Font font, int x, int y, int w) {
			super.layout(font, x, y, w);
			int gap = 4;
			double total = 0;
			for (double weight : weights) total += weight;
			int available = w - gap * (buttons.size() - 1);
			int cx = x;
			for (int i = 0; i < buttons.size(); i++) {
				int bw = i == buttons.size() - 1 ? x + w - cx : (int) Math.round(available * weights[i] / total);
				AbstractWidget button = buttons.get(i);
				button.setX(cx);
				button.setY(y);
				button.setWidth(Math.max(10, bw));
				cx += bw + gap;
			}
		}
	}
}
