package dev.afkmod.logic;

/** Where an incoming piece of text came from. Used for restart detection and the debug message log. */
public enum MessageSource {
	/** System/game message in the chat area. */
	SYSTEM,
	/** Action bar text (a system message with the overlay flag, or a dedicated action bar packet). */
	ACTION_BAR,
	TITLE,
	SUBTITLE,
	BOSS_BAR,
	/** Signed player chat. Only written to the debug log, never used for restart detection. */
	CHAT;

	/** The name shown in the GUI. */
	public String displayName() {
		return switch (this) {
			case SYSTEM -> "Chat/system";
			case ACTION_BAR -> "Action bar";
			case TITLE -> "Title";
			case SUBTITLE -> "Subtitle";
			case BOSS_BAR -> "Boss bar";
			case CHAT -> "Player chat";
		};
	}
}
