package dev.afkmod.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkStatsTest {
	@TempDir
	Path dir;

	private final TestClock clock = new TestClock();
	private Path file;
	private AfkStats stats;

	@BeforeEach
	void setUp() {
		file = dir.resolve("afkmod-stats.json");
		stats = clock.stats(file);
	}

	private SessionRecord runSession(long totalSeconds, EndReason reason) {
		stats.startSession();
		clock.advanceSeconds(totalSeconds);
		return stats.endSession(reason);
	}

	// ---- session time math ----

	@Test
	void activeTimeExcludesRestartTime() {
		stats.startSession();
		clock.advanceSeconds(100);
		stats.restartStarted(false);
		clock.advanceSeconds(20);
		stats.restartEnded(false);
		clock.advanceSeconds(30);
		SessionRecord r = stats.endSession(EndReason.MANUAL_TOGGLE);

		assertEquals(150_000, r.wallMs);
		assertEquals(20_000, r.restartMs);
		assertEquals(130_000, r.activeMs);
		assertEquals(1, r.restarts);
	}

	@Test
	void startAndEndTimesAreWallClockTimes() {
		long start = clock.wallMs;
		SessionRecord r = runSession(60, EndReason.MANUAL_TOGGLE);
		assertEquals(start, r.startEpochMs);
		assertEquals(start + 60_000, r.endEpochMs);
	}

	@Test
	void averageAndLongestRestart() {
		stats.startSession();
		for (long seconds : new long[]{10, 30, 20}) {
			clock.advanceSeconds(5);
			stats.restartStarted(false);
			clock.advanceSeconds(seconds);
			stats.restartEnded(false);
		}
		SessionRecord r = stats.endSession(EndReason.TIMER_ENDED);

		assertEquals(3, r.restarts);
		assertEquals(60_000, r.restartMs);
		assertEquals(30_000, r.longestRestartMs);
		assertEquals(20_000, r.averageRestartMs());
	}

	@Test
	void noRestartsMeansZeroAverage() {
		SessionRecord r = runSession(10, EndReason.MANUAL_TOGGLE);
		assertEquals(0, r.averageRestartMs());
		assertEquals(0, r.longestRestartMs);
		assertEquals(r.wallMs, r.activeMs);
	}

	@Test
	void repeatOfAnOpenRestartIsNotCountedAgain() {
		stats.startSession();
		stats.restartStarted(false);
		clock.advanceSeconds(5);
		stats.restartStarted(false);
		clock.advanceSeconds(5);
		assertEquals(10_000, stats.restartEnded(false));
		assertEquals(-1, stats.restartEnded(false), "no restart is open any more");
		assertEquals(1, stats.endSession(EndReason.MANUAL_TOGGLE).restarts);
	}

	@Test
	void restartStillOpenAtSessionEndIsClosedAtTheEndTime() {
		stats.startSession();
		clock.advanceSeconds(10);
		stats.restartStarted(false);
		clock.advanceSeconds(40);
		SessionRecord r = stats.endSession(EndReason.MAX_RESTART_WAIT_EXCEEDED);

		assertEquals(40_000, r.restartMs);
		assertEquals(40_000, r.longestRestartMs);
		assertEquals(10_000, r.activeMs);
		assertEquals(EndReason.MAX_RESTART_WAIT_EXCEEDED, r.endReason);
	}

	@Test
	void currentSessionSnapshotIsLiveAndRunning() {
		assertNull(stats.getCurrentSession());
		stats.startSession();
		clock.advanceSeconds(10);
		stats.restartStarted(false);
		clock.advanceSeconds(5);

		SessionRecord live = stats.getCurrentSession();
		assertTrue(live.isRunning());
		assertEquals(0, live.endEpochMs);
		assertEquals(15_000, live.wallMs);
		assertEquals(5_000, live.restartMs, "an open restart counts up to now");
		assertEquals(10_000, live.activeMs);
	}

	@Test
	void recoveryCountersAndBlocks() {
		stats.startSession();
		stats.stuckDetected(false);
		stats.stuckDetected(false);
		stats.recoveryAttempt(false);
		stats.recoveryResult(true, false);
		stats.recoveryAttempt(false);
		stats.recoveryResult(false, false);
		stats.blockMined();
		stats.blockMined();
		stats.blockMined();
		SessionRecord r = stats.endSession(EndReason.RECOVERY_FAILED);

		assertEquals(2, r.stuckDetections);
		assertEquals(2, r.recoveryAttempts);
		assertEquals(1, r.recoverySuccesses);
		assertEquals(1, r.recoveryFailures);
		assertEquals(3, r.blocksMined);
	}

	@Test
	void blocksMinedOutsideASessionAreIgnored() {
		stats.blockMined();
		assertEquals(0, stats.getLifetime().blocksMined);
	}

	@Test
	void logoutFlagFollowsTheEndReason() {
		assertTrue(runSession(1, EndReason.TIMER_ENDED).endedInLogout);
		assertTrue(runSession(1, EndReason.RECOVERY_FAILED).endedInLogout);
		assertFalse(runSession(1, EndReason.MANUAL_TOGGLE).endedInLogout);
		assertFalse(runSession(1, EndReason.DEATH).endedInLogout);
		assertFalse(runSession(1, EndReason.MANUAL_DISCONNECT).endedInLogout);
	}

	@Test
	void startingASessionWhileOneIsOpenEndsTheOldOneAsInterrupted() {
		stats.startSession();
		clock.advanceSeconds(5);
		stats.startSession();
		assertEquals(EndReason.INTERRUPTED, stats.getRecentSessions().get(0).endReason);
		assertEquals(1, stats.getLifetime().sessions);
	}

	// ---- lifetime and history ----

	@Test
	void lifetimeIsTheSumOfAllSessions() {
		stats.startSession();
		clock.advanceSeconds(100);
		stats.restartStarted(false);
		clock.advanceSeconds(20);
		stats.restartEnded(false);
		stats.stuckDetected(false);
		stats.recoveryAttempt(false);
		stats.recoveryResult(true, false);
		stats.blockMined();
		stats.endSession(EndReason.TIMER_ENDED);

		stats.startSession();
		clock.advanceSeconds(50);
		stats.restartStarted(false);
		clock.advanceSeconds(40);
		stats.restartEnded(false);
		stats.blockMined();
		stats.blockMined();
		stats.endSession(EndReason.DEATH);

		LifetimeStats l = stats.getLifetime();
		assertEquals(2, l.sessions);
		assertEquals(210_000, l.wallMs);
		assertEquals(60_000, l.restartMs);
		assertEquals(150_000, l.activeMs);
		assertEquals(2, l.restarts);
		assertEquals(40_000, l.longestRestartMs);
		assertEquals(30_000, l.averageRestartMs());
		assertEquals(1, l.stuckDetections);
		assertEquals(1, l.recoveryAttempts);
		assertEquals(1, l.recoverySuccesses);
		assertEquals(0, l.recoveryFailures);
		assertEquals(1, l.logouts);
		assertEquals(3, l.blocksMined);
		assertEquals(1, l.endReasons.get("TIMER_ENDED"));
		assertEquals(1, l.endReasons.get("DEATH"));
	}

	@Test
	void recentSessionsKeepTheNewest20NewestFirst() {
		for (int i = 1; i <= 25; i++) runSession(i, EndReason.MANUAL_TOGGLE);

		List<SessionRecord> recent = stats.getRecentSessions();
		assertEquals(20, recent.size());
		assertEquals(25_000, recent.get(0).wallMs, "newest first");
		assertEquals(6_000, recent.get(19).wallMs);
		assertEquals(25, stats.getLifetime().sessions, "lifetime still counts the dropped sessions");
	}

	@Test
	void returnedSnapshotsAreCopies() {
		runSession(10, EndReason.MANUAL_TOGGLE);
		stats.getLifetime().sessions = 99;
		stats.getRecentSessions().get(0).wallMs = 1;
		assertEquals(1, stats.getLifetime().sessions);
		assertEquals(10_000, stats.getRecentSessions().get(0).wallMs);
	}

	@Test
	void resetLifetimeClearsTotalsAndHistoryButNotTheRunningSession() {
		runSession(10, EndReason.MANUAL_TOGGLE);
		stats.startSession();
		clock.advanceSeconds(5);
		stats.resetLifetime();

		assertEquals(0, stats.getLifetime().sessions);
		assertTrue(stats.getRecentSessions().isEmpty());
		assertNotNull(stats.getCurrentSession());
		// The reset was saved: a reload only finds the still-open session (recovered as INTERRUPTED), not the old one.
		AfkStats reloaded = clock.stats(file);
		assertEquals(1, reloaded.getLifetime().sessions);
		assertEquals(EndReason.INTERRUPTED, reloaded.getRecentSessions().get(0).endReason);
	}

	// ---- test flag ----

	@Test
	void testFlaggedUpdatesStayOutOfTheRealCountersLifetimeAndHistory() {
		stats.startSession();
		stats.restartStarted(true);
		clock.advanceSeconds(10);
		stats.restartEnded(true);
		stats.stuckDetected(true);
		stats.recoveryAttempt(true);
		stats.recoveryResult(false, true);
		SessionRecord real = stats.endSession(EndReason.MANUAL_TOGGLE);

		assertEquals(0, real.restarts);
		assertEquals(0, real.restartMs);
		assertEquals(0, real.stuckDetections);
		assertEquals(0, real.recoveryAttempts);
		assertEquals(0, real.recoveryFailures);
		LifetimeStats l = stats.getLifetime();
		assertEquals(0, l.restarts);
		assertEquals(0, l.stuckDetections);
		assertEquals(0, l.recoveryAttempts);
		assertEquals(0, l.recoveryFailures);

		SessionRecord test = stats.getTestCounters();
		assertNotNull(test);
		assertTrue(test.test);
		assertEquals(1, test.restarts);
		assertEquals(10_000, test.restartMs);
		assertEquals(1, test.stuckDetections);
		assertEquals(1, test.recoveryAttempts);
		assertEquals(1, test.recoveryFailures);
	}

	@Test
	void testUpdatesWorkWithoutARealSession() {
		stats.stuckDetected(true);
		assertEquals(1, stats.getTestCounters().stuckDetections);
		assertNull(stats.getCurrentSession());
		stats.stuckDetected(false); // real update with no session: nothing to update, no crash
		assertEquals(0, stats.getLifetime().stuckDetections);
	}

	@Test
	void injectedTestSessionsGoOnlyToTestData() {
		SessionRecord r = new SessionRecord();
		r.wallMs = 5_000;
		r.restarts = 4;
		r.endReason = EndReason.TIMER_ENDED;
		SessionRecord stored = stats.injectTestSession(r);

		assertTrue(stored.test, "the test flag is forced on");
		assertFalse(r.test, "the caller's object is not modified");
		assertEquals(1, stats.getTestSessions().size());
		assertEquals(0, stats.getLifetime().sessions);
		assertTrue(stats.getRecentSessions().isEmpty());
		assertNull(stats.getCurrentSession());
	}

	@Test
	void clearTestDataEmptiesTestStateAndTestEventsOnly() {
		stats.stuckDetected(true);
		stats.injectTestSession(new SessionRecord());
		stats.event(EventType.STUCK_DETECTED, "real", false);
		stats.event(EventType.STUCK_DETECTED, "fake", true);

		stats.clearTestData();

		assertNull(stats.getTestCounters());
		assertTrue(stats.getTestSessions().isEmpty());
		List<StatsEvent> events = stats.getRecentEvents(10);
		assertEquals(1, events.size());
		assertEquals("real", events.get(0).text());
	}

	@Test
	void testDataIsNeverWrittenToTheFile() throws IOException {
		stats.stuckDetected(true);
		stats.injectTestSession(new SessionRecord());
		runSession(5, EndReason.MANUAL_TOGGLE);

		String json = Files.readString(file);
		assertFalse(json.contains("\"test\": true"));
		assertEquals(0, clock.stats(file).getTestSessions().size());
	}

	// ---- event log ----

	@Test
	void eventsCarryTimestampTypeTestFlagAndText() {
		clock.wallMs = 123_456;
		StatsEvent e = stats.event(EventType.YAW_SNAP, "north", true);
		assertEquals(123_456, e.timeMillis());
		assertEquals(EventType.YAW_SNAP, e.type());
		assertTrue(e.test());
		assertEquals("north", e.text());
	}

	@Test
	void ringBufferKeepsTheLast100Events() {
		for (int i = 0; i < 130; i++) stats.event(EventType.TOGGLED_ON, "e" + i, false);

		List<StatsEvent> all = stats.getRecentEvents(1000);
		assertEquals(100, all.size());
		assertEquals("e30", all.get(0).text(), "oldest kept");
		assertEquals("e129", all.get(99).text(), "newest");
		List<StatsEvent> last3 = stats.getRecentEvents(3);
		assertEquals(List.of("e127", "e128", "e129"), last3.stream().map(StatsEvent::text).toList());
		assertTrue(stats.getRecentEvents(0).isEmpty());
	}

	@Test
	void eventSinkReceivesEventsAndItsFailuresAreSwallowed() {
		StringBuilder seen = new StringBuilder();
		stats.setEventSink(e -> seen.append(e.text()).append(';'));
		stats.event(EventType.TOGGLED_ON, "a", false);
		stats.setEventSink(e -> {
			throw new IllegalStateException("boom");
		});
		stats.event(EventType.TOGGLED_ON, "b", false);

		assertEquals("a;", seen.toString());
		assertEquals(2, stats.getRecentEvents(10).size(), "the event is kept even if the sink fails");
	}

	// ---- persistence ----

	@Test
	void sessionEndIsSavedAndReloaded() {
		runSession(60, EndReason.TIMER_ENDED);

		AfkStats reloaded = clock.stats(file);
		assertEquals(1, reloaded.getLifetime().sessions);
		assertEquals(1, reloaded.getRecentSessions().size());
		SessionRecord r = reloaded.getRecentSessions().get(0);
		assertEquals(60_000, r.wallMs);
		assertEquals(EndReason.TIMER_ENDED, r.endReason);
		assertTrue(r.endedInLogout);
		assertFalse(Files.exists(dir.resolve("afkmod-stats.json.tmp")), "no temp file is left behind");
	}

	@Test
	void autoSaveHappensOnlyAfterTheInterval() {
		stats.startSession();
		clock.advanceSeconds(60);
		stats.maybeAutoSave();
		assertFalse(Files.exists(file), "too early");

		clock.advanceSeconds(130);
		stats.maybeAutoSave();
		assertTrue(Files.exists(file));
	}

	@Test
	void autoSaveDoesNothingWithoutARunningSession() {
		clock.advanceSeconds(3600);
		stats.maybeAutoSave();
		assertFalse(Files.exists(file));
	}

	@Test
	void aSessionLeftOpenByACrashBecomesAnInterruptedSession() {
		stats.startSession();
		clock.advanceSeconds(120);
		stats.saveNow(); // what the periodic save writes, then the game dies

		AfkStats reloaded = clock.stats(file);
		assertEquals(1, reloaded.getLifetime().sessions);
		SessionRecord r = reloaded.getRecentSessions().get(0);
		assertEquals(EndReason.INTERRUPTED, r.endReason);
		assertEquals(120_000, r.wallMs);
		assertEquals(r.startEpochMs + 120_000, r.endEpochMs);
		assertFalse(r.endedInLogout);
		assertNull(reloaded.getCurrentSession());
		assertEquals(1, clock.stats(file).getLifetime().sessions, "it is only recovered once");
	}

	@Test
	void shutdownEndsAnOpenSessionAsInterruptedAndSaves() {
		stats.startSession();
		clock.advanceSeconds(10);
		stats.shutdown();
		assertEquals(EndReason.INTERRUPTED, clock.stats(file).getRecentSessions().get(0).endReason);
	}

	@Test
	void setStoreFilePointsAtAnotherFileAndNeverTouchesTheOldOne() throws IOException {
		runSession(10, EndReason.MANUAL_TOGGLE);
		long before = Files.size(file);

		Path other = dir.resolve("other").resolve("stats.json");
		stats.setStoreFile(other);
		assertEquals(0, stats.getLifetime().sessions, "fresh data from a missing file");
		runSession(20, EndReason.MANUAL_TOGGLE);

		assertTrue(Files.exists(other));
		assertEquals(before, Files.size(file));
		assertEquals(other, stats.storeFile());
	}
}
