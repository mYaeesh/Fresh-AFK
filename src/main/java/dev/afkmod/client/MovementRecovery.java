package dev.afkmod.client;

import dev.afkmod.AfkModClient;
import dev.afkmod.client.KeyControl.Action;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.logic.AfkStateMachine;
import dev.afkmod.logic.AfkStateMachine.Reason;
import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.logic.EdgeCheck;
import dev.afkmod.logic.RecoveryAttempts;
import dev.afkmod.logic.RecoverySequence;
import dev.afkmod.logic.RecoverySequence.Outcome;
import dev.afkmod.logic.StuckDetector;
import dev.afkmod.logic.YawMath;
import dev.afkmod.logic.YawRotation;
import dev.afkmod.stats.AfkStats;
import dev.afkmod.stats.EventType;
import dev.afkmod.testlab.EdgeReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Movement recovery: notices that the player stopped moving while ACTIVE (the farm always moves them) and tries
 * to get them going again: release crouch/left/right, turn smoothly to the nearest cardinal direction (yaw only),
 * jump and walk forward a little. After {@code recoveryRetries + 1} failed attempts it turns the mod off and
 * disconnects to the server list.
 *
 * <p>The decisions live in pure, tested classes ({@link StuckDetector}, {@link RecoveryAttempts},
 * {@link RecoverySequence}, {@link EdgeCheck}); this class connects them to the game. Hooks for a later Test Lab:
 * {@link #forceStuckDetection()}, {@link #injectAttemptOutcome}, {@link #setPathCheck} and {@link #setLogOutAction}.
 */
public final class MovementRecovery {
	private final AfkStateMachine machine;
	private final MessageLog log;
	private final AfkStats stats;
	private final StuckDetector detector = new StuckDetector();
	private final RecoveryAttempts attempts = new RecoveryAttempts();
	private final GameActions actions = new GameActions();
	private final RecoverySequence sequence = new RecoverySequence(actions);

	/** Edge check for a cardinal yaw; replaceable for tests. */
	private IntFunction<EdgeCheck.Verdict> pathCheck = MovementRecovery::checkPathInWorld;
	/** The final "log out" after every attempt failed; replaceable (e.g. with a no-op) for tests. */
	private Runnable logOutAction;
	private boolean screenWasOpen;
	private int currentAttempt;
	/** Test Lab hook: while true, this feature's stats and events are flagged as test data. */
	private boolean statsTest;
	private Outcome lastOutcome;
	private int attemptsStarted;
	/** Test Lab "rotation only": the real smooth turn without the rest of the recovery. */
	private YawRotation testTurn;
	private boolean testTurnRunning;

	public MovementRecovery(AfkStateMachine machine, MessageLog log, AfkStats stats, Runnable logOutAction) {
		this.machine = Objects.requireNonNull(machine);
		this.log = Objects.requireNonNull(log);
		this.stats = Objects.requireNonNull(stats);
		this.logOutAction = Objects.requireNonNull(logOutAction);
	}

	/** Test Lab hook: flags everything this feature records in the stats as test data (kept out of the real counters). */
	public void setStatsTest(boolean statsTest) {
		this.statsTest = statsTest;
	}

	// ---- Test Lab hooks ----

	/** The next periodic check in ACTIVE reports "stuck", whatever the samples say. */
	public void forceStuckDetection() {
		detector.forceStuck();
	}

	/** The running attempt ends with {@code outcome} on its next tick (e.g. a forced failure). */
	public void injectAttemptOutcome(Outcome outcome) {
		sequence.injectOutcome(outcome);
	}

	/** Replaces the edge check (cardinal yaw -> verdict). */
	public void setPathCheck(IntFunction<EdgeCheck.Verdict> pathCheck) {
		this.pathCheck = Objects.requireNonNull(pathCheck);
	}

	/** Replaces the final disconnect, e.g. with a no-op. */
	public void setLogOutAction(Runnable logOutAction) {
		this.logOutAction = Objects.requireNonNull(logOutAction);
	}

	/** The outcome of the last finished attempt, or null. */
	public Outcome lastOutcome() {
		return lastOutcome;
	}

	/** Attempts started this game session. */
	public int attemptsStarted() {
		return attemptsStarted;
	}

	/** The last smooth turn to a cardinal direction (for the GUI). Angles are the raw yaw before and after. */
	public record YawTurn(String direction, float fromYaw, float toYaw, double seconds) {
	}

	private YawTurn lastYawTurn;

	/** The last yaw turn the recovery (or the Test Lab's rotation test) made, or null if none yet this game session. */
	public YawTurn lastYawTurn() {
		return lastYawTurn;
	}

	/** Clears the window (and a pending forced detection) and the attempt counter. */
	public void resetForTest() {
		detector.clear();
		attempts.reset();
	}

	/** Sets the yaw at once through the same path the recovery turn uses (yaw only). */
	public void setYawForTest(float yaw) {
		actions.setYaw(yaw);
	}

	/**
	 * Starts the real smooth turn to the nearest cardinal direction (same {@link YawRotation}, same per-frame and per-tick
	 * application, same yaw setter as a recovery attempt) without releasing keys, jumping or walking.
	 */
	public void startTestTurn(double seconds) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return;
		float start = player.getYRot();
		int target = YawMath.nearestCardinal(start);
		testTurn = new YawRotation(start, target, System.nanoTime(), seconds);
		testTurnRunning = true;
		log.event(String.format(Locale.ROOT, "Test turn: yaw %.2f -> %.1f (%s) over %.2f s, no walk",
				start, testTurn.endYaw(), YawMath.directionName(target), seconds));
		stats.event(EventType.YAW_SNAP, String.format(Locale.ROOT, "Yaw %.1f -> %.1f (%s), rotation only",
				start, testTurn.endYaw(), YawMath.directionName(target)), statsTest);
	}

	public boolean isTestTurnRunning() {
		return testTurnRunning;
	}

	/** The current or last test turn, or null. */
	public YawRotation testTurnRotation() {
		return testTurn;
	}

	/** Stops a test turn where it is (no snapping). */
	public void cancelTestTurn() {
		testTurnRunning = false;
	}

	/** Every tick: advances a test turn and puts the yaw exactly on the target when it is done. */
	public void onTestTurnTick() {
		if (!testTurnRunning) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			testTurnRunning = false;
			return;
		}
		long now = System.nanoTime();
		actions.setYaw(testTurn.yawAt(now));
		if (testTurn.isFinished(now)) {
			actions.setYaw(testTurn.endYaw());
			testTurnRunning = false;
			log.event(String.format(Locale.ROOT, "Test turn finished at yaw %.1f", testTurn.endYaw()));
		}
	}

	/** What the edge check sees ahead of the nearest cardinal direction, or null without a world. */
	public EdgeReport edgeReport() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) return null;
		int cardinal = YawMath.nearestCardinal(player.getYRot());
		Direction dir = Direction.fromYRot(cardinal);
		BlockPos feet = player.blockPosition();
		EdgeCheck.Cell[][] cells = new EdgeCheck.Cell[EdgeCheck.BLOCKS_AHEAD][EdgeReport.DYS.length];
		String[][] names = new String[EdgeCheck.BLOCKS_AHEAD][EdgeReport.DYS.length];
		for (int forward = 1; forward <= EdgeCheck.BLOCKS_AHEAD; forward++) {
			for (int i = 0; i < EdgeReport.DYS.length; i++) {
				BlockPos pos = feet.relative(dir, forward).above(EdgeReport.DYS[i]);
				cells[forward - 1][i] = classify(level, pos);
				names[forward - 1][i] = level.getBlockState(pos).getBlock().getName().getString();
			}
		}
		return EdgeReport.of(cardinal, YawMath.directionName(cardinal), cells, names);
	}

	public StuckDetector detector() {
		return detector;
	}

	public RecoveryAttempts attempts() {
		return attempts;
	}

	public RecoverySequence sequence() {
		return sequence;
	}

	// ---- driven by AfkController ----

	/** The periodic (20-tick) check, after the state machine's own tick. Samples and evaluates in ACTIVE only. */
	public void onCheck(Minecraft mc, AfkConfig config, boolean worldReady) {
		if (machine.state() != State.ACTIVE) return;
		if (!config.movementCheckEnabled || !worldReady) {
			detector.clear();
			return;
		}
		// Clear while a screen is open and once more when it closes, so the time behind it never counts.
		if (mc.screen != null) {
			if (!screenWasOpen) detector.clear();
			screenWasOpen = true;
			return;
		}
		if (screenWasOpen) {
			screenWasOpen = false;
			detector.clear();
		}

		LocalPlayer player = mc.player;
		long now = System.nanoTime();
		detector.addSample(now, player.getX(), player.getZ(), config.stuckWindowSeconds);
		StuckDetector.Evaluation eval = detector.evaluate(config.stuckWindowSeconds, config.stuckDistanceBlocks);
		if (!eval.windowFull()) return;

		boolean wasCounting = attempts.failures() > 0 || attempts.isAwaitingVerification();
		RecoveryAttempts.Decision decision = attempts.onWindow(eval.stuck(), config.recoveryRetries);
		if (!eval.stuck()) {
			if (wasCounting) log.event("Recovery: a full window shows normal movement, attempt counter reset");
			return;
		}
		StuckDetector.Sample oldest = eval.oldest();
		log.event(String.format(Locale.ROOT,
				"Stuck detected%s: %d samples over %ds, max distance %.2f < %.2f blocks, oldest %.2f, %.2f, now %.2f, %.2f",
				eval.forced() ? " (forced)" : "", eval.samples(), config.stuckWindowSeconds, eval.maxDistance(),
				config.stuckDistanceBlocks, oldest.x(), oldest.z(), eval.current().x(), eval.current().z()));
		stats.stuckDetected(statsTest);
		stats.event(EventType.STUCK_DETECTED, String.format(Locale.ROOT, "Moved %.2f blocks in %ds (limit %.2f)",
				eval.maxDistance(), config.stuckWindowSeconds, config.stuckDistanceBlocks), statsTest);
		switch (decision) {
			case START_ATTEMPT -> startAttempt(config);
			case GIVE_UP -> {
				// The last "successful" walk didn't hold: that attempt counts as failed.
				stats.recoveryResult(false, statsTest);
				stats.event(EventType.RECOVERY_RESULT, "Failed: still stuck after the last recovery walk", statsTest);
				giveUp("still stuck after the last successful recovery walk");
			}
			default -> { }
		}
	}

	/** Every tick while RECOVERING (the one exception to the 20-tick rule). */
	public void onTick(Minecraft mc, AfkConfig config) {
		if (machine.state() != State.RECOVERING) return;
		if (!sequence.isRunning()) {
			machine.endRecovery(Reason.RECOVERY_CANCELLED);
			return;
		}
		if (mc.player == null || mc.level == null || mc.screen != null) {
			cancel(mc.screen != null ? "a screen opened" : "no player");
			return;
		}
		Outcome outcome = sequence.tick(System.nanoTime());
		if (outcome != null) finishAttempt(outcome, config);
	}

	/** Every frame: applies the smooth turn so it is not limited to 20 updates a second. */
	public void onFrame() {
		Minecraft mc = Minecraft.getInstance();
		if (testTurnRunning && mc.player != null) actions.setYaw(testTurn.yawAt(System.nanoTime()));
		if (machine.state() != State.RECOVERING || !sequence.isRunning()) return;
		if (mc.player == null || mc.screen != null) return;
		sequence.frame(System.nanoTime());
	}

	/** Any state change clears the window; leaving RECOVERING early (restart, death, toggle...) cancels the attempt. */
	public void onTransition(State from, State to) {
		detector.clear();
		screenWasOpen = false;
		if (from == State.RECOVERING && sequence.isRunning()) sequence.cancel();
		if (to == State.OFF) {
			attempts.reset();
			testTurnRunning = false;
		}
	}

	/** A join or rejoin: positions from before it mean nothing. */
	public void onJoin() {
		detector.clear();
	}

	// ---- internals ----

	private void startAttempt(AfkConfig config) {
		currentAttempt = attempts.nextAttemptNumber();
		if (!machine.startRecovery()) return;
		attemptsStarted++;
		log.event("Recovery attempt " + currentAttempt + " of " + (Math.max(0, config.recoveryRetries) + 1) + " started");
		stats.recoveryAttempt(statsTest);
		stats.event(EventType.RECOVERY_ATTEMPT, "Attempt " + currentAttempt + " of " + (Math.max(0, config.recoveryRetries) + 1), statsTest);
		sequence.start(System.nanoTime(), new RecoverySequence.Settings(config.recoveryTurnSeconds,
				config.recoveryWalkBlocks, config.recoveryWalkTimeoutSeconds, config.recoveryEdgeCheck));
	}

	private void finishAttempt(Outcome outcome, AfkConfig config) {
		lastOutcome = outcome;
		log.event("Recovery attempt " + currentAttempt + " result: " + outcome);
		stats.recoveryResult(outcome.isSuccess(), statsTest);
		stats.event(EventType.RECOVERY_RESULT, "Attempt " + currentAttempt + ": " + outcome, statsTest);
		RecoveryAttempts.Decision decision = attempts.onAttemptResult(outcome.isSuccess(), config.recoveryRetries);
		if (decision == RecoveryAttempts.Decision.GIVE_UP) {
			giveUp("attempt " + currentAttempt + " failed (" + outcome + ")");
			return;
		}
		// Back to ACTIVE: the controller re-verifies crouch, left and right click on the next tick.
		machine.endRecovery(outcome.isSuccess() ? Reason.RECOVERED : Reason.RECOVERY_FAILED);
	}

	private void cancel(String why) {
		log.event("Recovery cancelled: " + why);
		sequence.cancel();
		machine.endRecovery(Reason.RECOVERY_CANCELLED);
	}

	private void giveUp(String why) {
		log.event("Recovery failed, turning the mod off and disconnecting: " + why);
		machine.giveUpRecovery();
		logOutAction.run();
	}

	/** The real edge check: the 2 blocks ahead of the player's feet towards {@code cardinalYaw}. */
	private static EdgeCheck.Verdict checkPathInWorld(int cardinalYaw) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) return EdgeCheck.Verdict.NO_GROUND;
		Direction dir = Direction.fromYRot(cardinalYaw);
		BlockPos feet = player.blockPosition();
		return EdgeCheck.check((forward, dy) -> classify(level, feet.relative(dir, forward).above(dy)));
	}

	private static EdgeCheck.Cell classify(ClientLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES)
				|| state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.SWEET_BERRY_BUSH)
				|| state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.POINTED_DRIPSTONE)) {
			return EdgeCheck.Cell.HAZARD;
		}
		return state.getCollisionShape(level, pos).isEmpty() ? EdgeCheck.Cell.PASSABLE : EdgeCheck.Cell.SOLID;
	}

	/** What the recovery sequence does to the game. */
	private final class GameActions implements RecoverySequence.Actions {
		@Override
		public void releaseKeys() {
			Minecraft mc = Minecraft.getInstance();
			KeyControl.ensureInactive(mc, Action.ATTACK);
			KeyControl.ensureInactive(mc, Action.USE);
			KeyControl.ensureInactive(mc, Action.CROUCH);
		}

		@Override
		public boolean keysReleased() {
			Minecraft mc = Minecraft.getInstance();
			return !KeyControl.isActive(mc, Action.ATTACK) && !KeyControl.isActive(mc, Action.USE)
					&& !KeyControl.isActive(mc, Action.CROUCH);
		}

		@Override
		public float yaw() {
			LocalPlayer player = Minecraft.getInstance().player;
			return player == null ? 0f : player.getYRot();
		}

		/**
		 * Yaw only, never the pitch. The previous-tick yaw is set to the same value so the camera shows exactly this
		 * yaw at any partial tick (the turn is re-applied every frame). {@code LocalPlayer.sendPosition} sends the
		 * new rotation (with the unchanged pitch) to the server on the next tick.
		 */
		@Override
		public void setYaw(float yaw) {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player == null || player.getYRot() == yaw) return;
			player.setYRot(yaw);
			player.yRotO = yaw;
			Entity vehicle = player.getVehicle();
			if (vehicle != null) vehicle.onPassengerTurned(player);
		}

		@Override
		public double x() {
			LocalPlayer player = Minecraft.getInstance().player;
			return player == null ? 0 : player.getX();
		}

		@Override
		public double z() {
			LocalPlayer player = Minecraft.getInstance().player;
			return player == null ? 0 : player.getZ();
		}

		@Override
		public EdgeCheck.Verdict checkPath(int cardinalYaw) {
			return pathCheck.apply(cardinalYaw);
		}

		@Override
		public void setWalking(boolean walking) {
			Minecraft mc = Minecraft.getInstance();
			mc.options.keyUp.setDown(walking);
			mc.options.keyJump.setDown(walking);
		}

		@Override
		public void log(String message) {
			log.event(message);
		}

		@Override
		public void turnStarted(float fromYaw, float toYaw, String direction) {
			lastYawTurn = new YawTurn(direction, fromYaw, toYaw, AfkModClient.config().recoveryTurnSeconds);
			stats.event(EventType.YAW_SNAP, String.format(Locale.ROOT, "Yaw %.1f -> %.1f (%s)", fromYaw, toYaw, direction), statsTest);
		}
	}
}
