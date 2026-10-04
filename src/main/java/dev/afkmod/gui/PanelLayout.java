package dev.afkmod.gui;

/** Size of the centred settings panel (in scaled GUI pixels, so it follows the GUI scale). No Minecraft imports. */
public final class PanelLayout {
	public static final int MAX_WIDTH = 420;
	/** Narrow windows still get a panel this wide (when the window allows it) so fields stay usable. */
	public static final int MIN_WIDTH = 300;
	/** Space kept free at both sides of the screen. */
	public static final int SIDE_MARGIN = 6;

	private PanelLayout() {
	}

	/** About 60% of the screen width, at most {@value #MAX_WIDTH}, at least {@value #MIN_WIDTH}, never wider than the screen. */
	public static int panelWidth(int screenWidth) {
		int sixtyPercent = screenWidth * 3 / 5;
		int width = Math.min(MAX_WIDTH, Math.max(sixtyPercent, MIN_WIDTH));
		return Math.max(60, Math.min(width, screenWidth - 2 * SIDE_MARGIN));
	}

	/** X of the panel's left edge. */
	public static int panelX(int screenWidth, int panelWidth) {
		return (screenWidth - panelWidth) / 2;
	}
}
