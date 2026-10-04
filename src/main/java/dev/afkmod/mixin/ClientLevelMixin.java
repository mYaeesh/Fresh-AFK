package dev.afkmod.mixin;

import dev.afkmod.client.AfkController;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every client-side quit in 26.1.2 (pause menu "Disconnect", death screen "Title screen", closing the game)
 * calls {@code ClientLevel.disconnect} before the connection closes. Server kicks, drops and the restart relog
 * never do, which is how a manual disconnect is told apart from a relog.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
	@Inject(method = "disconnect", at = @At("HEAD"))
	private void afkmod$onClientQuit(Component message, CallbackInfo ci) {
		AfkController controller = AfkController.get();
		if (controller != null) controller.onClientQuit();
	}
}
