package dev.afkmod.client.gui;

import net.minecraft.client.gui.Font;

/** Colours and small helpers shared by the settings screen's rows and tabs. */
final class Ui {
	static final int TEXT = 0xFFE0E0E0;
	static final int HEADER = 0xFFFFD27F;
	static final int ERROR = 0xFFFF5555;
	static final int GREEN = 0xFF55FF55;
	static final int GREY = 0xFFAAAAAA;
	static final int DARK_GREY = 0xFF707070;
	static final int YELLOW = 0xFFFFFF55;
	static final int ORANGE = 0xFFFFAA33;
	static final int WHITE = 0xFFFFFFFF;

	/** Height of one control row (buttons and fields are this tall). */
	static final int ROW_H = 18;
	/** Space under a row. */
	static final int GAP = 2;
	/** Height of one line of plain text. */
	static final int LINE_H = 11;
	/** Height of the red error line under a field. */
	static final int ERROR_H = 10;

	private Ui() {
	}

	/** Width of the label column of a setting row: 45% of the row, between 100 and 160 px. */
	static int labelWidth(int rowWidth) {
		return Math.min(160, Math.max(100, rowWidth * 45 / 100));
	}

	/** {@code text} cut with "..." so it fits in {@code width} pixels. */
	static String fit(Font font, String text, int width) {
		if (width <= 0) return "";
		if (font.width(text) <= width) return text;
		String ellipsis = "...";
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width(ellipsis))) + ellipsis;
	}
}
