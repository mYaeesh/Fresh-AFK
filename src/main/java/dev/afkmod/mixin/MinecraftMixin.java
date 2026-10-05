package dev.afkmod.mixin;

import dev.afkmod.client.AfkController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code Minecraft.pauseIfInactive} runs every frame and opens the pause menu once the window has been unfocused for
 * half a second (when "pause on lost focus" is on). While the mod is on, that is skipped so it keeps working after an
 * alt-tab. The player's own option is not changed.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Inject(method = "pauseIfInactive", at = @At("HEAD"), cancellable = true)
	private void afkmod$keepRunningUnfocused(CallbackInfo ci) {
		AfkController controller = AfkController.get();
		if (controller != null && controller.keepRunningWhileUnfocused()) ci.cancel();
	}
}
