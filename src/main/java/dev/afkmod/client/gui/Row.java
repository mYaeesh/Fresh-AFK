package dev.afkmod.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One line of a tab's content: label, controls, text or a button row. The tab asks every row for its height each frame,
 * lays out the ones that fit in the viewport, and hides the rest (see {@link RowTab}).
 */
abstract class Row {
	/** Position from the last {@link #layout}; used for drawing and hit testing. */
	protected int x;
	protected int y;
	protected int w;

	/** The current height in pixels (may change, e.g. when an error line appears). 0 hides the row. */
	abstract int height();

	/** Places the row's widgets. Called every frame for the rows that are shown. */
	void layout(Font font, int x, int y, int w) {
		this.x = x;
		this.y = y;
		this.w = w;
	}

	/** The widgets this row owns (they are added to the screen while the tab is selected). */
	List<AbstractWidget> widgets() {
		return List.of();
	}

	/** Shows or hides the widgets with the row. Rows with conditional widgets override this. */
	void applyVisibility(boolean shown) {
		for (AbstractWidget widget : widgets()) widget.visible = shown;
	}

	/** Refreshes live values; called every frame for every row, before heights are read. */
	void update() {
	}

	/** Draws text and decorations (the widgets draw themselves). Only called for rows that are shown. */
	void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
	}

	/** The tooltip for the pointer position, or null. Only called for rows that are shown. */
	@Nullable TooltipRequest tooltipAt(int mouseX, int mouseY) {
		return null;
	}
}
