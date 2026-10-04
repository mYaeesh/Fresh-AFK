package dev.afkmod.mixin;

import dev.afkmod.client.AfkController;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code Minecraft.runTick} calls {@code handleAccumulatedMovement} once per frame, after the client ticks and
 * right before the frame is rendered; it is where vanilla turns the player with the mouse. The movement recovery
 * applies its smooth turn here so it is smooth at any frame rate.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
	private void afkmod$onFrame(CallbackInfo ci) {
		AfkController controller = AfkController.get();
		if (controller != null) controller.onFrame();
	}
}
