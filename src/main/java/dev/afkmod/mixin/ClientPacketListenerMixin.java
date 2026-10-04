package dev.afkmod.mixin;

import dev.afkmod.client.AfkController;
import dev.afkmod.client.BossBarTextReader;
import dev.afkmod.logic.MessageSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Text that doesn't go through Fabric's message events (titles, subtitles, the dedicated action bar packet,
 * boss bars), plus death.
 *
 * <p>Every hook is at TAIL: each handler starts with {@code ensureRunningOnSameThread}, which re-queues the
 * packet and throws when called on the network thread, so TAIL is only reached on the client thread.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	@Inject(method = "setTitleText", at = @At("TAIL"))
	private void afkmod$onTitle(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
		afkmod$text(MessageSource.TITLE, packet.text());
	}

	@Inject(method = "setSubtitleText", at = @At("TAIL"))
	private void afkmod$onSubtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
		afkmod$text(MessageSource.SUBTITLE, packet.text());
	}

	@Inject(method = "setActionBarText", at = @At("TAIL"))
	private void afkmod$onActionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
		afkmod$text(MessageSource.ACTION_BAR, packet.text());
	}

	@Inject(method = "handleBossUpdate", at = @At("TAIL"))
	private void afkmod$onBossUpdate(ClientboundBossEventPacket packet, CallbackInfo ci) {
		AfkController controller = AfkController.get();
		if (controller != null) packet.dispatch(new BossBarTextReader(controller));
	}

	@Inject(method = "handlePlayerCombatKill", at = @At("TAIL"))
	private void afkmod$onCombatKill(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
		AfkController controller = AfkController.get();
		Minecraft mc = Minecraft.getInstance();
		if (controller != null && mc.player != null && packet.playerId() == mc.player.getId()) {
			controller.onLocalPlayerDeath();
		}
	}

	private static void afkmod$text(MessageSource source, Component text) {
		AfkController controller = AfkController.get();
		if (controller != null) controller.onIncomingText(source, text);
	}
}
