package dev.afkmod.gui;

/** Where to draw a tooltip so it always stays fully on screen. No Minecraft imports. */
public final class TooltipPlacement {
	/** Space kept free at every screen edge (the tooltip frame is drawn a few pixels outside its text). */
	public static final int MARGIN = 6;
	/** Offset from the pointer, like vanilla tooltips. */
	public static final int OFFSET = 12;

	private TooltipPlacement() {
	}

	/**
	 * Top-left corner for a tooltip of {@code width} x {@code height} anchored at ({@code anchorX}, {@code anchorY}):
	 * to the right of and slightly above the anchor, flipped to the left when it would not fit, then clamped on all
	 * four sides. A tooltip bigger than the screen sits at the top-left margin.
	 */
	public static int[] place(int screenWidth, int screenHeight, int anchorX, int anchorY, int width, int height) {
		int x = anchorX + OFFSET;
		int y = anchorY - OFFSET;
		if (x + width > screenWidth - MARGIN) x = anchorX - OFFSET - width;
		x = clamp(x, MARGIN, screenWidth - MARGIN - width);
		y = clamp(y, MARGIN, screenHeight - MARGIN - height);
		return new int[]{x, y};
	}

	private static int clamp(int value, int min, int max) {
		if (max < min) return min;
		return Math.max(min, Math.min(max, value));
	}
}
