package dev.afkmod.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import dev.afkmod.gui.RowScroll;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A tab whose content is a list of {@link Row}s inside the panel. Rows are laid out top to bottom from the first
 * visible row; a row is shown only if it fits completely, and the mouse wheel / Page Up / Page Down scroll by whole rows
 * (see {@link RowScroll}). Everything is re-laid out every frame, so live values and error lines can change the height.
 * Implements vanilla's {@link Tab}, so {@code TabNavigationBar} and {@code TabManager} drive it.
 */
abstract class RowTab implements Tab {
	static final int PAD = 8;
	/** Width reserved on the right for the scrollbar. */
	static final int SCROLLBAR = 4;

	/** First visible row per tab, so a rebuilt screen keeps the scroll position. */
	private static final Map<String, Integer> SAVED_FIRST = new HashMap<>();

	protected final Font font = Minecraft.getInstance().font;
	protected final List<Row> rows = new ArrayList<>();
	private final String shortTitle;
	private final String fullTitle;
	private @Nullable ScreenRectangle area;
	private int first;
	private int maxFirst;
	private int viewportHeight;
	private int[] heights = new int[0];
	private int[] tops = new int[0];
	private int lastShown;

	RowTab(String shortTitle, String fullTitle) {
		this.shortTitle = shortTitle;
		this.fullTitle = fullTitle;
		this.first = SAVED_FIRST.getOrDefault(fullTitle, 0);
	}

	/** The full name, used as the heading, the tab's tooltip and for narration. */
	String fullTitle() {
		return fullTitle;
	}

	@Override
	public Component getTabTitle() {
		return Component.literal(shortTitle);
	}

	@Override
	public Component getTabExtraNarration() {
		return Component.literal(fullTitle);
	}

	@Override
	public void visitChildren(Consumer<AbstractWidget> childrenConsumer) {
		for (Row row : rows) {
			for (AbstractWidget widget : row.widgets()) childrenConsumer.accept(widget);
		}
	}

	@Override
	public void doLayout(ScreenRectangle screenRectangle) {
		this.area = screenRectangle;
		relayout();
	}

	/** Called every frame before the rows are measured, e.g. to rebuild rows that depend on live data. */
	protected void refreshRows() {
	}

	/** Re-measures and re-positions every row, hiding those that don't fit. Called every frame. */
	void relayout() {
		ScreenRectangle r = area;
		if (r == null) return;
		refreshRows();
		for (Row row : rows) row.update();
		int count = rows.size();
		if (heights.length != count) heights = new int[count];
		for (int i = 0; i < count; i++) heights[i] = rows.get(i).height();

		int vx = r.left() + PAD;
		int vy = r.top() + PAD;
		int vw = Math.max(40, r.width() - 2 * PAD - SCROLLBAR);
		viewportHeight = Math.max(20, r.height() - 2 * PAD);
		first = RowScroll.clampFirst(first, heights, viewportHeight);
		SAVED_FIRST.put(fullTitle, first);
		maxFirst = RowScroll.maxFirst(heights, viewportHeight);
		RowScroll.Layout layout = RowScroll.layout(heights, viewportHeight, first);
		tops = layout.top();
		lastShown = layout.last();
		for (int i = 0; i < count; i++) {
			Row row = rows.get(i);
			boolean shown = tops[i] >= 0;
			row.applyVisibility(shown);
			if (shown) row.layout(font, vx, vy + tops[i], vw);
		}
	}

	/** Scrolls by whole rows (negative = up). */
	void scroll(int rowsDelta) {
		first = RowScroll.clampFirst(first + rowsDelta, heights, viewportHeight);
	}

	/** Draws the rows' text and the scrollbar. The widgets are drawn by the screen. */
	void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ScreenRectangle r = area;
		if (r == null) return;
		for (int i = 0; i < rows.size(); i++) {
			if (tops.length > i && tops[i] >= 0) rows.get(i).draw(graphics, font, mouseX, mouseY);
		}
		if (maxFirst > 0 && !rows.isEmpty()) {
			int trackX = r.right() - PAD + 1;
			int trackY = r.top() + PAD;
			graphics.fill(trackX, trackY, trackX + 3, trackY + viewportHeight, 0x40FFFFFF);
			int visibleRows = Math.max(1, lastShown - first);
			int thumb = Math.max(10, viewportHeight * visibleRows / rows.size());
			int thumbY = trackY + (viewportHeight - thumb) * first / maxFirst;
			graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumb, 0xB0FFFFFF);
		}
	}

	/** The tooltip for the pointer position, or null. */
	@Nullable TooltipRequest tooltipAt(int mouseX, int mouseY) {
		for (int i = 0; i < rows.size(); i++) {
			if (tops.length <= i || tops[i] < 0) continue;
			TooltipRequest request = rows.get(i).tooltipAt(mouseX, mouseY);
			if (request != null) return request;
		}
		return null;
	}
}
