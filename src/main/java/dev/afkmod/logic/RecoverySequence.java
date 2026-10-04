package dev.afkmod.logic;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * One movement-recovery attempt, run every tick while RECOVERING:
 * <ol>
 *   <li>release crouch, left and right click (wait up to 3 s until they read inactive),</li>
 *   <li>turn smoothly to the nearest cardinal direction (yaw only, never the pitch),</li>
 *   <li>check the path ahead, then hold jump and forward until the player moved {@code walkBlocks} or
 *       {@code walkTimeoutSeconds} passed.</li>
 * </ol>
 * The new facing is kept afterwards. Pure logic: everything that touches the game goes through {@link Actions},
 * so a later Test Lab (or a unit test) can drive it, and {@link #injectOutcome} can force a result.
 */
public final class RecoverySequence {
	/** How long to wait for crouch/left/right to read inactive. */
	public static final double KEY_RELEASE_WAIT_SECONDS = 3.0;
	/** While waiting, the release is re-checked (at most one press per key) this often. */
	public static final double KEY_RELEASE_RETRY_SECONDS = 1.0;

	public enum Phase {
		IDLE,
		RELEASING_KEYS,
		TURNING,
		WALKING
	}

	public enum Outcome {
		SUCCESS,
		KEYS_NOT_RELEASED,
		EDGE_UNSAFE,
		WALK_TIMEOUT;

		public boolean isSuccess() {
			return this == SUCCESS;
		}
	}

	public record Settings(double turnSeconds, double walkBlocks, double walkTimeoutSeconds, boolean edgeCheck) {
	}

	/** What the sequence does to the game. */
	public interface Actions {
		/** {@code ensureInactive} on crouch, left and right click (at most one press each). */
		void releaseKeys();

		boolean keysReleased();

		float yaw();

		/** Sets the yaw only (the pitch is never touched). */
		void setYaw(float yaw);

		double x();

		double z();

		/** Whether walking forward towards {@code cardinalYaw} is safe. */
		EdgeCheck.Verdict checkPath(int cardinalYaw);

		/** Holds (true) or releases (false) forward and jump. */
		void setWalking(boolean walking);

		void log(String message);

		/** The turn to the nearest cardinal direction begins (for the stats event log). */
		default void turnStarted(float fromYaw, float toYaw, String direction) {
		}
	}

	private final Actions actions;
	private Phase phase = Phase.IDLE;
	private Settings settings;
	private long phaseStartedAt;
	private long lastReleaseAt;
	private YawRotation rotation;
	private int targetYaw;
	private double walkStartX;
	private double walkStartZ;
	private boolean walking;
	private Outcome injected;

	public RecoverySequence(Actions actions) {
		this.actions = Objects.requireNonNull(actions);
	}

	/** Starts an attempt: releases the keys first. */
	public void start(long now, Settings settings) {
		this.settings = Objects.requireNonNull(settings);
		injected = null;
		rotation = null;
		phase = Phase.RELEASING_KEYS;
		phaseStartedAt = now;
		lastReleaseAt = now;
		actions.releaseKeys();
	}

	/** Advances the attempt (call every tick). Returns the outcome when it ends, or null while it runs. */
	public Outcome tick(long now) {
		if (phase == Phase.IDLE) return null;
		if (injected != null) {
			Outcome o = injected;
			actions.log("Recovery: injected outcome " + o);
			return finish(o);
		}
		if (phase == Phase.RELEASING_KEYS) {
			if (actions.keysReleased()) {
				beginTurn(now);
			} else if (elapsed(now, phaseStartedAt, KEY_RELEASE_WAIT_SECONDS)) {
				actions.log("Recovery: crouch/left/right still active after 3 s");
				return finish(Outcome.KEYS_NOT_RELEASED);
			} else {
				if (elapsed(now, lastReleaseAt, KEY_RELEASE_RETRY_SECONDS)) {
					lastReleaseAt = now;
					actions.releaseKeys();
				}
				return null;
			}
		}
		if (phase == Phase.TURNING) {
			actions.setYaw(rotation.yawAt(now));
			if (!rotation.isFinished(now)) return null;
			// Exactly on the target (yawAt returns the exact end value once finished).
			actions.setYaw(rotation.endYaw());
			if (settings.edgeCheck()) {
				EdgeCheck.Verdict verdict = actions.checkPath(targetYaw);
				if (verdict != EdgeCheck.Verdict.SAFE) {
					actions.log("Recovery: path ahead unsafe (" + verdict + "), not walking");
					return finish(Outcome.EDGE_UNSAFE);
				}
			}
			phase = Phase.WALKING;
			phaseStartedAt = now;
			walkStartX = actions.x();
			walkStartZ = actions.z();
			walking = true;
			actions.setWalking(true);
			actions.log(String.format(Locale.ROOT, "Recovery: jumping and walking from %.2f, %.2f", walkStartX, walkStartZ));
			return null;
		}
		// WALKING
		double moved = Math.hypot(actions.x() - walkStartX, actions.z() - walkStartZ);
		if (moved >= settings.walkBlocks()) {
			actions.log(String.format(Locale.ROOT, "Recovery: walked %.2f blocks", moved));
			return finish(Outcome.SUCCESS);
		}
		if (elapsed(now, phaseStartedAt, settings.walkTimeoutSeconds())) {
			actions.log(String.format(Locale.ROOT, "Recovery: walk timed out after %.2f blocks", moved));
			return finish(Outcome.WALK_TIMEOUT);
		}
		actions.setWalking(true);
		return null;
	}

	/** Applies the rotation for the current frame (smoother than per tick). Does nothing outside the turn. */
	public void frame(long now) {
		if (phase == Phase.TURNING && injected == null) actions.setYaw(rotation.yawAt(now));
	}

	/**
	 * Stops the attempt at once: the rotation stops where it is (no snapping back), forward and jump are
	 * released. No outcome is reported.
	 */
	public void cancel() {
		if (phase == Phase.IDLE) return;
		actions.log("Recovery: cancelled during " + phase);
		stopWalking();
		phase = Phase.IDLE;
		rotation = null;
		injected = null;
	}

	/** Test Lab hook: the running attempt ends with {@code outcome} on its next tick. */
	public void injectOutcome(Outcome outcome) {
		if (phase != Phase.IDLE) injected = Objects.requireNonNull(outcome);
	}

	public Phase phase() {
		return phase;
	}

	public boolean isRunning() {
		return phase != Phase.IDLE;
	}

	/** The current or last turn, or null before the first one of this attempt. */
	public YawRotation rotation() {
		return rotation;
	}

	private void beginTurn(long now) {
		float start = actions.yaw();
		targetYaw = YawMath.nearestCardinal(start);
		rotation = new YawRotation(start, targetYaw, now, settings.turnSeconds());
		phase = Phase.TURNING;
		phaseStartedAt = now;
		actions.log(String.format(Locale.ROOT, "Recovery: turning yaw %.2f -> %.1f (%s) over %.2f s",
				start, rotation.endYaw(), YawMath.directionName(targetYaw), settings.turnSeconds()));
		actions.turnStarted(start, rotation.endYaw(), YawMath.directionName(targetYaw));
	}

	private Outcome finish(Outcome outcome) {
		stopWalking();
		phase = Phase.IDLE;
		injected = null;
		return outcome;
	}

	private void stopWalking() {
		if (walking) {
			walking = false;
			actions.setWalking(false);
		}
	}

	private static boolean elapsed(long now, long since, double seconds) {
		return now - since >= Math.round(seconds * TimeUnit.SECONDS.toNanos(1));
	}
}
