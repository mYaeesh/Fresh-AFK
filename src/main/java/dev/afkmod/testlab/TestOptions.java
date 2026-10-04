package dev.afkmod.testlab;

import dev.afkmod.logic.MessageSource;

/**
 * Options for one scenario run. Plain mutable fields with fluent setters; each scenario reads the ones it needs and
 * ignores the rest.
 */
public final class TestOptions {
	/** The caller confirmed a scenario that moves the player or can disconnect (the screen shows a dialog first). */
	public boolean confirmed;
	/** D5: really disconnect at the end instead of logging "WOULD DISCONNECT" (a separate confirmation). */
	public boolean allowRealDisconnect;
	/** A1: the message text. */
	public String text = "";
	/** A1: where the message arrives (system/chat, action bar, title, subtitle, boss bar). */
	public MessageSource messageType = MessageSource.SYSTEM;
	/** A1: false = Explain (triggers nothing), true = Send for real through the real handler (mod ON only). */
	public boolean sendForReal;
	/** D1: the yaw to calculate; D6: the start yaw to turn from. Null = the player's current yaw. */
	public Double yaw;
	/** B1/E2: how long the "restart queue" action bar text keeps being refreshed. */
	public int testRestartSeconds = 15;
	/** E1: the short timer. */
	public int timerSeconds = 10;
	/** E1: false = "dry end" (everything but the final disconnect), true = "full" (real disconnect, needs confirm). */
	public boolean fullTimerEnd;

	public static TestOptions defaults() {
		return new TestOptions();
	}

	public TestOptions confirmed(boolean value) {
		confirmed = value;
		return this;
	}

	public TestOptions allowRealDisconnect(boolean value) {
		allowRealDisconnect = value;
		return this;
	}

	public TestOptions text(String value) {
		text = value == null ? "" : value;
		return this;
	}

	public TestOptions messageType(MessageSource value) {
		messageType = value == null ? MessageSource.SYSTEM : value;
		return this;
	}

	public TestOptions sendForReal(boolean value) {
		sendForReal = value;
		return this;
	}

	public TestOptions yaw(Double value) {
		yaw = value;
		return this;
	}

	public TestOptions testRestartSeconds(int value) {
		testRestartSeconds = Math.clamp(value, 1, 600);
		return this;
	}

	public TestOptions timerSeconds(int value) {
		timerSeconds = Math.clamp(value, 1, 3600);
		return this;
	}

	public TestOptions fullTimerEnd(boolean value) {
		fullTimerEnd = value;
		return this;
	}
}
