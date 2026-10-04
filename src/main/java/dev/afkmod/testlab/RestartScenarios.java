package dev.afkmod.testlab;

import dev.afkmod.logic.AfkStateMachine.Reason;
import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.logic.DurationParser;
import dev.afkmod.logic.KeywordMatcher;
import dev.afkmod.logic.MessageSource;
import dev.afkmod.stats.SessionRecord;
import dev.afkmod.testlab.ConfigOverrides.Key;
import dev.afkmod.testlab.TestContext.Transition;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

import static dev.afkmod.testlab.TestContext.s;
import static dev.afkmod.testlab.TestContext.seconds;

/**
 * B. Restart flow. Every message goes through the real handler, the queue text becomes the real action bar text (read
 * back by the real periodic check), and the reconnect is the mod's real disconnect/join handling without any network
 * traffic. Each scenario asserts the transitions in order, that left and right click are released and the timer is
 * paused during the restart, and that crouch/left/right are active again after resuming.
 */
public final class RestartScenarios {
	static final String CHAT_MESSAGE = "[TEST] Servers are updating, restarting soon";

	private RestartScenarios() {
	}

	static String queueText(int n) {
		return "restart queue #" + Math.max(1, n) + " (TEST)";
	}

	/** Measurements of one restart run. */
	static final class Run {
		int mark;
		int count0;
		long testRestarts0;
		int queueN = 20;
		long restartAt;
		long lastQueueAt;
		long textGoneAt;
		long joinAt;
		long settlingAt;
		long activeAt;
		long keysBackAt;
		long timerAtRestart;
		long timerAtActive;
	}

	static long testRestarts(TestContext ctx) {
		SessionRecord counters = ctx.env().stats().getTestCounters();
		return counters == null ? 0 : counters.restarts;
	}

	// ---- building blocks (also used by the timer scenario E2) ----

	static void begin(TestContext ctx, Run r) {
		r.mark = ctx.mark();
		r.count0 = ctx.machine().restartCount();
		r.testRestarts0 = testRestarts(ctx);
	}

	/** Sends the chat message through the real handler; it must start the restart at once. */
	static void startWithChat(Script.Builder b, Run r) {
		b.run(ctx -> {
			begin(ctx, r);
			KeywordMatcher.Result result = ctx.env().injectMessage(MessageSource.SYSTEM, CHAT_MESSAGE);
			afterStart(ctx, r, result, "The chat message");
		});
	}

	static void afterStart(TestContext ctx, Run r, KeywordMatcher.Result result, String what) {
		ctx.check(ctx.state() == State.RESTARTING,
				what + " didn't start a restart (handler result " + result + ", state " + ctx.state() + ")");
		r.restartAt = ctx.now();
		r.timerAtRestart = ctx.machine().timerRemainingNanos();
		assertRestarting(ctx, r, "right after the restart began");
	}

	static void assertRestarting(TestContext ctx, Run r, String when) {
		ctx.check(ctx.state() == State.RESTARTING,
				"Expected RESTARTING " + when + " but it is " + ctx.state() + " (" + ctx.describe(r.mark) + ")");
		ctx.check(ctx.leftRightReleased(), "Left/right click still active " + when + " (" + ctx.keys() + ")");
		ctx.check(ctx.machine().isTimerPaused(), "The AFK timer is not paused " + when);
	}

	/** Shows the queue text as the real action bar, refreshed every second, asserting RESTARTING each time. */
	static void queueText(Script.Builder b, Run r, ToDoubleFunction<TestContext> seconds, Consumer<TestContext> extra) {
		b.repeat(seconds, 1.0, ctx -> {
			ctx.env().injectMessage(MessageSource.ACTION_BAR, queueText(r.queueN--));
			r.lastQueueAt = ctx.now();
			assertRestarting(ctx, r, "while the queue text shows");
			if (extra != null) extra.accept(ctx);
		});
	}

	static void queueText(Script.Builder b, Run r, double seconds) {
		queueText(b, r, ctx -> seconds, null);
	}

	/** Waits until the periodic check can no longer see the queue text (vanilla shows it 3 s after the last one). */
	static void waitTextGone(Script.Builder b, Run r) {
		b.waitUntil("the queue text to leave the screen", ctx -> {
			if (ctx.env().queueTextVisible()) return false;
			r.textGoneAt = ctx.now();
			return true;
		}, 6.0);
		b.run(ctx -> assertRestarting(ctx, r, "right after the queue text left the screen"));
	}

	/** The mod-side handling of the server's reconnect: the disconnect, 1 s on the "connecting" screen, the rejoin. */
	static void simulatedReconnect(Script.Builder b, Run r) {
		b.run(ctx -> {
			ctx.env().simulateServerDisconnect();
			ctx.check(ctx.state() == State.RESTARTING && ctx.machine().isAwaitingRejoin(),
					"After the simulated server disconnect expected RESTARTING waiting for the rejoin, got " + ctx.state());
		});
		b.waitSeconds(1.0);
		b.run(ctx -> {
			ctx.env().simulateJoin();
			r.joinAt = ctx.now();
			ctx.check(ctx.state() == State.RESTARTING || ctx.state() == State.SETTLING,
					"After the simulated rejoin expected RESTARTING, got " + ctx.state());
		});
	}

	static void waitSettling(Script.Builder b, Run r, ToDoubleFunction<TestContext> timeout) {
		b.waitUntil("SETTLING", ctx -> {
			State state = ctx.state();
			if (state == State.SETTLING || state == State.ACTIVE) {
				r.settlingAt = ctx.now();
				return true;
			}
			if (state != State.RESTARTING) ctx.fail("Expected RESTARTING until the restart ends, got " + state + " (" + ctx.describe(r.mark) + ")");
			ctx.check(ctx.leftRightReleased(), "Left/right click active during the restart (" + ctx.keys() + ")");
			return false;
		}, timeout);
	}

	static void waitActive(Script.Builder b, Run r) {
		b.waitUntil("ACTIVE after the settle delay", ctx -> {
			State state = ctx.state();
			if (state == State.ACTIVE) {
				r.activeAt = ctx.now();
				r.timerAtActive = ctx.machine().timerRemainingNanos();
				return true;
			}
			if (state != State.SETTLING) ctx.fail("Expected SETTLING until resuming, got " + state + " (" + ctx.describe(r.mark) + ")");
			ctx.check(ctx.leftRightReleased(), "Left/right click active while settling (" + ctx.keys() + ")");
			ctx.check(ctx.machine().isTimerPaused(), "The AFK timer runs while settling");
			return false;
		}, ctx -> ctx.config().settleDelaySeconds + 2 * ctx.interval() + 2);
	}

	static void waitKeysBack(Script.Builder b, Run r) {
		b.waitUntil("crouch, left and right click to be active again", ctx -> {
			if (!ctx.allKeysActive()) return false;
			r.keysBackAt = ctx.now();
			return true;
		}, ctx -> 2 * ctx.interval() + 0.5);
	}

	/**
	 * The checks every resumed restart must pass. Returns the measured details for the result text.
	 *
	 * @param endReason why the restart ended (REJOINED or NO_RECONNECT_FALLBACK)
	 */
	static String checkResumed(TestContext ctx, Run r, Reason endReason) {
		ctx.expectStates(r.mark, State.RESTARTING, State.SETTLING, State.ACTIVE);
		Transition settling = ctx.firstTo(State.SETTLING, r.mark);
		ctx.check(settling.reason() == endReason, "Expected the restart to end with " + endReason + " but saw " + settling);
		int dCount = ctx.machine().restartCount() - r.count0;
		long dTest = testRestarts(ctx) - r.testRestarts0;
		ctx.check(dCount == 1, "Expected the restart count to go up by exactly 1, got +" + dCount);
		ctx.check(dTest == 1, "Expected exactly 1 restart in the test data, got +" + dTest);
		ctx.check(ctx.machine().isInPostResumeCooldown() || ctx.config().postResumeCooldownSeconds == 0,
				"The post-resume cooldown did not start");
		int settle = ctx.config().settleDelaySeconds;
		double settled = seconds(r.activeAt - r.settlingAt);
		ctx.check(settled >= settle - 0.05 && settled <= settle + ctx.interval() + 0.3, String.format(Locale.ROOT,
				"Expected resume %d s after SETTLING (settleDelaySeconds, + up to one check), got %.1f s", settle, settled));
		double frozen = Math.abs(seconds(r.timerAtRestart - r.timerAtActive));
		ctx.check(frozen <= 0.5, String.format(Locale.ROOT, "The timer moved %.1f s during the restart (it must be paused)", frozen));
		return String.format(Locale.ROOT, "%s. Settle %.1f s (settleDelaySeconds %d). Restart count +1, test data +1, "
						+ "timer frozen%s, cooldown %s, C L R back %.1f s after resuming",
				ctx.describe(r.mark), settled, settle,
				ctx.machine().hasTimer() ? " at " + DurationParser.format(TimeUnit.NANOSECONDS.toSeconds(r.timerAtActive)) : " (no timer, paused state checked)",
				ctx.config().postResumeCooldownSeconds == 0 ? "0 s" : "started",
				seconds(r.keysBackAt - r.activeAt));
	}

	/** The time from the queue text leaving the screen to SETTLING must match {@code expected} (+- one check). */
	static String checkGoneTiming(TestContext ctx, Run r, double expected, String what) {
		double gone = seconds(r.settlingAt - r.textGoneAt);
		ctx.check(gone >= expected - ctx.interval() - 0.1 && gone <= expected + ctx.interval() + 0.5, String.format(Locale.ROOT,
				"Expected the restart to end about %.1f s (%s) after the queue text left the screen, got %.1f s", expected, what, gone));
		return String.format(Locale.ROOT, "Queue text gone -> SETTLING after %.1f s (expected %.1f s, %s, +- one check)", gone, expected, what);
	}

	/** The full flow used by B1 and E2: chat, queue text, text gone, simulated reconnect, settle, resume. */
	static void fullFlowWithReconnect(Script.Builder b, Run r, ToDoubleFunction<TestContext> queueSeconds) {
		startWithChat(b, r);
		queueText(b, r, queueSeconds, null);
		waitTextGone(b, r);
		simulatedReconnect(b, r);
		waitSettling(b, r, ctx -> ctx.config().queueGoneSeconds + 2 * ctx.interval() + 3);
		waitActive(b, r);
		waitKeysBack(b, r);
	}

	// ---- scenarios ----

	public static final class B1 extends Scenario {
		public B1() {
			super("B1", Group.B, "Full flow with reconnect",
					"Chat message -> action bar \"restart queue #N (TEST)\" every second for testRestartSeconds (default 15) "
							+ "-> text stops -> simulated reconnect -> settle delay -> resume. Checks the transitions, released keys, "
							+ "paused timer, restart count +1 (test data), cooldown and keys back on.",
					MOD_ON | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			Run r = new Run();
			Script.Builder b = Script.builder();
			fullFlowWithReconnect(b, r, ctx -> o.testRestartSeconds);
			b.run(ctx -> {
				String gone = checkGoneTiming(ctx, r, ctx.config().queueGoneSeconds, "queueGoneSeconds");
				ctx.check(r.settlingAt >= r.joinAt, "The restart ended before the rejoin");
				ctx.pass(o.testRestartSeconds + " s of queue text. " + gone + ". " + checkResumed(ctx, r, Reason.REJOINED));
			});
			return b.build();
		}
	}

	public static final class B2 extends Scenario {
		public B2() {
			super("B2", Group.B, "No reconnect (fallback)",
					"The queue text ends and nothing reconnects: the fallback must resume after noReconnectFallbackSeconds "
							+ "(overridden to 5 s; queueGoneSeconds 2, settle 1 s).",
					MOD_ON | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			Run r = new Run();
			Script.Builder b = Script.builder();
			b.run(ctx -> {
				ctx.overrides().set(Key.QUEUE_GONE_SECONDS, 2);
				ctx.overrides().set(Key.NO_RECONNECT_FALLBACK_SECONDS, 5);
				ctx.overrides().set(Key.SETTLE_DELAY_SECONDS, 1);
			});
			startWithChat(b, r);
			queueText(b, r, 4);
			waitTextGone(b, r);
			waitSettling(b, r, ctx -> ctx.config().noReconnectFallbackSeconds + 2 * ctx.interval() + 3);
			waitActive(b, r);
			waitKeysBack(b, r);
			b.run(ctx -> {
				String gone = checkGoneTiming(ctx, r, ctx.config().noReconnectFallbackSeconds, "noReconnectFallbackSeconds");
				ctx.pass("No reconnect. " + gone + ". " + checkResumed(ctx, r, Reason.NO_RECONNECT_FALLBACK));
			});
			return b.build();
		}
	}

	public static final class B3 extends Scenario {
		public B3() {
			super("B3", Group.B, "Queue text only",
					"The chat message is missed; only the action bar queue text arrives. It must still enter RESTARTING "
							+ "(then resumes via the fallback, overridden short).",
					MOD_ON | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			Run r = new Run();
			Script.Builder b = Script.builder();
			b.run(ctx -> {
				ctx.overrides().set(Key.QUEUE_GONE_SECONDS, 2);
				ctx.overrides().set(Key.NO_RECONNECT_FALLBACK_SECONDS, 3);
				ctx.overrides().set(Key.SETTLE_DELAY_SECONDS, 1);
				begin(ctx, r);
				KeywordMatcher.Result result = ctx.env().injectMessage(MessageSource.ACTION_BAR, queueText(r.queueN--));
				afterStart(ctx, r, result, "The action bar queue text alone");
				Transition t = ctx.firstTo(State.RESTARTING, r.mark);
				ctx.check(t != null && t.reason() == Reason.RESTART_DETECTED, "Expected ACTIVE -> RESTARTING (RESTART_DETECTED), saw " + ctx.describe(r.mark));
			});
			queueText(b, r, 3);
			waitTextGone(b, r);
			waitSettling(b, r, ctx -> ctx.config().noReconnectFallbackSeconds + 2 * ctx.interval() + 3);
			waitActive(b, r);
			waitKeysBack(b, r);
			b.run(ctx -> ctx.pass("Entered RESTARTING on the action bar text alone. "
					+ checkResumed(ctx, r, Reason.NO_RECONNECT_FALLBACK)));
			return b.build();
		}
	}

	public static final class B4 extends Scenario {
		public B4() {
			super("B4", Group.B, "Reconnect before the text ends",
					"The simulated reconnect happens while the queue text still shows: the mod must stay RESTARTING until the "
							+ "text has been gone for queueGoneSeconds (3 s).",
					MOD_ON | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			Run r = new Run();
			Script.Builder b = Script.builder();
			b.run(ctx -> {
				ctx.overrides().set(Key.QUEUE_GONE_SECONDS, 3);
				ctx.overrides().set(Key.NO_RECONNECT_FALLBACK_SECONDS, 60);
				ctx.overrides().set(Key.SETTLE_DELAY_SECONDS, 1);
			});
			startWithChat(b, r);
			queueText(b, r, 3);
			b.run(ctx -> {
				ctx.env().simulateServerDisconnect();
				ctx.check(ctx.machine().isAwaitingRejoin(), "Expected RESTARTING waiting for the rejoin, got " + ctx.state());
			});
			queueText(b, r, 1);
			b.run(ctx -> {
				ctx.env().simulateJoin();
				r.joinAt = ctx.now();
				ctx.check(ctx.machine().hasRejoinedDuringRestart(), "The rejoin wasn't registered (state " + ctx.state() + ")");
			});
			// The text keeps showing after the rejoin: RESTARTING is asserted every second.
			queueText(b, r, 6);
			waitTextGone(b, r);
			waitSettling(b, r, ctx -> ctx.config().queueGoneSeconds + 2 * ctx.interval() + 3);
			waitActive(b, r);
			waitKeysBack(b, r);
			b.run(ctx -> {
				String gone = checkGoneTiming(ctx, r, ctx.config().queueGoneSeconds, "queueGoneSeconds");
				ctx.pass(String.format(Locale.ROOT, "Rejoined while the text showed; stayed RESTARTING through %s more of text. ",
						s(seconds(r.textGoneAt - r.joinAt))) + gone + ". " + checkResumed(ctx, r, Reason.REJOINED));
			});
			return b.build();
		}
	}

	public static final class B5 extends Scenario {
		public B5() {
			super("B5", Group.B, "Repeated message",
					"The restart message and the queue text keep arriving while already RESTARTING: only one restart may be counted.",
					MOD_ON | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			Run r = new Run();
			Script.Builder b = Script.builder();
			b.run(ctx -> {
				ctx.overrides().set(Key.QUEUE_GONE_SECONDS, 1);
				ctx.overrides().set(Key.NO_RECONNECT_FALLBACK_SECONDS, 2);
				ctx.overrides().set(Key.SETTLE_DELAY_SECONDS, 1);
			});
			startWithChat(b, r);
			queueText(b, r, ctx -> 3, ctx -> {
				KeywordMatcher.Result result = ctx.env().injectMessage(MessageSource.SYSTEM, CHAT_MESSAGE);
				ctx.check(result == KeywordMatcher.Result.RESTART, "The repeated message was classified " + result);
			});
			waitTextGone(b, r);
			waitSettling(b, r, ctx -> ctx.config().noReconnectFallbackSeconds + 2 * ctx.interval() + 3);
			waitActive(b, r);
			waitKeysBack(b, r);
			b.run(ctx -> {
				long detected = ctx.count(r.mark, Reason.RESTART_DETECTED);
				ctx.check(detected == 1, "Expected one RESTART_DETECTED transition, saw " + detected + ": " + ctx.describe(r.mark));
				ctx.pass("4 restart messages and 3 queue texts counted as 1 restart. " + checkResumed(ctx, r, Reason.NO_RECONNECT_FALLBACK));
			});
			return b.build();
		}
	}

	public static final class B6 extends Scenario {
		static final double SHORT_GAP = 1.5;

		public B6() {
			super("B6", Group.B, "Flicker",
					"After the rejoin, a queue-text gap of 1.5 s (shorter than queueGoneSeconds 3) must NOT end the restart; "
							+ "a gap longer than 3 s must.",
					MOD_ON | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			Run r = new Run();
			long[] gap = new long[2];
			Script.Builder b = Script.builder();
			b.run(ctx -> {
				ctx.overrides().set(Key.QUEUE_GONE_SECONDS, 3);
				ctx.overrides().set(Key.NO_RECONNECT_FALLBACK_SECONDS, 60);
				ctx.overrides().set(Key.SETTLE_DELAY_SECONDS, 1);
			});
			startWithChat(b, r);
			queueText(b, r, 2);
			b.run(ctx -> {
				ctx.env().simulateServerDisconnect();
				ctx.env().simulateJoin();
				r.joinAt = ctx.now();
			});
			queueText(b, r, 2);
			b.run(ctx -> {
				ctx.env().clearActionBar();
				gap[0] = ctx.now();
			});
			b.waitUntil("the short gap", ctx -> {
				assertRestarting(ctx, r, "during the short gap");
				return ctx.secondsSince(gap[0]) >= SHORT_GAP;
			}, SHORT_GAP + 2);
			queueText(b, r, 2);
			b.run(ctx -> {
				ctx.env().clearActionBar();
				gap[1] = ctx.now();
				r.textGoneAt = gap[1];
			});
			waitSettling(b, r, ctx -> ctx.config().queueGoneSeconds + 2 * ctx.interval() + 3);
			waitActive(b, r);
			waitKeysBack(b, r);
			b.run(ctx -> {
				String gone = checkGoneTiming(ctx, r, ctx.config().queueGoneSeconds, "queueGoneSeconds");
				ctx.pass(String.format(Locale.ROOT, "A %.1f s gap kept RESTARTING; the long gap ended it. ", SHORT_GAP)
						+ gone + ". " + checkResumed(ctx, r, Reason.REJOINED));
			});
			return b.build();
		}
	}

	public static final class B7 extends Scenario {
		public B7() {
			super("B7", Group.B, "Message during the cooldown",
					"A short restart, then a restart message and the queue text during the post-resume cooldown "
							+ "(overridden to 10 s): both must be ignored.",
					MOD_ON | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			Run r = new Run();
			int[] after = new int[2];
			Script.Builder b = Script.builder();
			b.run(ctx -> {
				ctx.overrides().set(Key.QUEUE_GONE_SECONDS, 1);
				// Longer than the 1 s check interval, so the restart can't end on the same check the text vanishes.
				ctx.overrides().set(Key.NO_RECONNECT_FALLBACK_SECONDS, 2);
				ctx.overrides().set(Key.SETTLE_DELAY_SECONDS, 1);
				ctx.overrides().set(Key.POST_RESUME_COOLDOWN_SECONDS, 10);
			});
			startWithChat(b, r);
			queueText(b, r, 2);
			waitTextGone(b, r);
			waitSettling(b, r, ctx -> ctx.config().noReconnectFallbackSeconds + 2 * ctx.interval() + 3);
			waitActive(b, r);
			b.run(ctx -> {
				ctx.check(ctx.machine().isInPostResumeCooldown(), "The post-resume cooldown is not active after resuming");
				after[0] = ctx.mark();
				after[1] = ctx.machine().restartCount();
				KeywordMatcher.Result chat = ctx.env().injectMessage(MessageSource.SYSTEM, CHAT_MESSAGE);
				ctx.check(ctx.state() == State.ACTIVE, "The restart message during the cooldown started a restart (" + ctx.describe(after[0]) + ")");
				ctx.env().injectMessage(MessageSource.ACTION_BAR, queueText(1));
				ctx.check(ctx.state() == State.ACTIVE, "The queue text during the cooldown started a restart (" + ctx.describe(after[0]) + ")");
				ctx.check(chat == KeywordMatcher.Result.RESTART, "The message wasn't even recognised (" + chat + ")");
			});
			// One more periodic check with the queue text on screen must not start anything either.
			b.waitSeconds(ctx -> ctx.interval() + 0.2);
			b.run(ctx -> {
				ctx.env().clearActionBar();
				ctx.check(ctx.state() == State.ACTIVE && ctx.since(after[0]).isEmpty(),
						"Something happened during the cooldown: " + ctx.describe(after[0]));
				ctx.check(ctx.machine().restartCount() == after[1], "The restart count changed during the cooldown");
				ctx.pass("During the post-resume cooldown (10 s) a restart message and the queue text were recognised but "
						+ "ignored: still ACTIVE, restart count unchanged.");
			});
			return b.build();
		}
	}

	public static final class B8 extends Scenario {
		static final String WHISPER = "[TEST] Bob whispered to you: servers are updating";
		static final String WHISPER_QUEUE = "whispered restart queue #1 (TEST)";

		public B8() {
			super("B8", Group.B, "Whispered message",
					"\"whispered\" + \"servers\" (chat) and a whispered queue text (action bar) must be ignored by the real handler.",
					MOD_ON | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			int[] start = new int[2];
			return Script.builder()
					.run(ctx -> {
						start[0] = ctx.mark();
						start[1] = ctx.machine().restartCount();
						KeywordMatcher.Result chat = ctx.env().injectMessage(MessageSource.SYSTEM, WHISPER);
						ctx.check(chat == KeywordMatcher.Result.IGNORED, "The whisper was classified " + chat + ", expected IGNORED");
						KeywordMatcher.Result bar = ctx.env().injectMessage(MessageSource.ACTION_BAR, WHISPER_QUEUE);
						ctx.check(bar == KeywordMatcher.Result.IGNORED, "The whispered queue text was classified " + bar + ", expected IGNORED");
						ctx.check(ctx.state() == State.ACTIVE, "A whisper changed the state: " + ctx.describe(start[0]));
					})
					.waitSeconds(ctx -> ctx.interval() + 0.2)
					.run(ctx -> {
						ctx.env().clearActionBar();
						ctx.check(ctx.state() == State.ACTIVE && ctx.since(start[0]).isEmpty(), "A whisper changed the state: " + ctx.describe(start[0]));
						ctx.check(ctx.machine().restartCount() == start[1], "A whisper was counted as a restart");
						ctx.pass("\"" + WHISPER + "\" and \"" + WHISPER_QUEUE + "\" were IGNORED; still ACTIVE, nothing counted.");
					})
					.build();
		}
	}

	public static final class B9 extends Scenario {
		public B9() {
			super("B9", Group.B, "Never reconnects",
					"Restart, simulated server disconnect, no rejoin ever. With maxRestartWait overridden to 6 s the mod must "
							+ "turn OFF, release everything and NOT disconnect.",
					MOD_ON | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			Run r = new Run();
			int[] disconnects = new int[2];
			Script.Builder b = Script.builder();
			b.run(ctx -> {
				ctx.overrides().set(Key.MAX_RESTART_WAIT_SECONDS, 6);
				disconnects[0] = ctx.env().realDisconnectCount();
				disconnects[1] = ctx.env().wouldDisconnectCount();
			});
			startWithChat(b, r);
			b.run(ctx -> {
				ctx.env().simulateServerDisconnect();
				ctx.check(ctx.machine().isAwaitingRejoin(), "Expected RESTARTING waiting for the rejoin, got " + ctx.state());
			});
			b.waitUntil("the max wait to turn the mod OFF", ctx -> {
				if (ctx.state() == State.OFF) {
					r.settlingAt = ctx.now();
					return true;
				}
				assertRestarting(ctx, r, "while waiting for a rejoin that never comes");
				return false;
			}, ctx -> ctx.config().maxRestartWaitSeconds() + 2 * ctx.interval() + 2);
			b.waitUntil("everything to be released", TestContext::noKeysActive, 1.5);
			b.run(ctx -> {
				ctx.check(ctx.machine().lastReason() == Reason.RESTART_TIMEOUT, "Expected OFF (RESTART_TIMEOUT), got " + ctx.machine().lastReason());
				double waited = seconds(r.settlingAt - r.restartAt);
				long max = ctx.config().maxRestartWaitSeconds();
				ctx.check(waited >= max - 0.05 && waited <= max + ctx.interval() + 0.5, String.format(Locale.ROOT,
						"Expected OFF %d s after the restart began (+ up to one check), got %.1f s", max, waited));
				ctx.check(ctx.env().realDisconnectCount() == disconnects[0], "The mod really disconnected");
				ctx.check(ctx.env().wouldDisconnectCount() == disconnects[1], "The mod tried to disconnect (it must do nothing else)");
				ctx.check(ctx.env().inWorld(), "No longer in the world");
				ctx.pass(String.format(Locale.ROOT, "No rejoin: OFF (RESTART_TIMEOUT) %.1f s after the restart began (max wait %d s), "
						+ "everything released (%s), no disconnect, still in the world.", waited, max, ctx.keys()));
			});
			return b.build();
		}
	}

	public static final class B10 extends Scenario {
		public B10() {
			super("B10", Group.B, "Manual disconnect (simulated)",
					"Runs the client-initiated disconnect handling (the pause menu Disconnect path, classified by the real "
							+ "DisconnectTracker): the mod must turn OFF at once. Nothing is really disconnected.",
					MOD_ON | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			int[] start = new int[2];
			return Script.builder()
					.run(ctx -> {
						start[0] = ctx.mark();
						start[1] = ctx.env().realDisconnectCount();
						ctx.env().simulateClientDisconnect();
						ctx.check(ctx.state() == State.OFF, "Expected OFF at once, got " + ctx.state());
						ctx.check(ctx.machine().lastReason() == Reason.MANUAL_DISCONNECT,
								"Expected MANUAL_DISCONNECT, got " + ctx.machine().lastReason());
					})
					.waitUntil("everything to be released", TestContext::noKeysActive, 1.5)
					.run(ctx -> {
						ctx.check(ctx.env().inWorld() && ctx.env().realDisconnectCount() == start[1], "Something really disconnected");
						ctx.pass("The client-initiated disconnect handling turned the mod OFF at once (" + ctx.describe(start[0])
								+ "), everything released; nothing was disconnected.");
					})
					.build();
		}
	}

	public static final class B11 extends Scenario {
		public B11() {
			super("B11", Group.B, "Server reconnect while ACTIVE",
					"Simulated server-initiated disconnect with no restart message: the mod waits (reconnectGraceSeconds "
							+ "overridden to 5 s) and stays ON when the rejoin arrives; a second time with no rejoin it turns OFF "
							+ "when the grace expires.",
					MOD_ON | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			int[] marks = new int[2];
			long[] times = new long[3];
			String[] firstHalf = new String[1];
			return Script.builder()
					.run(ctx -> {
						ctx.overrides().set(Key.RECONNECT_GRACE_SECONDS, 5);
						ctx.overrides().set(Key.SETTLE_DELAY_SECONDS, 1);
						marks[0] = ctx.mark();
						ctx.env().simulateServerDisconnect();
						ctx.check(ctx.state() == State.RECONNECTING, "Expected RECONNECTING after an unexplained disconnect, got " + ctx.state());
						ctx.check(ctx.machine().isTimerPaused(), "The timer is not paused while reconnecting");
						times[0] = ctx.now();
					})
					.waitUntil("2 s in RECONNECTING", ctx -> {
						ctx.check(ctx.state() == State.RECONNECTING, "Expected to keep waiting in RECONNECTING, got " + ctx.state());
						return ctx.secondsSince(times[0]) >= 2;
					}, 4)
					.run(ctx -> {
						ctx.env().simulateJoin();
						ctx.check(ctx.state() == State.SETTLING, "Expected SETTLING after the rejoin, got " + ctx.state());
					})
					.waitUntil("ACTIVE after the rejoin", ctx -> ctx.state() == State.ACTIVE,
							ctx -> ctx.config().settleDelaySeconds + 2 * ctx.interval() + 2)
					.run(ctx -> {
						ctx.expectStates(marks[0], State.RECONNECTING, State.SETTLING, State.ACTIVE);
						firstHalf[0] = ctx.describe(marks[0]);
						marks[1] = ctx.mark();
						ctx.env().simulateServerDisconnect();
						ctx.check(ctx.state() == State.RECONNECTING, "Expected RECONNECTING again, got " + ctx.state());
						times[1] = ctx.now();
					})
					.waitUntil("the grace period to expire", ctx -> {
						if (ctx.state() == State.OFF) {
							times[2] = ctx.now();
							return true;
						}
						ctx.check(ctx.state() == State.RECONNECTING, "Expected RECONNECTING until the grace expires, got " + ctx.state());
						return false;
					}, ctx -> ctx.config().reconnectGraceSeconds + 2 * ctx.interval() + 2)
					.run(ctx -> {
						ctx.check(ctx.machine().lastReason() == Reason.GRACE_EXPIRED, "Expected OFF (GRACE_EXPIRED), got " + ctx.machine().lastReason());
						double waited = seconds(times[2] - times[1]);
						int grace = ctx.config().reconnectGraceSeconds;
						ctx.check(waited >= grace - 0.05 && waited <= grace + ctx.interval() + 0.5, String.format(Locale.ROOT,
								"Expected OFF %d s after the disconnect (+ up to one check), got %.1f s", grace, waited));
						ctx.pass(String.format(Locale.ROOT, "Rejoin within the grace: stayed ON (%s). No rejoin: OFF (GRACE_EXPIRED) "
								+ "after %.1f s (grace %d s).", firstHalf[0], waited, grace));
					})
					.build();
		}
	}
}
