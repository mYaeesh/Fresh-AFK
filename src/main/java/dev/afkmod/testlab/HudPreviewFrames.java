package dev.afkmod.testlab;

import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.logic.HudText;

import java.util.List;

/** The sample HUD states the HUD preview (G1) cycles through. Fake numbers; the real mod state is never changed. */
public final class HudPreviewFrames {
	public record Frame(String label, HudText.Snapshot snapshot) {
	}

	private HudPreviewFrames() {
	}

	public static List<Frame> frames() {
		return List.of(
				new Frame("mining", new HudText.Snapshot(State.ACTIVE, true, false, 5025, 2, true, true, true, false)),
				new Frame("restarting", new HudText.Snapshot(State.RESTARTING, true, true, 5025, 3, true, false, false, false)),
				new Frame("recovering", new HudText.Snapshot(State.RECOVERING, true, false, 5010, 3, false, false, false, false)),
				new Frame("paused", new HudText.Snapshot(State.SETTLING, true, true, 5025, 3, true, false, false, false)),
				new Frame("no timer", new HudText.Snapshot(State.ACTIVE, false, false, 0, 0, true, true, true, false)),
				new Frame("timer running", new HudText.Snapshot(State.ACTIVE, true, false, 599, 1, true, true, true, true)));
	}
}
