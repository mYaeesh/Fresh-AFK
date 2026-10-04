package dev.afkmod.testlab;

import dev.afkmod.logic.AfkStateMachine.Reason;
import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.logic.RecoverySequence;
import dev.afkmod.logic.RecoverySequence.Outcome;
import dev.afkmod.logic.YawRotation;
import dev.afkmod.testlab.ConfigOverrides.Key;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.afkmod.testlab.TestContext.seconds;

/** D. Movement recovery. */
public final class MovementScenarios {
	private MovementScenarios() {
	}

	/** Samples of one turn (and the walk that follows), taken every frame and every tick. */
	static final class TurnRecorder {
		final List<RotationAnalyzer.Sample> samples = new ArrayList<>();
		float pitchBefore;
		YawRotation rotation;
		long walkStartAt;
		float yawAtWalkStart;
		double walkX;
		double walkZ;
		boolean walkSeen;

		void begin(TestContext ctx) {
			pitchBefore = ctx.env().pitch();
		}

		/** One sample; {@code rotationNow} is the running rotation (or null). */
		void sample(TestContext ctx, YawRotation rotationNow) {
			TestEnvironment env = ctx.env();
			if (rotation == null && rotationNow != null) rotation = rotationNow;
			if (rotation == null) return;
			samples.add(new RotationAnalyzer.Sample(ctx.now(), env.yaw(), env.pitch()));
			if (!walkSeen && env.walkKeysDown()) {
				walkSeen = true;
				walkStartAt = ctx.now();
				yawAtWalkStart = env.yaw();
				walkX = env.x();
				walkZ = env.z();
			}
		}

		RotationAnalyzer.Analysis analyze() {
			return RotationAnalyzer.analyze(samples, pitchBefore, rotation.startYaw(), rotation.endYaw(),
					rotation.startNanos(), seconds(rotation.durationNanos()));
		}
	}

	static double recoveryTimeout(TestContext ctx) {
		return RecoverySequence.KEY_RELEASE_WAIT_SECONDS + ctx.config().recoveryTurnSeconds
				+ ctx.config().recoveryWalkTimeoutSeconds + 3;
	}

	/** D1: the yaw snap calculator and the edge-value table. */
	public static final class D1 extends Scenario {
		public D1() {
			super("D1", Group.D, "Yaw snap calculator",
					"Normalized yaw, direction and exact target yaw for the given yaw (or your current yaw), plus the "
							+ "built-in edge-value table (0, 44.99, 45, 90, 134.99, 135, 179.9, 180, -179.9, -90, 270, 359.9, -0.1) "
							+ "checked PASS/FAIL. Works with the mod OFF.",
					AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			return Script.of(ctx -> {
				Double yaw = o.yaw != null ? o.yaw : ctx.env().inWorld() ? Double.valueOf(ctx.env().yaw()) : null;
				String calc = yaw == null ? "No yaw given (type one or join a world)." : YawTable.calculate(yaw).describe() + ".";
				List<YawTable.Row> rows = YawTable.check();
				long passed = rows.stream().filter(YawTable.Row::pass).count();
				StringBuilder failures = new StringBuilder();
				for (YawTable.Row row : rows) {
					if (!row.pass()) failures.append("\n").append(row.describe());
				}
				String text = calc + " Edge table: " + passed + "/" + rows.size() + " PASS.";
				if (passed < rows.size()) ctx.fail(text + failures);
				ctx.pass(text);
			});
		}
	}

	/** D2: what the edge check sees ahead, without moving. */
	public static final class D2 extends Scenario {
		public D2() {
			super("D2", Group.D, "Edge check report",
					"Without moving: what the edge check sees in the 2 blocks ahead of the nearest cardinal direction "
							+ "(solid ground / drop / harmful block) and what it would decide. Works with the mod OFF.",
					WORLD | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			return Script.of(ctx -> {
				EdgeReport report = ctx.env().edgeReport();
				if (report == null) ctx.info("No world loaded.");
				ctx.info(report.describe());
			});
		}
	}

	/** D3: skip the stuck wait and run the real recovery, sampling the turn every frame. */
	public static final class D3 extends Scenario {
		public D3() {
			super("D3", Group.D, "Forced recovery",
					"Skips the stuck-detection wait and runs the real recovery sequence (the player TURNS, JUMPS and WALKS). "
							+ "Samples yaw and pitch every frame: pitch identical, yaw monotonic without overshoot, ease-in-out, "
							+ "duration = recoveryTurnSeconds +-0.15 s, exact multiple of 90, walk only after the turn, distance, "
							+ "keys restored.",
					MOD_ON | CONFIRM);
		}

		@Override
		public Script script(TestOptions o) {
			TurnRecorder rec = new TurnRecorder();
			Outcome[] outcome = new Outcome[1];
			double[] moved = new double[1];
			return Script.builder()
					.run(ctx -> {
						ctx.overrides().set(Key.MOVEMENT_CHECK_ENABLED, true);
						ctx.env().resetRecovery();
						rec.begin(ctx);
						ctx.onFrame(c -> rec.sample(c, c.env().recoveryRotation()));
						ctx.env().forceStuckDetection();
						ctx.env().requestCheck();
					})
					.waitUntil("the recovery to start (RECOVERING)", ctx -> ctx.state() == State.RECOVERING,
							ctx -> 2 * ctx.interval() + 1)
					.waitUntil("the recovery attempt to finish", ctx -> {
						rec.sample(ctx, ctx.env().recoveryRotation());
						return ctx.state() != State.RECOVERING;
					}, MovementScenarios::recoveryTimeout)
					.run(ctx -> {
						ctx.onFrame(null);
						outcome[0] = ctx.env().lastRecoveryOutcome();
						if (rec.walkSeen) moved[0] = Math.hypot(ctx.env().x() - rec.walkX, ctx.env().z() - rec.walkZ);
						if (ctx.state() == State.OFF) {
							ctx.info("Attempt result " + outcome[0] + "; with recoveryRetries " + ctx.config().recoveryRetries
									+ " the mod gave up: OFF (" + ctx.machine().lastReason() + "), WOULD DISCONNECT logged.");
						}
					})
					.waitUntil("crouch, left and right click to be restored",
							ctx -> ctx.state() == State.ACTIVE && ctx.allKeysActive(), ctx -> 2 * ctx.interval() + 1.5)
					.run(ctx -> {
						if (rec.rotation == null) ctx.fail("The recovery never started its turn (result " + outcome[0] + ")");
						RotationAnalyzer.Analysis a = rec.analyze();
						List<String> failures = new ArrayList<>(a.failures());
						long turnEnd = rec.rotation.startNanos() + rec.rotation.durationNanos();
						if (rec.walkSeen && Float.compare(rec.yawAtWalkStart, rec.rotation.endYaw()) != 0) {
							failures.add("the walk started at yaw " + rec.yawAtWalkStart + ", before the turn reached " + rec.rotation.endYaw());
						}
						if (rec.walkSeen && rec.walkStartAt < turnEnd) failures.add("the walk started before the turn's end time");
						String summary = "Turn: " + a.summary() + ".";
						if (!failures.isEmpty()) ctx.fail(summary + " Problems: " + String.join("; ", failures));
						String walk = String.format(Locale.ROOT, "Walk: moved %.2f blocks (recoveryWalkBlocks %.2f). Keys restored.",
								moved[0], ctx.config().recoveryWalkBlocks);
						switch (outcome[0]) {
							case SUCCESS -> ctx.pass(summary + " " + walk + " Result SUCCESS.");
							case EDGE_UNSAFE -> ctx.info(summary + " The edge check said the path ahead is unsafe, so it didn't walk "
									+ "(counted as a failed attempt). Keys restored.");
							case WALK_TIMEOUT -> ctx.info(summary + " " + walk + " The walk timed out (something blocked it).");
							case null -> ctx.fail(summary + " No recovery result was recorded.");
							default -> ctx.fail(summary + " Result " + outcome[0] + ".");
						}
					})
					.build();
		}
	}

	/** D4: arm the real stuck detection with a 5 s window and measure when it fires. */
	public static final class D4 extends Scenario {
		static final int WINDOW = 5;

		public D4() {
			super("D4", Group.D, "Armed stuck detection",
					"Overrides stuckWindowSeconds to 5 and clears the window. Standing still must fire the real detector "
							+ "within the window + one check interval; the real recovery then runs (the player moves).",
					MOD_ON | CONFIRM);
		}

		@Override
		public Script script(TestOptions o) {
			long[] t = new long[2];
			return Script.builder()
					.run(ctx -> {
						ctx.overrides().set(Key.STUCK_WINDOW_SECONDS, WINDOW);
						ctx.overrides().set(Key.MOVEMENT_CHECK_ENABLED, true);
						ctx.env().resetRecovery();
						t[0] = ctx.now();
					})
					.waitUntil("the stuck detection to fire", ctx -> {
						if (ctx.state() == State.RECOVERING) {
							t[1] = ctx.now();
							return true;
						}
						ctx.check(ctx.state() == State.ACTIVE, "The mod left ACTIVE (" + ctx.state() + ")");
						return false;
					}, ctx -> WINDOW + 3 * ctx.interval() + 2, ctx -> ctx.fail(String.format(Locale.ROOT,
							"The detector didn't fire within %.1f s: max distance in the window %.2f blocks (limit %.2f). Did you move?",
							ctx.secondsSince(t[0]), ctx.env().stuckWindowMaxDistance(), ctx.config().stuckDistanceBlocks)))
					.run(ctx -> {
						double fired = seconds(t[1] - t[0]);
						ctx.check(fired >= WINDOW - 0.05, String.format(Locale.ROOT, "Fired too early: %.1f s (window %d s)", fired, WINDOW));
						ctx.check(fired <= WINDOW + ctx.interval() + 0.3, String.format(Locale.ROOT,
								"Fired late: %.1f s (expected within %d s + one %.1f s check)", fired, WINDOW, ctx.interval()));
					})
					.waitUntil("the recovery attempt to finish", ctx -> ctx.state() != State.RECOVERING, MovementScenarios::recoveryTimeout)
					.run(ctx -> ctx.pass(String.format(Locale.ROOT,
							"Stuck detection fired %.1f s after arming (window %d s + up to one %.1f s check). The real recovery ran: %s.",
							seconds(t[1] - t[0]), WINDOW, ctx.interval(), ctx.env().lastRecoveryOutcome())))
					.build();
		}
	}

	/** D5: inject failed attempts into the real retry logic and check the final logout. */
	public static final class D5 extends Scenario {
		public D5() {
			super("D5", Group.D, "Failed recovery path",
					"Injects \"didn't move\" results into the real retry logic: checks the attempt count (1 + recoveryRetries) "
							+ "and that the final action is the logout, which is replaced by a logged WOULD DISCONNECT unless "
							+ "\"real disconnect\" is allowed (a separate confirmation).",
					MOD_ON | CONFIRM);
		}

		/** Force stuck -> wait for RECOVERING -> inject WALK_TIMEOUT -> wait for the end, until the mod gives up. */
		private static final class FailLoop implements Step {
			private enum Phase { FORCE, WAIT_START, WAIT_END }

			private final int[] attempts;
			private final int expected;
			private Phase phase;
			private long since;

			FailLoop(int[] attempts, int expected) {
				this.attempts = attempts;
				this.expected = expected;
			}

			@Override
			public void start(TestContext ctx) {
				phase = Phase.FORCE;
			}

			@Override
			public boolean tick(TestContext ctx) {
				switch (phase) {
					case FORCE -> {
						ctx.check(ctx.state() == State.ACTIVE, "Expected ACTIVE before attempt " + (attempts[0] + 1) + ", got " + ctx.state());
						ctx.env().forceStuckDetection();
						ctx.env().requestCheck();
						since = ctx.now();
						phase = Phase.WAIT_START;
					}
					case WAIT_START -> {
						if (ctx.state() == State.RECOVERING) {
							attempts[0]++;
							ctx.env().injectRecoveryOutcome(Outcome.WALK_TIMEOUT);
							since = ctx.now();
							phase = Phase.WAIT_END;
						} else if (ctx.secondsSince(since) > 2 * ctx.interval() + 1) {
							ctx.fail("Attempt " + (attempts[0] + 1) + " didn't start (state " + ctx.state() + ")");
						}
					}
					case WAIT_END -> {
						State state = ctx.state();
						if (state == State.OFF) return true;
						if (state == State.ACTIVE) {
							ctx.check(attempts[0] < expected + 2, "Still not given up after " + attempts[0] + " attempts");
							phase = Phase.FORCE;
						} else if (state == State.RECOVERING) {
							ctx.check(ctx.secondsSince(since) < 5, "The injected result didn't end attempt " + attempts[0]);
						} else {
							ctx.fail("Unexpected state " + state + " during the failed-recovery test");
						}
					}
				}
				return false;
			}
		}

		@Override
		public Script script(TestOptions o) {
			int[] attempts = new int[1];
			int[] m = new int[4]; // expected attempts, mark, real disconnects, would-disconnects
			Script.Builder b = Script.builder();
			b.run(ctx -> {
				ctx.overrides().set(Key.MOVEMENT_CHECK_ENABLED, true);
				ctx.env().resetRecovery();
				m[0] = Math.max(0, ctx.config().recoveryRetries) + 1;
				m[1] = ctx.mark();
				m[2] = ctx.env().realDisconnectCount();
				m[3] = ctx.env().wouldDisconnectCount();
				if (o.allowRealDisconnect) {
					ctx.allowRealDisconnect();
					ctx.expectRealDisconnect(c -> {
						c.check(attempts[0] == m[0], "Expected " + m[0] + " attempts, got " + attempts[0]);
						c.pass(attempts[0] + " failed attempts (1 + recoveryRetries " + (m[0] - 1) + "), then the mod gave up "
								+ "and really disconnected to the server list (confirmed).");
					});
				}
			});
			b.step(new Step() {
				private FailLoop loop;

				@Override
				public void start(TestContext ctx) {
					loop = new FailLoop(attempts, m[0]);
					loop.start(ctx);
				}

				@Override
				public boolean tick(TestContext ctx) {
					return loop.tick(ctx);
				}
			});
			if (o.allowRealDisconnect) {
				b.waitUntil("the real disconnect", ctx -> false, 10);
			}
			b.run(ctx -> {
				ctx.check(ctx.machine().lastReason() == Reason.RECOVERY_GAVE_UP, "Expected OFF (RECOVERY_GAVE_UP), got " + ctx.machine().lastReason());
				ctx.check(attempts[0] == m[0], "Expected " + m[0] + " attempts (1 + recoveryRetries), got " + attempts[0]);
				long failed = ctx.count(m[1], Reason.RECOVERY_FAILED);
				ctx.check(failed == m[0] - 1, "Expected " + (m[0] - 1) + " RECOVERY_FAILED returns to ACTIVE, got " + failed);
				ctx.check(ctx.env().wouldDisconnectCount() == m[3] + 1, "The final logout was not reached (no WOULD DISCONNECT)");
				ctx.check(ctx.env().realDisconnectCount() == m[2] && ctx.env().inWorld(), "The mod really disconnected");
				ctx.pass(attempts[0] + " injected failed attempts (1 + recoveryRetries " + (m[0] - 1) + "): " + failed
						+ " went back to ACTIVE (RECOVERY_FAILED), the last one gave up: OFF (RECOVERY_GAVE_UP), "
						+ "\"WOULD DISCONNECT\" logged instead of the real logout.");
			});
			return b.build();
		}
	}

	/** D6: the real smooth turn only (no jump, no walk), optionally from a chosen start yaw. */
	public static final class D6 extends Scenario {
		public D6() {
			super("D6", Group.D, "Rotation only",
					"Runs the real smooth rotation to the nearest cardinal direction WITHOUT jumping or walking (the camera "
							+ "turns). Optional start yaw. Reports duration, peak speed, whether the pitch changed and the final "
							+ "yaw, judged like D3.",
					MOD_ON | CONFIRM);
		}

		@Override
		public Script script(TestOptions o) {
			TurnRecorder rec = new TurnRecorder();
			Script.Builder b = Script.builder();
			if (o.yaw != null) {
				b.run(ctx -> ctx.env().setYawForTest(o.yaw.floatValue()));
				b.waitSeconds(0.15);
			}
			b.run(ctx -> {
				rec.begin(ctx);
				ctx.env().startTurnOnly();
				ctx.check(ctx.env().turnOnlyRotation() != null, "The turn didn't start");
				ctx.onFrame(c -> rec.sample(c, c.env().turnOnlyRotation()));
				rec.sample(ctx, ctx.env().turnOnlyRotation());
			});
			b.waitUntil("the turn to finish", ctx -> {
				rec.sample(ctx, ctx.env().turnOnlyRotation());
				ctx.check(!ctx.env().walkKeysDown(), "Forward/jump were pressed during a rotation-only test");
				return !ctx.env().turnOnlyRunning();
			}, ctx -> ctx.config().recoveryTurnSeconds + 2);
			b.waitSeconds(0.1);
			b.run(ctx -> {
				ctx.onFrame(null);
				rec.sample(ctx, ctx.env().turnOnlyRotation());
				RotationAnalyzer.Analysis a = rec.analyze();
				String start = o.yaw != null ? "From the test yaw " + YawTable.num(o.yaw) + ": " : "";
				if (!a.passed()) ctx.fail(start + a.summary() + ". Problems: " + a.failureText());
				ctx.pass(start + a.summary() + ". No jump or walk.");
			});
			return b.build();
		}
	}
}
