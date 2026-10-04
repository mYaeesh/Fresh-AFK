package dev.afkmod.client.gui;

import dev.afkmod.gui.TooltipText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * The small circled "i" next to a label. It is a real (focusable) widget, so the keyboard can focus it (Tab) and the
 * screen shows its tooltip, just like hovering it. It does nothing when clicked.
 */
final class InfoIcon extends AbstractWidget {
	static final int SIZE = 9;
	/** Half-widths of the 9 rows of the circle, top to bottom. */
	private static final int[] ROW_WIDTHS = {5, 7, 9, 9, 9, 9, 9, 7, 5};

	private final String name;
	private final Supplier<TooltipText> text;

	InfoIcon(String name, Supplier<TooltipText> text) {
		super(0, 0, SIZE, SIZE, Component.literal("Info: " + name));
		this.name = name;
		this.text = text;
	}

	TooltipText text() {
		return text.get();
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean hot = isHoveredOrFocused();
		int fill = hot ? 0xFF7CC4FF : 0xFF3F8FE0;
		int x = getX();
		int y = getY();
		for (int row = 0; row < SIZE; row++) {
			int width = ROW_WIDTHS[row];
			int left = x + (SIZE - width) / 2;
			graphics.fill(left, y + row, left + width, y + row + 1, fill);
		}
		// The "i": a dot, then a stem with a small foot and head serif.
		int white = 0xFFFFFFFF;
		graphics.fill(x + 4, y + 2, x + 5, y + 3, white);
		graphics.fill(x + 3, y + 4, x + 5, y + 5, white);
		graphics.fill(x + 4, y + 4, x + 5, y + 7, white);
		graphics.fill(x + 3, y + 6, x + 6, y + 7, white);
		if (isFocused()) graphics.outline(x - 1, y - 1, SIZE + 2, SIZE + 2, 0xFFFFFFFF);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal("Info: " + name));
		output.add(NarratedElementType.HINT, Component.literal(String.join(" ", text.get().allLines())));
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
		// Clicking an info icon is not a button press.
	}

	@Override
	protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
		// The mouse only hovers it. A click must not focus it, or its tooltip would stay up after the pointer leaves;
		// keyboard focus (Tab) still works.
		return false;
	}
}
