package dev.afkmod.config;

public enum HudCorner {
	TOP_LEFT,
	TOP_RIGHT,
	BOTTOM_RIGHT,
	BOTTOM_LEFT;

	/** Next corner, clockwise, for the GUI cycle button. */
	public HudCorner next() {
		HudCorner[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	/** The name shown in the GUI and in tooltips. */
	public String displayName() {
		return switch (this) {
			case TOP_LEFT -> "Top left";
			case TOP_RIGHT -> "Top right";
			case BOTTOM_RIGHT -> "Bottom right";
			case BOTTOM_LEFT -> "Bottom left";
		};
	}
}
