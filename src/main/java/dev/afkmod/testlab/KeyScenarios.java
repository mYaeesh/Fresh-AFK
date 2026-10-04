package dev.afkmod.testlab;

import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.testlab.TestEnvironment.Key;

import java.util.Locale;

import static dev.afkmod.testlab.TestContext.seconds;

/** C. Key control. */
public final class KeyScenarios {
	private KeyScenarios() {
	}

	/** C1: per key, switch it off and measure how the mod's periodic check turns it back on. */
	public static final class C1 extends Scenario {
		public C1() {
			super("C1", Group.C, "Key check",
					"For crouch, left click and right click: reports the mode (toggle/hold) and state, switches the key off "
							+ "with ensureInactive, then checks the mod turns it back on within 2 check intervals with at most "
							+ "one press per check (presses counted by instrumenting ensureActive/ensureInactive).",
					MOD_ON | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			StringBuilder report = new StringBuilder();
			Script.Builder b = Script.builder();
			for (Key key : Key.values()) {
				long[] m = new long[2]; // start time, presses before
				boolean[] before = new boolean[2]; // was active, toggle mode
				b.run(ctx -> {
					ctx.check(ctx.state() == State.ACTIVE, "The mod left ACTIVE (" + ctx.state() + ")");
					TestEnvironment env = ctx.env();
					before[0] = env.keyActive(key);
					before[1] = env.keyToggleMode(key);
					env.releaseKeyForTest(key);
					m[0] = ctx.now();
					m[1] = env.presses().total(key.ordinal());
					env.presses().resetMax();
				});
				b.waitUntil(key.label() + " to be turned back on", ctx -> ctx.env().keyActive(key),
						ctx -> 2 * ctx.interval() + 0.3,
						ctx -> ctx.fail(String.format(Locale.ROOT, "%s (%s mode) was not turned back on within 2 check intervals (%.1f s)",
								key.label(), before[1] ? "toggle" : "hold", 2 * ctx.interval())));
				b.run(ctx -> {
					long presses = ctx.env().presses().total(key.ordinal()) - m[1];
					int maxPerCheck = ctx.env().presses().maxPerCheck(key.ordinal());
					double after = seconds(ctx.now() - m[0]);
					ctx.check(maxPerCheck <= 1, String.format(Locale.ROOT,
							"%s: the mod sent %d presses within one check (at most 1 allowed, a second press toggles it back off)",
							key.label(), maxPerCheck));
					report.append(String.format(Locale.ROOT, "%s: %s mode, was %s, back on after %.1f s with %d press(es) (max %d per check). ",
							key.label(), before[1] ? "toggle" : "hold", before[0] ? "on" : "off", after, presses, maxPerCheck));
				});
			}
			b.run(ctx -> ctx.pass(report.toString().strip()));
			return b.build();
		}
	}

	/** C2: the mod does nothing while a screen is open and re-verifies the keys after it closes. */
	public static final class C2 extends Scenario {
		public C2() {
			super("C2", Group.C, "Screen-open rule",
					"Opens a test screen: the mod must press nothing while it is open (2 check intervals), then turn crouch, "
							+ "left and right click back on after it closes.",
					MOD_ON | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			long[] m = new long[3]; // presses before, checks before, closed at
			return Script.builder()
					.waitUntil("crouch, left and right click to be active first", TestContext::allKeysActive,
							ctx -> 2 * ctx.interval() + 0.5)
					.run(ctx -> {
						ctx.env().openTestScreen();
						ctx.check(ctx.env().screenOpen(), "The test screen didn't open");
						m[0] = ctx.env().presses().totalAll();
						m[1] = ctx.env().checkCount();
					})
					.waitSeconds(ctx -> 2 * ctx.interval() + 0.2)
					.run(ctx -> {
						long checks = ctx.env().checkCount() - m[1];
						long presses = ctx.env().presses().totalAll() - m[0];
						ctx.check(checks >= 2, "The periodic check didn't run while the screen was open (" + checks + " checks)");
						ctx.check(presses == 0, "The mod pressed keys " + presses + " time(s) while a screen was open");
						ctx.env().closeTestScreen();
						m[2] = ctx.now();
					})
					.waitUntil("crouch, left and right click after the screen closed", TestContext::allKeysActive,
							ctx -> 2 * ctx.interval() + 0.5)
					.run(ctx -> ctx.pass(String.format(Locale.ROOT,
							"Screen open for %d checks: no presses. After closing, C L R were active again after %.1f s.",
							ctx.env().checkCount() - m[1], seconds(ctx.now() - m[2]))))
					.build();
		}
	}
}
