package dev.afkmod.testlab;

import dev.afkmod.logic.AfkStateMachine.Reason;
import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.logic.DurationParser;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static dev.afkmod.testlab.TestContext.seconds;

/** E. Timer. Test timers live in the override layer; the saved timer and the real countdown are put back afterwards. */
public final class TimerScenarios {
	private TimerScenarios() {
	}

	/** E1: a short timer through the real timer-end path. */
	public static final class E1 extends Scenario {
		public E1() {
			super("E1", Group.E, "Short timer",
					"A short timer (default 10 s) runs out through the real timer-end path: keys released, crouch off, mod OFF. "
							+ "\"Dry end\" replaces only the final disconnect with a logged WOULD DISCONNECT; \"full\" includes the "
							+ "real disconnect to the server list and needs confirmation.",
					MOD_ON | AUTO);
		}

		@Override
		public boolean needsConfirm(TestOptions options) {
			return options.fullTimerEnd;
		}

		@Override
		public Script script(TestOptions o) {
			long[] t = new long[2];
			int[] counts = new int[2];
			Script.Builder b = Script.builder();
			b.run(ctx -> {
				counts[0] = ctx.env().realDisconnectCount();
				counts[1] = ctx.env().wouldDisconnectCount();
				if (o.fullTimerEnd) {
					ctx.allowRealDisconnect();
					ctx.expectRealDisconnect(c -> {
						c.check(c.machine().lastReason() == Reason.TIMER_END, "Disconnected, but the mod's last reason is " + c.machine().lastReason());
						c.pass(String.format(Locale.ROOT, "The %d s timer ended after %.1f s and the mod really disconnected to the "
								+ "server list (confirmed).", o.timerSeconds, seconds(t[1] - t[0])));
					});
				}
				ctx.startTestTimer(o.timerSeconds);
				t[0] = ctx.now();
			});
			b.waitUntil("the timer to end (mod OFF)", ctx -> {
				State state = ctx.state();
				if (state == State.OFF) {
					t[1] = ctx.now();
					return true;
				}
				ctx.check(state == State.ACTIVE || state == State.RECOVERING, "The timer test was interrupted: the mod is " + state);
				return false;
			}, ctx -> o.timerSeconds + 2 * ctx.interval() + 2);
			b.run(ctx -> {
				ctx.check(ctx.machine().lastReason() == Reason.TIMER_END, "Expected OFF (TIMER_END), got " + ctx.machine().lastReason());
				double took = seconds(t[1] - t[0]);
				ctx.check(took >= o.timerSeconds - 0.05 && took <= o.timerSeconds + ctx.interval() + 0.3, String.format(Locale.ROOT,
						"Expected the timer to end after %d s (+ up to one %.1f s check), got %.1f s", o.timerSeconds, ctx.interval(), took));
			});
			if (o.fullTimerEnd) {
				b.waitUntil("the real disconnect", ctx -> false, 10);
				return b.build();
			}
			b.waitUntil("crouch, left and right click to be released", TestContext::noKeysActive, 1.5);
			b.run(ctx -> {
				ctx.check(ctx.env().wouldDisconnectCount() == counts[1] + 1, "The timer-end path didn't reach the final disconnect");
				ctx.check(ctx.env().realDisconnectCount() == counts[0] && ctx.env().inWorld(), "The dry end really disconnected");
				ctx.pass(String.format(Locale.ROOT, "The %d s timer ended after %.1f s (+ up to one %.1f s check): OFF (TIMER_END), "
								+ "%s, \"WOULD DISCONNECT\" logged instead of the disconnect, still in the world.",
						o.timerSeconds, seconds(t[1] - t[0]), ctx.interval(), ctx.keys()));
			});
			return b.build();
		}
	}

	/** E2: the timer is frozen for exactly the restart's duration. */
	public static final class E2 extends Scenario {
		static final int TIMER = 30;
		static final int RUN_BEFORE = 5;
		static final int RUN_AFTER = 2;
		static final int QUEUE_SECONDS = 8;

		public E2() {
			super("E2", Group.E, "Timer pause",
					"Starts a 30 s timer, runs the B1 restart flow at about 5 s, and checks the remaining time is frozen during "
							+ "the restart and counts down again afterwards (PASS when the paused time matches the restart within 1 s).",
					MOD_ON | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			RestartScenarios.Run r = new RestartScenarios.Run();
			long[] timerStart = new long[1];
			Script.Builder b = Script.builder();
			b.run(ctx -> {
				ctx.startTestTimer(TIMER);
				timerStart[0] = ctx.now();
			});
			b.waitSeconds(RUN_BEFORE);
			RestartScenarios.fullFlowWithReconnect(b, r, ctx -> Math.min(o.testRestartSeconds, QUEUE_SECONDS));
			b.waitSeconds(RUN_AFTER);
			b.run(ctx -> {
				ctx.check(ctx.state() == State.ACTIVE, "Expected ACTIVE after the restart, got " + ctx.state());
				long remaining = ctx.machine().timerRemainingNanos();
				double wall = seconds(ctx.now() - timerStart[0]);
				double consumed = TIMER - seconds(remaining);
				double paused = wall - consumed;
				double restart = seconds(r.activeAt - r.restartAt);
				double frozen = Math.abs(seconds(r.timerAtRestart - r.timerAtActive));
				double after = seconds(r.timerAtActive - remaining);
				double sinceResume = seconds(ctx.now() - r.activeAt);
				ctx.check(frozen <= 0.5, String.format(Locale.ROOT, "The timer moved %.1f s during the restart", frozen));
				ctx.check(Math.abs(after - sinceResume) <= 0.5, String.format(Locale.ROOT,
						"After resuming the timer counted %.1f s in %.1f s (it should run again)", after, sinceResume));
				ctx.check(Math.abs(paused - restart) <= 1.0, String.format(Locale.ROOT,
						"The timer was paused %.1f s but the restart lasted %.1f s (more than 1 s apart)", paused, restart));
				ctx.pass(String.format(Locale.ROOT, "Paused %.1f s, restart lasted %.1f s (difference %.1f s). Frozen at %s during "
								+ "the restart; counted down %.1f s in the %.1f s after resuming.", paused, restart,
						Math.abs(paused - restart), DurationParser.format(TimeUnit.NANOSECONDS.toSeconds(r.timerAtActive)), after, sinceResume));
			});
			return b.build();
		}
	}
}
