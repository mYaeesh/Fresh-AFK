package dev.afkmod.testlab;

import dev.afkmod.stats.AfkStats;
import dev.afkmod.stats.EndReason;
import dev.afkmod.stats.LifetimeStats;
import dev.afkmod.stats.SessionRecord;
import dev.afkmod.stats.StatsEvent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** F. Stats. Never touches the real stats file: F2 and F3 use their own stats object on a temporary file. */
public final class StatsScenarios {
	private StatsScenarios() {
	}

	/** A sample session for previewing the Stats tab. */
	static SessionRecord sampleSession(long nowMs) {
		SessionRecord s = new SessionRecord();
		s.wallMs = TimeUnit.HOURS.toMillis(2);
		s.restartMs = TimeUnit.MINUTES.toMillis(8);
		s.activeMs = s.wallMs - s.restartMs;
		s.restarts = 3;
		s.longestRestartMs = TimeUnit.MINUTES.toMillis(4);
		s.stuckDetections = 2;
		s.recoveryAttempts = 3;
		s.recoverySuccesses = 2;
		s.recoveryFailures = 1;
		s.blocksMined = 12_345;
		s.endReason = EndReason.TIMER_ENDED;
		s.endedInLogout = true;
		s.endEpochMs = nowMs;
		s.startEpochMs = nowMs - s.wallMs;
		s.test = true;
		return s;
	}

	/** A stats object on its own temporary folder, with a manual clock. */
	private static final class TempStats implements AutoCloseable {
		final Path dir;
		final Path file;
		final long[] nanos = {1_000_000_000L};
		final long[] wallMs = {1_700_000_000_000L};

		TempStats() {
			try {
				dir = Files.createTempDirectory("afkmod-testlab");
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			file = dir.resolve("afkmod-stats.json");
		}

		AfkStats open() {
			return new AfkStats(file, () -> nanos[0], () -> wallMs[0]);
		}

		void advance(long seconds) {
			nanos[0] += TimeUnit.SECONDS.toNanos(seconds);
			wallMs[0] += TimeUnit.SECONDS.toMillis(seconds);
		}

		@Override
		public void close() {
			try (Stream<Path> files = Files.walk(dir)) {
				for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
			} catch (IOException ignored) {
				// best effort: it is in the temp folder
			}
		}
	}

	public static final class F1 extends Scenario {
		public F1() {
			super("F1", Group.F, "Inject a test session",
					"Adds a fake, test-flagged session (2 h, 3 restarts, 12345 blocks) to the test data so the Stats tab can be "
							+ "previewed. The real history and lifetime totals must not change.", AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			return Script.of(ctx -> {
				AfkStats stats = ctx.env().stats();
				int recent = stats.getRecentSessions().size();
				int lifetime = stats.getLifetime().sessions;
				int tests = stats.getTestSessions().size();
				SessionRecord stored = stats.injectTestSession(sampleSession(System.currentTimeMillis()));
				ctx.check(stored.test, "The injected session is not flagged as test data");
				ctx.check(stats.getTestSessions().size() == Math.min(tests + 1, AfkStats.MAX_TEST_SESSIONS), "The test session wasn't stored");
				ctx.check(stats.getRecentSessions().size() == recent, "The real session history changed");
				ctx.check(stats.getLifetime().sessions == lifetime, "The real lifetime totals changed");
				ctx.pass("Injected a test session (2:00:00, 3 restarts, 12345 blocks, TIMER_ENDED). Test sessions: "
						+ stats.getTestSessions().size() + "; real history (" + recent + ") and lifetime (" + lifetime + " sessions) unchanged.");
			});
		}
	}

	public static final class F2 extends Scenario {
		public F2() {
			super("F2", Group.F, "Save/load round trip",
					"Records a session in a separate stats object on a temporary file, saves, loads it back and compares.", AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			return Script.of(ctx -> {
				try (TempStats temp = new TempStats()) {
					AfkStats a = temp.open();
					a.startSession();
					temp.advance(10);
					a.restartStarted(false);
					temp.advance(5);
					a.restartEnded(false);
					a.blockMined();
					a.blockMined();
					a.blockMined();
					temp.advance(5);
					SessionRecord ended = a.endSession(EndReason.MANUAL_TOGGLE);
					ctx.check(Files.exists(temp.file), "The temporary stats file was not written");

					AfkStats b = temp.open();
					LifetimeStats l = b.getLifetime();
					List<SessionRecord> recent = b.getRecentSessions();
					ctx.check(l.sessions == 1 && l.restarts == 1 && l.blocksMined == 3, "Lifetime totals didn't survive the round trip: "
							+ l.sessions + " sessions, " + l.restarts + " restarts, " + l.blocksMined + " blocks");
					ctx.check(recent.size() == 1, "Expected 1 recent session after loading, got " + recent.size());
					SessionRecord loaded = recent.getFirst();
					ctx.check(loaded.wallMs == ended.wallMs && loaded.restartMs == ended.restartMs && loaded.activeMs == ended.activeMs
									&& loaded.endReason == EndReason.MANUAL_TOGGLE,
							"The loaded session differs: wall " + loaded.wallMs + " vs " + ended.wallMs + ", restart " + loaded.restartMs
									+ " vs " + ended.restartMs + ", end " + loaded.endReason);
					ctx.pass("Saved and reloaded a 20 s session with a 5 s restart and 3 blocks: totals, history and times match. "
							+ "(Temporary file, the real stats file was not touched.)");
				}
			});
		}
	}

	public static final class F3 extends Scenario {
		public F3() {
			super("F3", Group.F, "Corrupt file handling",
					"Loads a deliberately corrupt temporary stats file: it must start fresh, not crash, move the bad file "
							+ "aside as .corrupt and still save afterwards.", AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			return Script.of(ctx -> {
				try (TempStats temp = new TempStats()) {
					Files.writeString(temp.file, "{ this is not valid json", StandardCharsets.UTF_8);
					AfkStats stats = temp.open();
					ctx.check(stats.getLifetime().sessions == 0 && stats.getRecentSessions().isEmpty(), "A corrupt file didn't start fresh");
					Path corrupt = temp.file.resolveSibling(temp.file.getFileName() + ".corrupt");
					ctx.check(Files.exists(corrupt), "The corrupt file was not moved aside as .corrupt");
					ctx.check(stats.saveNow(), "Saving after a corrupt file failed");
					ctx.pass("A corrupt stats file loaded as fresh stats without an error, was moved aside as .corrupt, and saving "
							+ "worked again. (Temporary file.)");
				} catch (IOException e) {
					ctx.fail("Could not prepare the temporary file: " + e);
				}
			});
		}
	}

	public static final class F4 extends Scenario {
		public F4() {
			super("F4", Group.F, "Clear test data",
					"Clears every test-flagged session, counter and event. Real data is untouched.", AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			return Script.of(ctx -> {
				AfkStats stats = ctx.env().stats();
				int sessions = stats.getTestSessions().size();
				long events = stats.getRecentEvents(AfkStats.MAX_EVENTS).stream().filter(StatsEvent::test).count();
				int lifetime = stats.getLifetime().sessions;
				stats.clearTestData();
				ctx.check(stats.getTestSessions().isEmpty() && stats.getTestCounters() == null, "Test sessions or counters are left");
				ctx.check(stats.getRecentEvents(AfkStats.MAX_EVENTS).stream().noneMatch(StatsEvent::test), "Test events are left");
				ctx.check(stats.getLifetime().sessions == lifetime, "The real lifetime totals changed");
				ctx.pass("Cleared " + sessions + " test session(s), the test counters and " + events + " test event(s); real data untouched.");
			});
		}
	}
}
