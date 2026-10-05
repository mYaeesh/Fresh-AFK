package dev.afkmod.logic;

import dev.afkmod.logic.AfkStateMachine.State;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the HUD content as coloured text segments. No Minecraft imports, so it can be unit tested; the
 * renderer only measures and draws what this returns.
 */
public final class HudText {
	public static final int GREEN = 0xFF55FF55;
	public static final int RED = 0xFFFF5555;
	public static final int GREY = 0xFFAAAAAA;
	public static final int YELLOW = 0xFFFFFF55;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int ORANGE = 0xFFFFAA33;
	public static final int AQUA = 0xFF55FFFF;

	public record Segment(String text, int color) {
	}

	/** The state of everything the HUD shows. */
	public record Snapshot(State state, boolean hasTimer, boolean timerPaused, long timerRemainingSeconds,
			int restartCount, boolean crouch, boolean left, boolean right, boolean cooldownActive) {
	}

	private HudText() {
	}

	/** One colour per state: green working, yellow recovering, red restarting, orange reconnecting, aqua resuming, grey off. */
	public static int dotColor(State state) {
		return switch (state) {
			case ACTIVE -> GREEN;
			case RESTARTING -> RED;
			case RECOVERING -> YELLOW;
			case RECONNECTING -> ORANGE;
			case SETTLING -> AQUA;
			case OFF -> GREY;
		};
	}

	/** The state's text colour: white while mining normally, otherwise the state's own colour. */
	public static int labelColor(State state) {
		return state == State.ACTIVE ? WHITE : dotColor(state);
	}

	public static String label(State state) {
		return switch (state) {
			case ACTIVE -> "AFK Mining";
			case RESTARTING -> "Server restarting";
			case SETTLING -> "Resuming";
			case RECONNECTING -> "Reconnecting";
			case RECOVERING -> "Recovering";
			case OFF -> "AFK Off";
		};
	}

	/** Timer text: remaining time, {@code ∞} for no timer, and a "paused" note while the mod is waiting. */
	public static String timer(Snapshot s) {
		if (!s.hasTimer()) return "∞";
		String time = DurationParser.format(s.timerRemainingSeconds());
		return s.timerPaused() ? time + " (paused)" : time;
	}

	/** Compact mode is one line; detailed mode is three short lines. */
	public static List<List<Segment>> lines(Snapshot s, boolean detailed) {
		List<List<Segment>> lines = new ArrayList<>();
		int base = s.state() == State.RESTARTING ? RED : WHITE;

		List<Segment> first = new ArrayList<>();
		first.add(new Segment("● ", dotColor(s.state())));
		first.add(new Segment(label(s.state()), labelColor(s.state())));

		if (detailed) {
			if (s.state() != State.RESTARTING) first.add(new Segment(" | " + timer(s), base));
			lines.add(first);
			lines.add(keys(s, "Keys: "));
			lines.add(List.of(new Segment("↻ " + s.restartCount() + (s.restartCount() == 1 ? " restart" : " restarts")
					+ (s.cooldownActive() ? " | cooldown" : ""), GREY)));
			return lines;
		}

		// Compact: while restarting the whole line reads "● Server restarting" and nothing else.
		if (s.state() != State.RESTARTING) {
			first.add(new Segment(" | " + timer(s) + " | ", base));
			first.addAll(keys(s, ""));
			first.add(new Segment(" | ↻" + s.restartCount(), base));
		}
		lines.add(first);
		return lines;
	}

	/** {@code C L R}: crouch, left click, right click, each green when active and red when not. */
	private static List<Segment> keys(Snapshot s, String prefix) {
		List<Segment> out = new ArrayList<>();
		if (!prefix.isEmpty()) out.add(new Segment(prefix, WHITE));
		out.add(new Segment("C", s.crouch() ? GREEN : RED));
		out.add(new Segment(" ", WHITE));
		out.add(new Segment("L", s.left() ? GREEN : RED));
		out.add(new Segment(" ", WHITE));
		out.add(new Segment("R", s.right() ? GREEN : RED));
		return out;
	}
}
