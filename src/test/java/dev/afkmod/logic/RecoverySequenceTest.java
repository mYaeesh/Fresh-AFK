package dev.afkmod.logic;

import dev.afkmod.config.AfkConfig;
import dev.afkmod.logic.AfkStateMachine.Reason;
import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.logic.RecoverySequence.Outcome;
import dev.afkmod.logic.RecoverySequence.Phase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoverySequenceTest {
	private static final double TICK = 0.05;
	private static final RecoverySequence.Settings DEFAULTS = new RecoverySequence.Settings(0.5, 2, 5, true);

	/** A fake player: keys, yaw, position and the walk keys. There is deliberately no pitch to touch. */
	private static final class FakeActions implements RecoverySequence.Actions {
		boolean keysActive = true;
		boolean keysReleaseWork = true;
		int releaseCalls;
		float yaw;
		final List<Float> yawHistory = new ArrayList<>();
		double x;
		double z;
		boolean walking;
		int walkingOnCalls;
		EdgeCheck.Verdict verdict = EdgeCheck.Verdict.SAFE;
		Integer checkedYaw;
		final List<String> logs = new ArrayList<>();

		@Override
		public void releaseKeys() {
			releaseCalls++;
			if (keysReleaseWork) keysActive = false;
		}

		@Override
		public boolean keysReleased() {
			return !keysActive;
		}

		@Override
		public float yaw() {
			return yaw;
		}

		@Override
		public void setYaw(float yaw) {
			this.yaw = yaw;
			yawHistory.add(yaw);
		}

		@Override
		public double x() {
			return x;
		}

		@Override
		public double z() {
			return z;
		}

		@Override
		public EdgeCheck.Verdict checkPath(int cardinalYaw) {
			checkedYaw = cardinalYaw;
			return verdict;
		}

		@Override
		public void setWalking(boolean walking) {
			if (walking) {
				walkingOnCalls++;
				assertTrue(this.yaw % 90 == 0, "walks only once the yaw is exactly on the target: " + this.yaw);
			}
			this.walking = walking;
		}

		@Override
		public void log(String message) {
			logs.add(message);
		}
	}

	private final FakeClock clock = new FakeClock();
	private final FakeActions game = new FakeActions();
	private final RecoverySequence seq = new RecoverySequence(game);

	@BeforeEach
	void setUp() {
		game.yaw = 163.4f;
	}

	private Outcome tick() {
		clock.advanceSeconds(TICK);
		return seq.tick(clock.getAsLong());
	}

	/** Ticks until the sequence reaches {@code phase} (or ends), at most 10 s. */
	private void tickUntil(Phase phase) {
		for (int i = 0; i < 200 && seq.phase() != phase && seq.isRunning(); i++) tick();
		assertEquals(phase, seq.phase());
	}

	@Test
	void fullSuccessfulAttempt() {
		seq.start(clock.getAsLong(), DEFAULTS);
		assertEquals(1, game.releaseCalls, "crouch/left/right released first");
		assertEquals(Phase.RELEASING_KEYS, seq.phase());

		assertNull(tick());
		assertEquals(Phase.TURNING, seq.phase());
		assertFalse(game.walking, "no walking during the turn");
		tickUntil(Phase.WALKING);
		assertEquals(180.0f, game.yaw, "exactly north");
		assertEquals(180, game.checkedYaw);
		assertTrue(game.walking, "jump + forward held");

		game.x += 1.5;
		assertNull(tick());
		game.z += 1.5; // hypot(1.5, 1.5) = 2.12
		assertEquals(Outcome.SUCCESS, tick());
		assertFalse(game.walking, "released afterwards");
		assertEquals(Phase.IDLE, seq.phase());
		assertEquals(180.0f, game.yaw, "the new facing is kept");
		assertNull(tick(), "nothing happens once finished");
	}

	@Test
	void turnIsSmoothMonotonicAndTakesTheConfiguredTime() {
		seq.start(clock.getAsLong(), DEFAULTS);
		tick(); // keys released -> turn starts at this time
		long turnStart = clock.getAsLong();
		// Frames at ~100 fps between the ticks.
		while (seq.phase() == Phase.TURNING) {
			for (int f = 0; f < 5; f++) {
				clock.advanceSeconds(0.01);
				seq.frame(clock.getAsLong());
			}
			seq.tick(clock.getAsLong());
		}
		double seconds = (clock.getAsLong() - turnStart) / 1e9;
		assertEquals(0.5, seconds, 0.011, "the turn takes recoveryTurnSeconds");
		float prev = 163.4f;
		for (float y : game.yawHistory) {
			assertTrue(y >= prev && y <= 180.0f, "monotonic, no overshoot: " + y);
			prev = y;
		}
		assertTrue(game.yawHistory.size() > 40, "applied every frame, not only every tick");
		assertEquals(180.0f, game.yaw);
	}

	@Test
	void instantSnapWithZeroTurnTime() {
		seq.start(clock.getAsLong(), new RecoverySequence.Settings(0, 2, 5, true));
		assertNull(tick());
		assertEquals(Phase.WALKING, seq.phase(), "snap and start walking on the same tick");
		assertEquals(180.0f, game.yaw);
	}

	@Test
	void waitsForTheKeysToReadInactive() {
		game.keysReleaseWork = false;
		seq.start(clock.getAsLong(), DEFAULTS);
		for (int i = 0; i < 30; i++) assertNull(tick()); // 1.5 s
		assertEquals(Phase.RELEASING_KEYS, seq.phase());
		assertEquals(2, game.releaseCalls, "re-checked once per second, never spammed");
		game.keysActive = false;
		tick();
		assertEquals(Phase.TURNING, seq.phase());
	}

	@Test
	void keysThatNeverReleaseFailAfterThreeSeconds() {
		game.keysReleaseWork = false;
		seq.start(clock.getAsLong(), DEFAULTS);
		Outcome outcome = null;
		int ticks = 0;
		while (outcome == null && ticks < 100) {
			outcome = tick();
			ticks++;
		}
		assertEquals(Outcome.KEYS_NOT_RELEASED, outcome);
		assertEquals(60, ticks, "3 s");
		assertEquals(163.4f, game.yaw, "never turned");
		assertEquals(0, game.walkingOnCalls);
	}

	@Test
	void walkTimeoutDoesNotIncludeTheTurn() {
		seq.start(clock.getAsLong(), DEFAULTS);
		tickUntil(Phase.WALKING);
		long walkStart = clock.getAsLong();
		Outcome outcome = null;
		while (outcome == null) outcome = tick();
		assertEquals(Outcome.WALK_TIMEOUT, outcome);
		assertEquals(5.0, (clock.getAsLong() - walkStart) / 1e9, 1e-6, "5 s of walking after the turn");
		assertFalse(game.walking);
	}

	@Test
	void unsafePathIsAFailedAttemptWithoutWalking() {
		game.verdict = EdgeCheck.Verdict.NO_GROUND;
		seq.start(clock.getAsLong(), DEFAULTS);
		Outcome outcome = null;
		while (outcome == null) outcome = tick();
		assertEquals(Outcome.EDGE_UNSAFE, outcome);
		assertEquals(0, game.walkingOnCalls);
		assertEquals(180.0f, game.yaw, "the turn still happened");
	}

	@Test
	void edgeCheckCanBeTurnedOff() {
		game.verdict = EdgeCheck.Verdict.HAZARD_ON_PATH;
		seq.start(clock.getAsLong(), new RecoverySequence.Settings(0.5, 2, 5, false));
		tickUntil(Phase.WALKING);
		assertNull(game.checkedYaw);
	}

	@Test
	void cancelDuringTheTurnLeavesTheYawWhereItIs() {
		seq.start(clock.getAsLong(), DEFAULTS);
		tick();
		tick();
		tick();
		float midTurn = game.yaw;
		assertTrue(midTurn > 163.4f && midTurn < 180.0f);
		int applied = game.yawHistory.size();
		seq.cancel();
		assertEquals(Phase.IDLE, seq.phase());
		clock.advanceSeconds(1);
		seq.frame(clock.getAsLong());
		assertNull(seq.tick(clock.getAsLong()));
		assertEquals(midTurn, game.yaw, "no snapping back or forward");
		assertEquals(applied, game.yawHistory.size(), "nothing applied after cancel");
	}

	@Test
	void cancelDuringTheWalkReleasesForwardAndJump() {
		seq.start(clock.getAsLong(), DEFAULTS);
		tickUntil(Phase.WALKING);
		assertTrue(game.walking);
		seq.cancel();
		assertFalse(game.walking);
	}

	@Test
	void injectedOutcomeEndsTheAttempt() {
		seq.start(clock.getAsLong(), DEFAULTS);
		tickUntil(Phase.WALKING);
		seq.injectOutcome(Outcome.WALK_TIMEOUT);
		assertEquals(Outcome.WALK_TIMEOUT, tick());
		assertFalse(game.walking);
	}

	@Test
	void turnsTheShortWayAcrossTheWrap() {
		game.yaw = -170.0f; // mostly north, from the negative side
		seq.start(clock.getAsLong(), DEFAULTS);
		tickUntil(Phase.WALKING);
		assertEquals(-180.0f, game.yaw);
		for (float y : game.yawHistory) assertTrue(y <= -170.0f && y >= -180.0f, "10 degrees, not 350: " + y);
	}

	/**
	 * Restart handling takes priority: wired like AfkController, a restart message during the recovery moves the
	 * state machine to RESTARTING and the listener cancels the sequence.
	 */
	@Test
	void restartDuringRecoveryCancelsTheSequence() {
		AfkConfig config = new AfkConfig();
		AfkStateMachine sm = new AfkStateMachine(() -> config, clock);
		sm.setListener((from, to, reason) -> {
			if (from == State.RECOVERING) seq.cancel();
		});
		sm.turnOn();
		assertTrue(sm.startRecovery());
		seq.start(clock.getAsLong(), DEFAULTS);
		tickUntil(Phase.WALKING);
		sm.onMessage("Servers restarting in 10 seconds");
		assertEquals(State.RESTARTING, sm.state());
		assertEquals(Reason.RESTART_DETECTED, sm.lastReason());
		assertEquals(1, sm.restartCount());
		assertFalse(seq.isRunning());
		assertFalse(game.walking, "W and jump released");
	}
}
