package dev.afkmod.client.gui;

import dev.afkmod.client.gui.Rows.HeaderRow;
import dev.afkmod.client.gui.SettingRows.CycleRow;
import dev.afkmod.client.gui.SettingRows.SliderRow;
import dev.afkmod.client.gui.SettingRows.ToggleRow;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.config.HudCorner;
import net.minecraft.network.chat.Component;

/** The HUD: on/off, detailed mode, corner and scale. The HUD itself updates behind the panel as you change them. */
final class DisplayTab extends RowTab {

	DisplayTab() {
		super("Display", "Display");
		AfkConfig config = SettingRows.config();
		rows.add(new HeaderRow("Display"));
		rows.add(new ToggleRow(font, "hudEnabled", config.hudEnabled, v -> config.hudEnabled = v));
		rows.add(new ToggleRow(font, "hudDetailed", config.hudDetailed, v -> config.hudDetailed = v));
		rows.add(new CycleRow<>("hudCorner", config.hudCorner, HudCorner.values(), c -> Component.literal(c.displayName()),
				v -> config.hudCorner = v));
		rows.add(new SliderRow("hudScale", v -> config.hudScale = v));
	}
}
