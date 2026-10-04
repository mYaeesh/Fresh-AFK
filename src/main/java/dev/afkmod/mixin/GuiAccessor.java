package dev.afkmod.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the action bar ("overlay message") exactly as the client is showing it, so the restart queue text counts
 * as gone only when it has really left the screen. In 26.1.2 {@code Gui.setOverlayMessage} sets the text and
 * {@code overlayMessageTime = 60}; the time counts down each tick and the text is drawn only while it is above 0.
 */
@Mixin(Gui.class)
public interface GuiAccessor {
	@Accessor("overlayMessageString")
	@Nullable Component afkmod$getOverlayMessage();

	@Accessor("overlayMessageTime")
	int afkmod$getOverlayMessageTime();
}
