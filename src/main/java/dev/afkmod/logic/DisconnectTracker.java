package dev.afkmod.logic;

import dev.afkmod.logic.AfkStateMachine.DisconnectCause;

/**
 * Works out why the connection closed, so a server-side relog isn't mistaken for the player quitting.
 *
 * <p>Every client-side quit in vanilla (pause menu "Disconnect", death screen "Title screen", closing the game)
 * calls {@code ClientLevel.disconnect} before the connection closes; server kicks, drops and relogs never do.
 * The Minecraft layer marks those calls here, and the mod marks its own timer-end disconnect. When Fabric's
 * disconnect event fires, {@link #consume()} turns the marks into a cause and clears them.
 *
 * <p>Marks may be set on the client thread and read on the network thread, hence {@code volatile}.
 */
public final class DisconnectTracker {
	private volatile boolean clientQuit;
	private volatile boolean modDisconnect;

	/** The player is leaving the world themselves (pause menu, death screen, closing the game). */
	public void markClientQuit() {
		clientQuit = true;
	}

	/** The mod is about to disconnect on its own (timer end). Takes priority over {@link #markClientQuit()}. */
	public void markModDisconnect() {
		modDisconnect = true;
	}

	/** Returns the cause of the disconnect that just happened and clears the marks. */
	public DisconnectCause consume() {
		DisconnectCause cause = modDisconnect ? DisconnectCause.MOD_INITIATED
				: clientQuit ? DisconnectCause.CLIENT_INITIATED
				: DisconnectCause.UNEXPECTED;
		reset();
		return cause;
	}

	/** Clears stale marks, e.g. on joining a world. */
	public void reset() {
		clientQuit = false;
		modDisconnect = false;
	}
}
