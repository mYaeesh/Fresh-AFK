package dev.afkmod.logic;

import dev.afkmod.config.AfkConfig;
import dev.afkmod.logic.AfkStateMachine.DisconnectCause;
import dev.afkmod.logic.AfkStateMachine.Reason;
import dev.afkmod.logic.AfkStateMachine.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkStateMachineTest {
	private static final String RESTART_MSG = "§cServers restarting in 10 seconds!";

	private record Transition(State from, State to, Reason reason) {
	}

	private final FakeClock clock = new FakeClock();
	private final AfkConfig config = new AfkConfig();
	private final List<Transition> transitions = new ArrayList<>();
	private AfkStateMachine sm;

	@BeforeEach
	void setUp() {
		sm = new AfkStateMachine(() -> config, clock);
		sm.setListener((from, to, reason) -> transitions.add(new Transition(from, to, reason)));
	}

	private static final String QUEUE_MSG = "§eRestart Queue #4";

	/** One periodic check with the world loaded and no queue text on screen. */
	private void check() {
		sm.tick(true, false);
	}

	/** One periodic check with the restart queue text on screen. */
	private void checkQueueVisible() {
		sm.tick(true, true);
	}

	private Transition lastTransition() {
		return transitions.getLast();
	}

	/** ACTIVE -> RESTARTING -> relog -> SETTLING -> (settle delay) -> ACTIVE. */
	private void runFullRestartWithRelog() {
		sm.onMessage(RESTART_MSG);
		sm.onDisconnect(DisconnectCause.UNEXPECTED);
		clock.advanceSeconds(30);
		sm.onJoin();
		check();
		clock.advanceSeconds(config.settleDelaySeconds);
		check();
		assertEquals(State.ACTIVE, sm.state());
	}

	// ---- basic toggling ----

	@Test
	void startsOffAndToggles() {
		assertEquals(State.OFF, sm.state());
		sm.toggle();
		assertEquals(State.ACTIVE, sm.state());
		assertEquals(new Transition(State.OFF, State.ACTIVE, Reason.TOGGLED_ON), lastTransition());
		sm.toggle();
		assertEquals(State.OFF, sm.state());
		assertEquals(Reason.TOGGLED_OFF, sm.lastReason());
	}

	@Test
	void eventsWhileOffDoNothing() {
		sm.onMessage(RESTART_MSG);
		sm.onDisconnect(DisconnectCause.UNEXPECTED);
		sm.onJoin();
		sm.onDeath();
		check();
		assertEquals(State.OFF, sm.state());
		assertTrue(transitions.isEmpty());
		assertEquals(0, sm.restartCount());
	}

	// ---- restart detection ----

	@Test
	void restartMessageEntersRestartingAndCountsOnce() {
		sm.turnOn();
		assertEquals(KeywordMatcher.Result.RESTART, sm.onMessage(RESTART_MSG));
		assertEquals(State.RESTARTING, sm.state());
		assertEquals(new Transition(State.ACTIVE, State.RESTARTING, Reason.RESTART_DETECTED), lastTransition());
		sm.onMessage(RESTART_MSG);
		sm.onMessage("SERVERS restarting in 5");
		assertEquals(1, sm.restartCount());
		assertEquals(2, transitions.size());
	}

	@Test
	void whisperedMessageDoesNotTriggerRestart() {
		sm.turnOn();
		assertEquals(KeywordMatcher.Result.IGNORED, sm.onMessage("Bob whispered to you: servers are restarting"));
		assertEquals(State.ACTIVE, sm.state());
		assertEquals(0, sm.restartCount());
	}

	@Test
	void unrelatedMessageDoesNothing() {
		sm.turnOn();
		sm.onMessage("You mined a block");
		assertEquals(State.ACTIVE, sm.state());
	}

	@Test
	void keywordChangesInConfigTakeEffectImmediately() {
		sm.turnOn();
		config.restartKeywords = List.of("reboot");
		sm.onMessage(RESTART_MSG);
		assertEquals(State.ACTIVE, sm.state());
		sm.onMessage("Reboot incoming");
		assertEquals(State.RESTARTING, sm.state());
	}

	// ---- leaving RESTARTING ----

	@Test
	void relogDuringRestartKeepsModOnAndResumesAfterSettleDelay() {
		sm.turnOn();
		sm.onMessage(RESTART_MSG);

		sm.onDisconnect(DisconnectCause.UNEXPECTED);
		assertEquals(State.RESTARTING, sm.state(), "a relog during a restart must not turn the mod off");
		assertTrue(sm.isAwaitingRejoin());

		// While disconnected, the clear timeout must not end the restart.
		clock.advanceSeconds(600);
		check();
		assertEquals(State.RESTARTING, sm.state());

		sm.onJoin();
		assertEquals(State.RESTARTING, sm.state(), "the rejoin alone doesn't resume; the check does");
		assertTrue(sm.hasRejoinedDuringRestart());

		// Nothing happens until the world is ready.
		sm.tick(false, false);
		clock.advanceSeconds(10);
		sm.tick(false, false);
		assertEquals(State.RESTARTING, sm.state());

		check();
		assertEquals(new Transition(State.RESTARTING, State.SETTLING, Reason.REJOINED), lastTransition());
		clock.advanceSeconds(2);
		check();
		assertEquals(State.SETTLING, sm.state());
		clock.advanceSeconds(1);
		check();
		assertEquals(new Transition(State.SETTLING, State.ACTIVE, Reason.SETTLED), lastTransition());
		assertEquals(1, sm.restartCount());
	}

	@Test
	void settleDelayRestartsIfWorldBecomesNotReady() {
		sm.turnOn();
		sm.onMessage(RESTART_MSG);
		sm.onJoin();
		clock.advanceSeconds(config.queueGoneSeconds);
		check();
		assertEquals(State.SETTLING, sm.state());
		clock.advanceSeconds(2);
		sm.tick(false, false);
		check();
		clock.advanceSeconds(2);
		check();
		assertEquals(State.SETTLING, sm.state());
		clock.advanceSeconds(1);
		check();
		assertEquals(State.ACTIVE, sm.state());
	}

	@Test
	void restartEndKeywordsAreNoLongerUsed() {
		config.restartEndKeywords = List.of("back online");
		sm.turnOn();
		sm.onMessage(RESTART_MSG);
		sm.onMessage("Servers are back online");
		assertEquals(State.RESTARTING, sm.state(), "there is no all-clear message; only the queue text logic ends a restart");
	}

	/** Restart message, then the queue text on screen for {@code seconds} checks (one per second), then gone. */
	private void restartWithQueueTextFor(int seconds) {
		sm.onMessage(RESTART_MSG);
		clock.advanceSeconds(1);
		sm.onMessage(QUEUE_MSG, true);
		for (int i = 0; i < seconds; i++) {
			checkQueueVisible();
			clock.advanceSeconds(1);
		}
	}

	@Test
	void queueTextGoneBeforeReconnectResumesOnRejoin() {
		config.timerSeconds = 600;
		sm.turnOn();
		clock.advanceSeconds(100);
		restartWithQueueTextFor(20);
		assertEquals(1, sm.restartCount(), "the queue text refreshes the same restart");

		// Text gone, but the server hasn't reconnected us yet: well under the fallback, so keep waiting.
		check();
		clock.advanceSeconds(5);
		check();
		assertEquals(State.RESTARTING, sm.state());

		// The server's reconnect: connecting screen, then rejoin.
		sm.onDisconnect(DisconnectCause.UNEXPECTED);
		clock.advanceSeconds(120);
		sm.tick(false, false);
		assertEquals(State.RESTARTING, sm.state(), "no fallback while disconnected");
		sm.onJoin();
		check();
		assertEquals(new Transition(State.RESTARTING, State.SETTLING, Reason.REJOINED), lastTransition());
		clock.advanceSeconds(config.settleDelaySeconds);
		assertEquals(500, sm.timerRemainingSeconds(), "message, queue text, reconnect and settle delay don't count");
		check();
		assertEquals(new Transition(State.SETTLING, State.ACTIVE, Reason.SETTLED), lastTransition());
		assertEquals(1, sm.restartCount());
	}

	@Test
	void reconnectWhileQueueTextStillShowingWaitsForItToGo() {
		sm.turnOn();
		restartWithQueueTextFor(5);
		sm.onDisconnect(DisconnectCause.UNEXPECTED);
		clock.advanceSeconds(10);
		sm.onJoin();

		// After the rejoin the text is still (or again) on screen.
		for (int i = 0; i < 10; i++) {
			checkQueueVisible();
			assertEquals(State.RESTARTING, sm.state());
			clock.advanceSeconds(1);
		}
		check(); // last seen 1 s ago
		clock.advanceSeconds(1);
		check();
		assertEquals(State.RESTARTING, sm.state(), "gone for only 2 of the 3 seconds");
		clock.advanceSeconds(1);
		check();
		assertEquals(new Transition(State.RESTARTING, State.SETTLING, Reason.REJOINED), lastTransition());
		clock.advanceSeconds(config.settleDelaySeconds);
		check();
		assertEquals(State.ACTIVE, sm.state());
	}

	@Test
	void reconnectWithoutDisconnectEventStillCountsAsRejoin() {
		sm.turnOn();
		restartWithQueueTextFor(5);
		sm.onJoin(); // a proxy-style reconnect fires only JOIN
		clock.advanceSeconds(config.queueGoneSeconds);
		check();
		assertEquals(new Transition(State.RESTARTING, State.SETTLING, Reason.REJOINED), lastTransition());
	}

	@Test
	void flickeringQueueTextDoesNotEndRestartEarly() {
		sm.turnOn();
		restartWithQueueTextFor(3);
		sm.onJoin();
		for (int round = 0; round < 5; round++) {
			checkQueueVisible();
			clock.advanceSeconds(1);
			check();
			clock.advanceSeconds(1);
			check(); // gone for 2 s, then it shows again
			assertEquals(State.RESTARTING, sm.state());
			clock.advanceSeconds(1);
		}
		check(); // now gone for 3 s
		assertEquals(State.SETTLING, sm.state());
	}

	@Test
	void noReconnectFallbackResumesAfterQueueTextGone() {
		sm.turnOn();
		restartWithQueueTextFor(10); // last seen 1 s ago
		check();
		clock.advanceSeconds(config.noReconnectFallbackSeconds - 2);
		check();
		assertEquals(State.RESTARTING, sm.state());
		clock.advanceSeconds(1);
		check();
		assertEquals(new Transition(State.RESTARTING, State.SETTLING, Reason.NO_RECONNECT_FALLBACK), lastTransition());
		clock.advanceSeconds(config.settleDelaySeconds);
		check();
		assertEquals(State.ACTIVE, sm.state());
		assertTrue(sm.isInPostResumeCooldown());
	}

	@Test
	void noReconnectFallbackAlsoWorksWhenNoQueueTextAppears() {
		sm.turnOn();
		sm.onMessage(RESTART_MSG);
		clock.advanceSeconds(29);
		check();
		assertEquals(State.RESTARTING, sm.state());
		clock.advanceSeconds(1);
		check();
		assertEquals(Reason.NO_RECONNECT_FALLBACK, sm.lastReason());
	}

	@Test
	void queueMessageTimingIsTheFallbackWhenTheActionBarCantBeRead() {
		sm.turnOn();
		sm.onMessage(RESTART_MSG);
		for (int i = 0; i < 5; i++) {
			sm.onMessage(QUEUE_MSG, true); // the overlay state is unreadable, so every check reports "not visible"
			check();
			clock.advanceSeconds(1);
		}
		sm.onJoin();
		clock.advanceSeconds(1); // 2 s since the last queue message
		check();
		assertEquals(State.RESTARTING, sm.state());
		clock.advanceSeconds(1);
		check();
		assertEquals(Reason.REJOINED, sm.lastReason());
	}

	@Test
	void maxWaitTurnsModOffWhenRejoinNeverComes() {
		sm.turnOn();
		restartWithQueueTextFor(5);
		sm.onDisconnect(DisconnectCause.UNEXPECTED);
		clock.advanceSeconds(15 * 60 - 7);
		sm.tick(false, false);
		assertEquals(State.RESTARTING, sm.state());
		clock.advanceSeconds(1);
		sm.tick(false, false);
		assertEquals(new Transition(State.RESTARTING, State.OFF, Reason.RESTART_TIMEOUT), lastTransition());
		sm.onJoin();
		check();
		assertEquals(State.OFF, sm.state(), "after the max wait the mod stays off");
	}

	@Test
	void maxWaitAlsoAppliesWhileTheQueueTextNeverGoes() {
		config.maxRestartWaitMinutes = 2;
		sm.turnOn();
		sm.onMessage(QUEUE_MSG, true);
		clock.advanceSeconds(119);
		checkQueueVisible();
		assertEquals(State.RESTARTING, sm.state());
		clock.advanceSeconds(1);
		checkQueueVisible();
		assertEquals(Reason.RESTART_TIMEOUT, sm.lastReason());
	}

	@Test
	void overlayQueueTextStartsARestartEvenWithOldRestartKeywords() {
		config.restartKeywords = List.of("servers"); // a config file from before "restart queue" was a default
		sm.turnOn();
		assertEquals(KeywordMatcher.Result.NONE, sm.onMessage("restart queue #2"), "chat text isn't the action bar");
		assertEquals(State.ACTIVE, sm.state());
		assertEquals(KeywordMatcher.Result.RESTART, sm.onMessage(QUEUE_MSG, true));
		assertEquals(State.RESTARTING, sm.state());
		assertEquals(1, sm.restartCount());
	}

	@Test
	void whisperedQueueTextIsIgnored() {
		sm.turnOn();
		assertEquals(KeywordMatcher.Result.IGNORED, sm.onMessage("Bob whispered: restart queue", true));
		assertEquals(State.ACTIVE, sm.state());
		assertFalse(sm.isQueueText("Bob whispered: restart queue"));
	}

	@Test
	void queueTextReappearingWhileSettlingGoesBackToRestarting() {
		sm.turnOn();
		restartWithQueueTextFor(3);
		sm.onJoin();
		clock.advanceSeconds(config.queueGoneSeconds);
		check();
		assertEquals(State.SETTLING, sm.state());
		checkQueueVisible();
		assertEquals(new Transition(State.SETTLING, State.RESTARTING, Reason.QUEUE_TEXT_SHOWN), lastTransition());
		assertEquals(1, sm.restartCount());
		assertTrue(sm.hasRejoinedDuringRestart(), "still the same restart; the rejoin already happened");
		clock.advanceSeconds(config.queueGoneSeconds);
		check();
		assertEquals(State.SETTLING, sm.state());
	}

	@Test
	void restartMessageDuringSettlingReturnsToRestartingWithoutCounting() {
		sm.turnOn();
		sm.onMessage(RESTART_MSG);
		sm.onJoin();
		clock.advanceSeconds(config.queueGoneSeconds);
		check();
		assertEquals(State.SETTLING, sm.state());
		sm.onMessage(RESTART_MSG);
		assertEquals(State.RESTARTING, sm.state());
		assertEquals(1, sm.restartCount());
	}

	@Test
	void disconnectDuringSettlingWaitsForRejoin() {
		sm.turnOn();
		sm.onMessage(RESTART_MSG);
		sm.onJoin();
		clock.advanceSeconds(config.queueGoneSeconds);
		check();
		sm.onDisconnect(DisconnectCause.UNEXPECTED);
		assertEquals(State.RESTARTING, sm.state());
		assertTrue(sm.isAwaitingRejoin());
		clock.advanceSeconds(300);
		check();
		assertEquals(State.RESTARTING, sm.state(), "no fallback while disconnected");
		sm.onJoin();
		check();
		assertEquals(State.SETTLING, sm.state());
	}

	// ---- disconnect kinds ----

	@Test
	void manualDisconnectTurnsOffImmediately() {
		sm.turnOn();
		sm.onDisconnect(DisconnectCause.CLIENT_INITIATED);
		assertEquals(new Transition(State.ACTIVE, State.OFF, Reason.MANUAL_DISCONNECT), lastTransition());
	}

	@Test
	void manualDisconnectDuringRestartTurnsOffImmediately() {
		sm.turnOn();
		sm.onMessage(RESTART_MSG);
		sm.onDisconnect(DisconnectCause.CLIENT_INITIATED);
		assertEquals(State.OFF, sm.state());
		assertEquals(Reason.MANUAL_DISCONNECT, sm.lastReason());
		sm.onJoin();
		assertEquals(State.OFF, sm.state(), "rejoining later must not turn the mod back on");
	}

	@Test
	void timerEndTurnsOffAndTheModsOwnDisconnectIsNotARelog() {
		config.timerSeconds = 60;
		sm.turnOn();
		clock.advanceSeconds(59);
		check();
		assertEquals(State.ACTIVE, sm.state());
		clock.advanceSeconds(1);
		check();
		assertEquals(new Transition(State.ACTIVE, State.OFF, Reason.TIMER_END), lastTransition());

		int before = transitions.size();
		sm.onDisconnect(DisconnectCause.MOD_INITIATED);
		sm.onJoin();
		assertEquals(State.OFF, sm.state());
		assertEquals(before, transitions.size());
	}

	@Test
	void modInitiatedDisconnectWhileOnTurnsOff() {
		sm.turnOn();
		sm.onDisconnect(DisconnectCause.MOD_INITIATED);
		assertEquals(State.OFF, sm.state());
		assertEquals(Reason.TIMER_END, sm.lastReason());
	}

	@Test
	void unexpectedDisconnectWithoutMessageWaitsForRejoin() {
		sm.turnOn();
		sm.onDisconnect(DisconnectCause.UNEXPECTED);
		assertEquals(new Transition(State.ACTIVE, State.RECONNECTING, Reason.CONNECTION_LOST), lastTransition());
		clock.advanceSeconds(100);
		check();
		assertEquals(State.RECONNECTING, sm.state());
		sm.onJoin();
		assertEquals(new Transition(State.RECONNECTING, State.SETTLING, Reason.REJOINED), lastTransition());
		check();
		clock.advanceSeconds(3);
		check();
		assertEquals(State.ACTIVE, sm.state());
		assertEquals(0, sm.restartCount(), "a relog without a restart message is not counted as a restart");
	}

	@Test
	void unexpectedDisconnectTurnsOffAfterGracePeriod() {
		sm.turnOn();
		sm.onDisconnect(DisconnectCause.UNEXPECTED);
		clock.advanceSeconds(119);
		check();
		assertEquals(State.RECONNECTING, sm.state());
		clock.advanceSeconds(1);
		check();
		assertEquals(new Transition(State.RECONNECTING, State.OFF, Reason.GRACE_EXPIRED), lastTransition());
		sm.onJoin();
		assertEquals(State.OFF, sm.state());
	}

	@Test
	void manualDisconnectDuringGraceTurnsOff() {
		sm.turnOn();
		sm.onDisconnect(DisconnectCause.UNEXPECTED);
		sm.onDisconnect(DisconnectCause.CLIENT_INITIATED);
		assertEquals(State.OFF, sm.state());
		assertEquals(Reason.MANUAL_DISCONNECT, sm.lastReason());
	}

	@Test
	void deathTurnsOffFromAnyOnState() {
		sm.turnOn();
		sm.onDeath();
		assertEquals(Reason.DEATH, sm.lastReason());
		assertEquals(State.OFF, sm.state());

		sm.turnOn();
		sm.onMessage(RESTART_MSG);
		sm.onDeath();
		assertEquals(State.OFF, sm.state());
	}

	// ---- timer ----

	@Test
	void timerPausesDuringRestartingRelogAndSettle() {
		config.timerSeconds = 600;
		sm.turnOn();
		clock.advanceSeconds(100);
		assertEquals(500, sm.timerRemainingSeconds());

		sm.onMessage(RESTART_MSG);
		assertTrue(sm.isTimerPaused());
		clock.advanceSeconds(60);
		sm.onDisconnect(DisconnectCause.UNEXPECTED);
		clock.advanceSeconds(120);
		sm.onJoin();
		check();
		clock.advanceSeconds(3);
		assertEquals(500, sm.timerRemainingSeconds(), "restart, relog wait and settle delay don't count");
		check();
		assertEquals(State.ACTIVE, sm.state());
		assertFalse(sm.isTimerPaused());

		clock.advanceSeconds(499);
		check();
		assertEquals(State.ACTIVE, sm.state());
		clock.advanceSeconds(1);
		check();
		assertEquals(Reason.TIMER_END, sm.lastReason());
	}

	@Test
	void timerPausesDuringReconnectGrace() {
		config.timerSeconds = 300;
		sm.turnOn();
		clock.advanceSeconds(100);
		sm.onDisconnect(DisconnectCause.UNEXPECTED);
		clock.advanceSeconds(100);
		assertEquals(200, sm.timerRemainingSeconds());
	}

	@Test
	void noTimerNeverEnds() {
		sm.turnOn();
		clock.advanceSeconds(1_000_000);
		check();
		assertEquals(State.ACTIVE, sm.state());
		assertFalse(sm.hasTimer());
	}

	@Test
	void changingTimerWhileActiveRestartsCountdown() {
		config.timerSeconds = 600;
		sm.turnOn();
		clock.advanceSeconds(550);
		sm.setTimerSeconds(120);
		assertEquals(120, config.timerSeconds);
		assertEquals(120, sm.timerRemainingSeconds());
		clock.advanceSeconds(119);
		check();
		assertEquals(State.ACTIVE, sm.state());
		clock.advanceSeconds(1);
		check();
		assertEquals(State.OFF, sm.state());
	}

	@Test
	void changingTimerWhileRestartingStaysPaused() {
		sm.turnOn();
		sm.onMessage(RESTART_MSG);
		sm.setTimerSeconds(60);
		clock.advanceSeconds(1000);
		assertEquals(60, sm.timerRemainingSeconds());
		assertTrue(sm.isTimerPaused());
	}

	@Test
	void timerStartsFreshEachTimeModIsTurnedOn() {
		config.timerSeconds = 100;
		sm.turnOn();
		clock.advanceSeconds(70);
		sm.turnOff();
		assertFalse(sm.hasTimer());
		sm.turnOn();
		assertEquals(100, sm.timerRemainingSeconds());
	}

	@Test
	void settingTimerWhileOffOnlyStoresIt() {
		sm.setTimerSeconds(90);
		assertEquals(90, config.timerSeconds);
		assertFalse(sm.hasTimer());
	}

	// ---- post-resume cooldown ----

	@Test
	void restartMessagesIgnoredDuringPostResumeCooldown() {
		sm.turnOn();
		runFullRestartWithRelog();
		assertTrue(sm.isInPostResumeCooldown());

		clock.advanceSeconds(14);
		sm.onMessage(RESTART_MSG);
		assertEquals(State.ACTIVE, sm.state(), "a leftover message within 15s must not re-trigger");
		assertEquals(1, sm.restartCount());

		clock.advanceSeconds(1);
		assertFalse(sm.isInPostResumeCooldown());
		sm.onMessage(RESTART_MSG);
		assertEquals(State.RESTARTING, sm.state());
		assertEquals(2, sm.restartCount());
	}

	@Test
	void cooldownUsesCurrentConfigValue() {
		config.postResumeCooldownSeconds = 5;
		sm.turnOn();
		runFullRestartWithRelog();
		clock.advanceSeconds(5);
		sm.onMessage(RESTART_MSG);
		assertEquals(State.RESTARTING, sm.state());
	}

	@Test
	void noCooldownWhenFirstTurnedOn() {
		sm.turnOn();
		assertFalse(sm.isInPostResumeCooldown());
		sm.onMessage(RESTART_MSG);
		assertEquals(State.RESTARTING, sm.state());
	}

	@Test
	void cooldownClearedWhenModIsToggledOffAndOn() {
		sm.turnOn();
		runFullRestartWithRelog();
		sm.turnOff();
		sm.turnOn();
		assertFalse(sm.isInPostResumeCooldown());
		sm.onMessage(RESTART_MSG);
		assertEquals(State.RESTARTING, sm.state());
	}

	// ---- relog without a disconnect event or message (e.g. proxy relog) ----

	@Test
	void joinWhileActiveIsARelogThatPausesTimerAndSettlesWithCooldown() {
		config.timerSeconds = 600;
		sm.turnOn();
		clock.advanceSeconds(100);

		sm.onJoin();
		assertEquals(new Transition(State.ACTIVE, State.SETTLING, Reason.REJOINED), lastTransition());
		assertEquals(0, sm.restartCount(), "no restart message, so not counted");
		assertTrue(sm.isTimerPaused());

		check();
		clock.advanceSeconds(config.settleDelaySeconds);
		assertEquals(500, sm.timerRemainingSeconds());
		check();
		assertEquals(State.ACTIVE, sm.state());
		assertTrue(sm.isInPostResumeCooldown());
	}

	@Test
	void restartCountPersistsAcrossToggles() {
		sm.turnOn();
		sm.onMessage(RESTART_MSG);
		sm.turnOff();
		sm.turnOn();
		sm.onMessage(RESTART_MSG);
		assertEquals(2, sm.restartCount());
	}

	// ---- movement recovery (RECOVERING, a sub-state of ACTIVE) ----

	@Test
	void recoveryStartsOnlyFromActiveAndReturnsToActive() {
		assertFalse(sm.startRecovery(), "not while OFF");
		sm.turnOn();
		assertTrue(sm.startRecovery());
		assertEquals(new Transition(State.ACTIVE, State.RECOVERING, Reason.STUCK_DETECTED), lastTransition());
		assertTrue(sm.isActiveOrRecovering());
		assertFalse(sm.startRecovery(), "already recovering");
		sm.endRecovery(Reason.RECOVERED);
		assertEquals(new Transition(State.RECOVERING, State.ACTIVE, Reason.RECOVERED), lastTransition());
		sm.startRecovery();
		sm.endRecovery(Reason.RECOVERY_FAILED);
		assertEquals(State.ACTIVE, sm.state());
		sm.startRecovery();
		sm.endRecovery(Reason.RECOVERY_CANCELLED);
		assertEquals(Reason.RECOVERY_CANCELLED, sm.lastReason());
	}

	@Test
	void endRecoveryOutsideRecoveringDoesNothing() {
		sm.turnOn();
		int before = transitions.size();
		sm.endRecovery(Reason.RECOVERED);
		assertEquals(before, transitions.size());
		assertThrows(IllegalArgumentException.class, () -> sm.endRecovery(Reason.SETTLED));
	}

	@Test
	void restartTakesPriorityOverRecovery() {
		sm.turnOn();
		sm.startRecovery();
		sm.onMessage(RESTART_MSG);
		assertEquals(new Transition(State.RECOVERING, State.RESTARTING, Reason.RESTART_DETECTED), lastTransition());
		assertEquals(1, sm.restartCount());
		assertTrue(sm.isTimerPaused());
	}

	@Test
	void queueTextAlsoCancelsRecovery() {
		sm.turnOn();
		sm.startRecovery();
		sm.onMessage(QUEUE_MSG, true);
		assertEquals(State.RESTARTING, sm.state());
	}

	@Test
	void timerKeepsRunningDuringRecoveryAndCanEndIt() {
		config.timerSeconds = 30;
		sm.turnOn();
		clock.advanceSeconds(10);
		sm.startRecovery();
		assertFalse(sm.isTimerPaused(), "recovery is still ACTIVE time");
		clock.advanceSeconds(20);
		check();
		assertEquals(new Transition(State.RECOVERING, State.OFF, Reason.TIMER_END), lastTransition());
	}

	@Test
	void changingTimerDuringRecoveryKeepsItRunning() {
		sm.turnOn();
		sm.startRecovery();
		sm.setTimerSeconds(60);
		assertFalse(sm.isTimerPaused(), "recovery is still ACTIVE time, so the new countdown must run");
		clock.advanceSeconds(20);
		assertEquals(40, sm.timerRemainingSeconds());
	}

	@Test
	void deathDisconnectAndJoinDuringRecovery() {
		sm.turnOn();
		sm.startRecovery();
		sm.onDeath();
		assertEquals(new Transition(State.RECOVERING, State.OFF, Reason.DEATH), lastTransition());

		sm.turnOn();
		sm.startRecovery();
		sm.onDisconnect(DisconnectCause.UNEXPECTED);
		assertEquals(new Transition(State.RECOVERING, State.RECONNECTING, Reason.CONNECTION_LOST), lastTransition());
		sm.turnOff();

		sm.turnOn();
		sm.startRecovery();
		sm.onDisconnect(DisconnectCause.CLIENT_INITIATED);
		assertEquals(new Transition(State.RECOVERING, State.OFF, Reason.MANUAL_DISCONNECT), lastTransition());

		sm.turnOn();
		sm.startRecovery();
		sm.onJoin();
		assertEquals(new Transition(State.RECOVERING, State.SETTLING, Reason.REJOINED), lastTransition());
	}

	@Test
	void togglingOffDuringRecoveryTurnsOff() {
		sm.turnOn();
		sm.startRecovery();
		sm.toggle();
		assertEquals(new Transition(State.RECOVERING, State.OFF, Reason.TOGGLED_OFF), lastTransition());
	}

	@Test
	void givingUpTurnsTheModOff() {
		sm.turnOn();
		sm.startRecovery();
		sm.giveUpRecovery();
		assertEquals(new Transition(State.RECOVERING, State.OFF, Reason.RECOVERY_GAVE_UP), lastTransition());
		// Also from ACTIVE (stuck again after a successful walk, no retries left).
		sm.turnOn();
		sm.giveUpRecovery();
		assertEquals(new Transition(State.ACTIVE, State.OFF, Reason.RECOVERY_GAVE_UP), lastTransition());
		// The mod's own disconnect afterwards changes nothing.
		sm.onDisconnect(DisconnectCause.MOD_INITIATED);
		assertEquals(State.OFF, sm.state());
	}

	@Test
	void postResumeCooldownStillAppliesDuringRecovery() {
		sm.turnOn();
		runFullRestartWithRelog();
		sm.startRecovery();
		assertTrue(sm.isInPostResumeCooldown());
		sm.onMessage(RESTART_MSG);
		assertEquals(State.RECOVERING, sm.state(), "a leftover restart message in the cooldown is ignored");
		sm.endRecovery(Reason.RECOVERED);
		assertTrue(sm.isInPostResumeCooldown());
	}
}
