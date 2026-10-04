package dev.afkmod.client.gui;

import dev.afkmod.gui.TooltipPlacement;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2i;
import org.joml.Vector2ic;

/**
 * Places a vanilla tooltip with {@link TooltipPlacement}: right of the anchor, flipped when it doesn't fit, and clamped
 * to all four screen edges (vanilla's default positioner doesn't clamp the top and left edges).
 */
final class ClampingTooltipPositioner implements ClientTooltipPositioner {
	static final ClampingTooltipPositioner INSTANCE = new ClampingTooltipPositioner();

	private ClampingTooltipPositioner() {
	}

	@Override
	public Vector2ic positionTooltip(int screenWidth, int screenHeight, int x, int y, int tooltipWidth, int tooltipHeight) {
		int[] p = TooltipPlacement.place(screenWidth, screenHeight, x, y, tooltipWidth, tooltipHeight);
		return new Vector2i(p[0], p[1]);
	}
}
