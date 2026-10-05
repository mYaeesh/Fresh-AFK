package dev.afkmod.client;

import dev.afkmod.AfkModClient;
import dev.afkmod.client.KeyControl.Action;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.config.HudCorner;
import dev.afkmod.logic.AfkStateMachine;
import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.logic.HudText;
import dev.afkmod.logic.HudText.Segment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The compact HUD. It only reads state every frame (nothing is computed or pressed here). Drawn as a small
 * block of text on a semi-transparent dark backing, plus a small red banner at the top centre while the
 * server is restarting.
 */
public final class AfkHud {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(AfkModClient.MOD_ID, "hud");
	private static final int BACKING = 0x99000000;
	private static final int MARGIN = 4;
	private static final int PAD = 2;
	private static final int LINE_HEIGHT = 10;
	private static final String BANNER = "Server restarting";

	/** Test Lab HUD preview: shown instead of the real state while set (the real state is untouched). */
	private static HudText.Snapshot preview;
	private static boolean previewDetailed;

	private AfkHud() {
	}

	/** Shows {@code snapshot} with a "[PREVIEW]" tag instead of the real state; null goes back to the real HUD. */
	public static void setPreview(HudText.Snapshot snapshot, boolean detailed) {
		preview = snapshot;
		previewDetailed = detailed;
	}

	public static void register() {
		// After the vanilla overlay message (action bar) so it draws on top of the game HUD but under chat/screens.
		HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, ID,
				(graphics, deltaTracker) -> render(graphics, Minecraft.getInstance()));
	}

	private static void render(GuiGraphicsExtractor graphics, Minecraft mc) {
		AfkController controller = AfkController.get();
		AfkConfig config = AfkModClient.config();
		if (controller == null || mc.player == null || mc.options.hideGui) return;
		Font font = mc.font;
		// While a Test Lab scenario runs, a small tag says so (even with the mod off).
		String testTag = controller.testTag();
		if (testTag != null) drawTestTag(graphics, font, "TEST: " + testTag);

		AfkStateMachine machine = controller.machine();
		HudText.Snapshot shown = preview;
		// Hidden entirely when the mod is off, or when the player turned the HUD off (a preview always shows).
		if (shown == null && (!machine.isOn() || !config.hudEnabled)) return;

		boolean debugShown = mc.gui.getDebugOverlay().showDebugScreen();
		HudText.Snapshot snapshot = shown != null ? shown : new HudText.Snapshot(machine.state(), machine.hasTimer(),
				machine.isTimerPaused(), machine.timerRemainingSeconds(), machine.restartCount(),
				KeyControl.isActive(mc, Action.CROUCH), KeyControl.isActive(mc, Action.ATTACK),
				KeyControl.isActive(mc, Action.USE), machine.isInPostResumeCooldown());
		State state = snapshot.state();

		if (state == State.RESTARTING) drawBanner(graphics, font);

		List<List<Segment>> lines = HudText.lines(snapshot, shown != null ? previewDetailed : config.hudDetailed);
		if (shown != null) {
			List<Segment> first = new ArrayList<>(lines.getFirst());
			first.addFirst(new Segment("[PREVIEW] ", HudText.YELLOW));
			lines = new ArrayList<>(lines);
			lines.set(0, first);
		}

		float scale = Math.clamp(config.hudScale, AfkConfig.HUD_SCALE_MIN, AfkConfig.HUD_SCALE_MAX);

		// The F3 screen fills the top corners, so draw at the bottom instead while it's open.
		HudCorner corner = config.hudCorner;
		if (debugShown) {
			corner = switch (corner) {
				case TOP_LEFT -> HudCorner.BOTTOM_LEFT;
				case TOP_RIGHT -> HudCorner.BOTTOM_RIGHT;
				default -> corner;
			};
		}
		float boxW = boxWidth(font, lines, scale);
		float boxH = boxHeight(lines, scale);
		float x = switch (corner) {
			case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
			case TOP_RIGHT, BOTTOM_RIGHT -> graphics.guiWidth() - MARGIN - boxW;
		};
		float y = switch (corner) {
			case TOP_LEFT, TOP_RIGHT -> MARGIN;
			case BOTTOM_LEFT, BOTTOM_RIGHT -> graphics.guiHeight() - MARGIN - boxH;
		};

		drawBox(graphics, font, lines, x, y, scale);
	}

	/** Width of the HUD box for {@code lines} at {@code scale}, in GUI pixels. */
	public static float boxWidth(Font font, List<List<Segment>> lines, float scale) {
		int width = 0;
		for (List<Segment> line : lines) width = Math.max(width, lineWidth(font, line));
		return (width + PAD * 2) * scale;
	}

	/** Height of the HUD box for {@code lines} at {@code scale}, in GUI pixels. */
	public static float boxHeight(List<List<Segment>> lines, float scale) {
		return (lines.size() * LINE_HEIGHT - 1 + PAD * 2) * scale;
	}

	/** Draws the HUD text on its dark backing with the top-left corner at ({@code x}, {@code y}). Also used by the settings preview. */
	public static void drawBox(GuiGraphicsExtractor graphics, Font font, List<List<Segment>> lines, float x, float y, float scale) {
		int width = Math.round(boxWidth(font, lines, 1f));
		int height = Math.round(boxHeight(lines, 1f));
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.fill(0, 0, width, height, BACKING);
		int ty = PAD;
		for (List<Segment> line : lines) {
			int tx = PAD;
			for (Segment segment : line) {
				graphics.text(font, segment.text(), tx, ty, segment.color());
				tx += font.width(segment.text());
			}
			ty += LINE_HEIGHT;
		}
		graphics.pose().popMatrix();
	}

	/** A small red banner at the top centre. Fixed normal-size text, never large. */
	private static void drawBanner(GuiGraphicsExtractor graphics, Font font) {
		int textWidth = font.width(BANNER);
		int x = (graphics.guiWidth() - textWidth) / 2;
		graphics.fill(x - 4, MARGIN, x + textWidth + 4, MARGIN + 13, BACKING);
		graphics.text(font, BANNER, x, MARGIN + 2, HudText.RED);
	}

	/** A small yellow tag at the top centre, under where the restart banner goes. */
	private static void drawTestTag(GuiGraphicsExtractor graphics, Font font, String text) {
		int textWidth = font.width(text);
		int x = (graphics.guiWidth() - textWidth) / 2;
		int y = MARGIN + 16;
		graphics.fill(x - 3, y, x + textWidth + 3, y + 11, BACKING);
		graphics.text(font, text, x, y + 2, HudText.YELLOW);
	}

	private static int lineWidth(Font font, List<Segment> line) {
		int w = 0;
		for (Segment segment : line) w += font.width(segment.text());
		return w;
	}
}
