package dev.afkmod.stats;

import dev.afkmod.config.AfkConfig;
import dev.afkmod.logic.AfkStateMachine;
import dev.afkmod.logic.AfkStateMachine.Reason;
import dev.afkmod.logic.AfkStateMachine.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsRecorderTest {
	@TempDir
	Path dir;

	private final TestClock clock = new TestClock();
	private AfkStats stats;
	private StatsRecorder recorder;

	@BeforeEach
	void setUp() {
		stats = clock.stats(dir.resolve("afkmod-stats.json"));
		recorder = new StatsRecorder(stats);
	}

	private List<EventType> types() {
		return stats.getRecentEvents(100).stream().map(StatsEvent::type).toList();
	}

	@Test
	void toggleOnStartsASessionAndLogsIt() {
		recorder.onTransition(State.OFF, State.ACTIVE, Reason.TOGGLED_ON);
		assertNotNull(stats.getCurrentSession());
		assertEquals(List.of(EventType.TOGGLED_ON), types());
	}

	@Test
	void toggleOffEndsTheSessionWithTheMappedReason() {
		recorder.onTransition(State.OFF, State.ACTIVE, Reason.TOGGLED_ON);
		clock.advanceSeconds(10);
		recorder.onTransition(State.ACTIVE, State.OFF, Reason.TOGGLED_OFF);

		assertNull(stats.getCurrentSession());
		assertEquals(EndReason.MANUAL_TOGGLE, stats.getRecentSessions().get(0).endReason);
		assertEquals(List.of(EventType.TOGGLED_ON, EventType.TOGGLED_OFF), types());
	}

	@Test
	void everyOffReasonIsRecordedAsItsEndReason() {
		Object[][] cases = {
				{Reason.DEATH, EndReason.DEATH},
				{Reason.MANUAL_DISCONNECT, EndReason.MANUAL_DISCONNECT},
				{Reason.TIMER_END, EndReason.TIMER_ENDED},
				{Reason.RECOVERY_GAVE_UP, EndReason.RECOVERY_FAILED},
				{Reason.GRACE_EXPIRED, EndReason.RECONNECT_GRACE_EXPIRED},
				{Reason.RESTART_TIMEOUT, EndReason.MAX_RESTART_WAIT_EXCEEDED},
		};
		for (Object[] c : cases) {
			recorder.onTransition(State.OFF, State.ACTIVE, Reason.TOGGLED_ON);
			recorder.onTransition(State.ACTIVE, State.OFF, (Reason) c[0]);
			assertEquals(c[1], stats.getRecentSessions().get(0).endReason, c[0].toString());
		}
		assertEquals(cases.length, stats.getLifetime().sessions);
	}

	@Test
	void timerEndAlsoLogsATimerEndedEvent() {
		recorder.onTransition(State.OFF, State.ACTIVE, Reason.TOGGLED_ON);
		recorder.onTransition(State.ACTIVE, State.OFF, Reason.TIMER_END);
		assertTrue(types().contains(EventType.TIMER_ENDED));
		assertTrue(stats.getRecentSessions().get(0).endedInLogout);
	}

	@Test
	void restartIsOpenedOnDetectionAndClosedWhenBackToActive() {
		recorder.onTransition(State.OFF, State.ACTIVE, Reason.TOGGLED_ON);
		clock.advanceSeconds(30);
		recorder.onTransition(State.ACTIVE, State.RESTARTING, Reason.RESTART_DETECTED);
		clock.advanceSeconds(12);
		recorder.onTransition(State.RESTARTING, State.SETTLING, Reason.REJOINED);
		clock.advanceSeconds(3);
		recorder.onTransition(State.SETTLING, State.ACTIVE, Reason.SETTLED);

		SessionRecord live = stats.getCurrentSession();
		assertEquals(1, live.restarts);
		assertEquals(15_000, live.restartMs);
		assertEquals(30_000, live.activeMs);
		assertTrue(types().containsAll(List.of(EventType.RESTART_DETECTED, EventType.RESUMED)));
	}

	@Test
	void backToRestartingFromSettlingIsTheSameRestart() {
		recorder.onTransition(State.OFF, State.ACTIVE, Reason.TOGGLED_ON);
		recorder.onTransition(State.ACTIVE, State.RESTARTING, Reason.RESTART_DETECTED);
		clock.advanceSeconds(5);
		recorder.onTransition(State.RESTARTING, State.SETTLING, Reason.REJOINED);
		recorder.onTransition(State.SETTLING, State.RESTARTING, Reason.QUEUE_TEXT_SHOWN);
		recorder.onTransition(State.SETTLING, State.RESTARTING, Reason.RESTART_DETECTED);
		clock.advanceSeconds(5);
		recorder.onTransition(State.RESTARTING, State.SETTLING, Reason.REJOINED);
		recorder.onTransition(State.SETTLING, State.ACTIVE, Reason.SETTLED);

		SessionRecord live = stats.getCurrentSession();
		assertEquals(1, live.restarts);
		assertEquals(10_000, live.restartMs);
	}

	@Test
	void aRelogWithoutARestartIsNotRestartTime() {
		recorder.onTransition(State.OFF, State.ACTIVE, Reason.TOGGLED_ON);
		clock.advanceSeconds(10);
		recorder.onTransition(State.ACTIVE, State.RECONNECTING, Reason.CONNECTION_LOST);
		clock.advanceSeconds(20);
		recorder.onTransition(State.RECONNECTING, State.SETTLING, Reason.REJOINED);
		recorder.onTransition(State.SETTLING, State.ACTIVE, Reason.SETTLED);

		SessionRecord live = stats.getCurrentSession();
		assertEquals(0, live.restarts);
		assertEquals(0, live.restartMs);
		assertTrue(types().contains(EventType.RESUMED));
	}

	@Test
	void aRestartThatEndsTheSessionIsClosedAtTheEnd() {
		recorder.onTransition(State.OFF, State.ACTIVE, Reason.TOGGLED_ON);
		recorder.onTransition(State.ACTIVE, State.RESTARTING, Reason.RESTART_DETECTED);
		clock.advanceSeconds(900);
		recorder.onTransition(State.RESTARTING, State.OFF, Reason.RESTART_TIMEOUT);

		SessionRecord r = stats.getRecentSessions().get(0);
		assertEquals(EndReason.MAX_RESTART_WAIT_EXCEEDED, r.endReason);
		assertEquals(900_000, r.restartMs);
		assertEquals(0, r.activeMs);
	}

	@Test
	void joinAndQueueGoneEventsOnlyWhileTheModIsOn() {
		recorder.onJoin(State.OFF);
		recorder.onQueueTextGone(State.OFF);
		assertTrue(types().isEmpty());

		recorder.onJoin(State.RESTARTING);
		recorder.onQueueTextGone(State.RESTARTING);
		assertEquals(List.of(EventType.RECONNECTED, EventType.QUEUE_TEXT_GONE), types());
	}

	@Test
	void timerAndLogoutEvents() {
		recorder.onTimerSet(3600);
		recorder.onTimerSet(0);
		recorder.onLogout("Timer finished");
		List<StatsEvent> events = stats.getRecentEvents(10);
		assertEquals(EventType.TIMER_SET, events.get(0).type());
		assertTrue(events.get(0).text().contains("1:00:00"));
		assertEquals("Timer cleared", events.get(1).text());
		assertEquals(EventType.LOGOUT, events.get(2).type());
		assertTrue(events.get(2).text().contains("Timer finished"));
	}

	@Test
	void recorderEventsAreNeverTestFlagged() {
		recorder.onTransition(State.OFF, State.ACTIVE, Reason.TOGGLED_ON);
		recorder.onJoin(State.ACTIVE);
		recorder.onTimerSet(5);
		assertTrue(stats.getRecentEvents(10).stream().noneMatch(StatsEvent::test));
	}

	/** The real state machine driving the recorder, through a whole restart. */
	@Test
	void realStateMachineRestartScenario() {
		AfkConfig config = new AfkConfig();
		AfkStateMachine machine = new AfkStateMachine(() -> config, clock::nanoTime);
		machine.setListener(recorder::onTransition);

		machine.turnOn();
		clock.advanceSeconds(10);
		machine.onMessage("Servers restarting", false);
		assertEquals(State.RESTARTING, machine.state());
		clock.advanceSeconds(5);
		machine.onJoin();
		machine.tick(true, false); // queue text gone long enough, rejoined -> SETTLING
		assertEquals(State.SETTLING, machine.state());
		clock.advanceSeconds(3);
		machine.tick(true, false);
		assertEquals(State.ACTIVE, machine.state());
		clock.advanceSeconds(7);
		machine.turnOff();

		SessionRecord r = stats.getRecentSessions().get(0);
		assertEquals(EndReason.MANUAL_TOGGLE, r.endReason);
		assertEquals(25_000, r.wallMs);
		assertEquals(1, r.restarts);
		assertEquals(8_000, r.restartMs, "from the restart message until ACTIVE again");
		assertEquals(17_000, r.activeMs);
		assertEquals(8_000, r.longestRestartMs);
		assertEquals(8_000, r.averageRestartMs());
	}
}
