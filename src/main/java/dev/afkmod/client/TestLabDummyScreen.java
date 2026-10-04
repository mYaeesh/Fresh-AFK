package dev.afkmod.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The plain screen the Test Lab opens for the screen-open rule (C2). It closes by itself; Esc closes it too. */
final class TestLabDummyScreen extends Screen {
	TestLabDummyScreen() {
		super(Component.literal("[TEST] Screen-open rule"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, "[TEST] A screen is open: the mod must not press anything.", this.width / 2,
				this.height / 2 - 10, 0xFFFFFF55);
		graphics.centeredText(this.font, "This screen closes by itself.", this.width / 2, this.height / 2 + 4, 0xFFE0E0E0);
	}
}
