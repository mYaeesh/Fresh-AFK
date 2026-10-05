package dev.afkmod.client.gui;

import dev.afkmod.client.AfkHud;
import dev.afkmod.client.gui.Rows.HeaderRow;
import dev.afkmod.client.gui.Rows.TextRow;
import dev.afkmod.client.gui.SettingRows.CycleRow;
import dev.afkmod.client.gui.SettingRows.SliderRow;
import dev.afkmod.client.gui.SettingRows.ToggleRow;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.config.HudCorner;
import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.logic.HudText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

import static dev.afkmod.client.gui.Rows.seg;

/**
 * The HUD: a live preview, the state colours, and on/off, detailed mode, corner and scale. The preview and the real HUD
 * are drawn by the same code, so what you see here is what you get in the corner.
 */
final class DisplayTab extends RowTab {

	DisplayTab() {
		super("Display", "Display");
		AfkConfig config = SettingRows.config();
		rows.add(new HeaderRow("Display"));
		rows.add(new HudPreviewRow());
		rows.add(new TextRow(() -> List.of(seg("States: ", Ui.GREY),
				seg("● Mining  ", HudText.dotColor(State.ACTIVE)), seg("● Recovering  ", HudText.dotColor(State.RECOVERING)),
				seg("● Restarting  ", HudText.dotColor(State.RESTARTING)), seg("● Reconnecting  ", HudText.dotColor(State.RECONNECTING)),
				seg("● Resuming", HudText.dotColor(State.SETTLING)))));
		rows.add(new ToggleRow(font, "hudEnabled", config.hudEnabled, v -> config.hudEnabled = v));
		rows.add(new ToggleRow(font, "hudDetailed", config.hudDetailed, v -> config.hudDetailed = v));
		rows.add(new CycleRow<>("hudCorner", config.hudCorner, HudCorner.values(), c -> Component.literal(c.displayName()),
				v -> config.hudCorner = v));
		rows.add(new SliderRow("hudScale", v -> config.hudScale = v));
	}

	/** A sample of the HUD at the chosen size and detail (a made-up state; the real HUD is untouched). */
	private static final class HudPreviewRow extends Row {
		private static final HudText.Snapshot SAMPLE = new HudText.Snapshot(State.ACTIVE, true, false, 5025, 2, true, true, true, false);
		private List<List<HudText.Segment>> lines = List.of();
		private float scale = 1f;
		private boolean shown = true;

		@Override
		void update() {
			AfkConfig config = SettingRows.config();
			shown = config.hudEnabled;
			scale = Math.clamp(config.hudScale, AfkConfig.HUD_SCALE_MIN, AfkConfig.HUD_SCALE_MAX);
			lines = HudText.lines(SAMPLE, config.hudDetailed);
		}

		@Override
		int height() {
			return (shown ? (int) Math.ceil(AfkHud.boxHeight(lines, scale)) : Ui.LINE_H) + 6;
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
			if (!shown) {
				graphics.text(font, "The HUD is turned off", x, y + 3, Ui.GREY);
				return;
			}
			AfkHud.drawBox(graphics, font, lines, x, y + 2, scale);
		}
	}
}
