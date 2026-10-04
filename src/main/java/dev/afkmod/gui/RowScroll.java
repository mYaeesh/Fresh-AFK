package dev.afkmod.gui;

import java.util.Arrays;

/**
 * Whole-row scrolling for a tab's content: rows are laid top to bottom from the first visible row, and a row is shown
 * only if it fits completely. That wastes no space at the edges and never draws half a row. No Minecraft imports.
 */
public final class RowScroll {
	/** The result of one layout pass. {@code top[i]} is the y offset of row i from the viewport top, or -1 if hidden. */
	public record Layout(int first, int last, int[] top, int usedHeight) {
	}

	private RowScroll() {
	}

	/** The largest first-row index for which every later row still fits (0 when everything fits). */
	public static int maxFirst(int[] heights, int viewportHeight) {
		int used = 0;
		int first = heights.length;
		while (first > 0 && used + heights[first - 1] <= viewportHeight) {
			used += heights[first - 1];
			first--;
		}
		return first;
	}

	public static int clampFirst(int first, int[] heights, int viewportHeight) {
		return Math.max(0, Math.min(first, maxFirst(heights, viewportHeight)));
	}

	/**
	 * Lays out the rows from {@code first}. {@code last} is exclusive. A row taller than the whole viewport is still
	 * shown alone at the top, so something is always visible.
	 */
	public static Layout layout(int[] heights, int viewportHeight, int first) {
		int[] top = new int[heights.length];
		Arrays.fill(top, -1);
		int start = Math.max(0, Math.min(first, Math.max(0, heights.length - 1)));
		int y = 0;
		int i = start;
		while (i < heights.length) {
			if (y + heights[i] > viewportHeight && i > start) break;
			top[i] = y;
			y += heights[i];
			i++;
		}
		return new Layout(start, i, top, y);
	}

	/** True when some rows do not fit at once, so scrolling is possible. */
	public static boolean canScroll(int[] heights, int viewportHeight) {
		return maxFirst(heights, viewportHeight) > 0;
	}
}
