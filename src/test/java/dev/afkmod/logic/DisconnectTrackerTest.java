package dev.afkmod.logic;

import dev.afkmod.config.AfkConfig;
import dev.afkmod.logic.AfkStateMachine.DisconnectCause;
import dev.afkmod.logic.AfkStateMachine.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisconnectTrackerTest {
	private final DisconnectTracker tracker = new DisconnectTracker();

	@Test
	void noMarkIsUnexpected() {
		// A server kick, drop or the restart relog never calls ClientLevel.disconnect.
		assertEquals(DisconnectCause.UNEXPECTED, tracker.consume());
	}

	@Test
	void clientQuitIsClientInitiated() {
		tracker.markClientQuit();
		assertEquals(DisconnectCause.CLIENT_INITIATED, tracker.consume());
	}

	@Test
	void modDisconnectWinsOverClientQuit() {
		// The timer-end disconnect also goes through ClientLevel.disconnect.
		tracker.markModDisconnect();
		tracker.markClientQuit();
		assertEquals(DisconnectCause.MOD_INITIATED, tracker.consume());
	}

	@Test
	void consumeClearsTheMarks() {
		tracker.markClientQuit();
		tracker.consume();
		assertEquals(DisconnectCause.UNEXPECTED, tracker.consume());
	}

	@Test
	void resetClearsStaleMarks() {
		tracker.markClientQuit();
		tracker.markModDisconnect();
		tracker.reset();
		assertEquals(DisconnectCause.UNEXPECTED, tracker.consume());
	}

	@Test
	void wiredToStateMachine_relogKeepsModOn_manualQuitTurnsItOff() {
		FakeClock clock = new FakeClock();
		AfkConfig config = new AfkConfig();
		AfkStateMachine sm = new AfkStateMachine(() -> config, clock);

		sm.turnOn();
		sm.onDisconnect(tracker.consume()); // relog with no restart message
		assertEquals(State.RECONNECTING, sm.state());
		tracker.reset(); // join
		sm.onJoin();
		assertEquals(State.SETTLING, sm.state());

		tracker.markClientQuit(); // pause menu "Disconnect"
		sm.onDisconnect(tracker.consume());
		assertEquals(State.OFF, sm.state());
	}
}
