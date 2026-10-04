package dev.afkmod.client;

import dev.afkmod.logic.MessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;

import java.util.UUID;

/**
 * Reads the text out of a boss bar packet: only a new bar or a renamed bar carries text, so progress and
 * style updates are skipped. Kept outside the mixin so the mixin has no inner classes.
 */
public final class BossBarTextReader implements ClientboundBossEventPacket.Handler {
	private final AfkController controller;

	public BossBarTextReader(AfkController controller) {
		this.controller = controller;
	}

	@Override
	public void add(UUID id, Component name, float progress, BossEvent.BossBarColor color,
			BossEvent.BossBarOverlay overlay, boolean darkenScreen, boolean playMusic, boolean createWorldFog) {
		controller.onIncomingText(MessageSource.BOSS_BAR, name);
	}

	@Override
	public void updateName(UUID id, Component name) {
		controller.onIncomingText(MessageSource.BOSS_BAR, name);
	}
}
