package dev.afkmod.testlab;

import dev.afkmod.config.AfkConfig;
import dev.afkmod.logic.AfkStateMachine;
import dev.afkmod.logic.HudText;
import dev.afkmod.logic.KeywordMatcher;
import dev.afkmod.logic.MessageSource;
import dev.afkmod.logic.RecoverySequence;
import dev.afkmod.logic.YawRotation;
import dev.afkmod.stats.AfkStats;

import java.io.IOException;
import java.util.List;

/**
 * Everything a Test Lab scenario may do to the game. The client implementation ({@code GameTestEnvironment}) routes
 * each call into the mod's REAL handlers: a message goes through {@code AfkController.onIncomingText}, a simulated
 * disconnect through the real {@code DisconnectTracker} and {@code onDisconnect}, a simulated join through
 * {@code onJoin}, the recovery hooks through {@code MovementRecovery}. Only the external trigger is faked.
 *
 * <p>Unit tests implement it on top of the real {@link AfkStateMachine} to run the scenario scripts without the game.
 */
public interface TestEnvironment {
	/** The three keys the mod keeps active, in the order {@link PressCounter} uses. */
	enum Key {
		CROUCH("crouch"),
		ATTACK("left click"),
		USE("right click");

		private final String label;

		Key(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	// ---- world and mod ----

	/** A world is loaded and the local player exists. */
	boolean inWorld();

	AfkStateMachine machine();

	/** The effective config (saved config plus the Test Lab's overrides). */
	AfkConfig config();

	/** The periodic check interval ({@code checkIntervalTicks}) in seconds. */
	double checkIntervalSeconds();

	/** How many periodic checks have run so far. */
	long checkCount();

	/** Runs the periodic check on the next tick instead of up to a full interval later. */
	void requestCheck();

	// ---- messages ----

	/**
	 * Feeds a message to the real handler, exactly as if it had arrived from the server (an action bar message also
	 * becomes the real action bar text, a title/subtitle the real title). Returns the handler's classification.
	 */
	KeywordMatcher.Result injectMessage(MessageSource source, String text);

	/** Removes the action bar text from the screen (the queue text "disappears"). */
	void clearActionBar();

	/** What the periodic check would read right now: is the restart queue text on screen? */
	boolean queueTextVisible();

	// ---- connection (simulated: nothing is sent over the network) ----

	/** Runs the mod's handling of a disconnect the player didn't cause (server reconnect/transfer, kick, drop). */
	void simulateServerDisconnect();

	/** Runs the mod's handling of a disconnect the player caused (pause menu Disconnect). */
	void simulateClientDisconnect();

	/** Runs the mod's handling of joining a world (the rejoin after a reconnect). */
	void simulateJoin();

	/** While true, the mod's own disconnect (timer end, recovery give-up) only logs "WOULD DISCONNECT". */
	void setDisconnectDryRun(boolean dryRun);

	int wouldDisconnectCount();

	/** Real disconnects the mod itself has performed this game session. */
	int realDisconnectCount();

	// ---- keys ----

	boolean keyActive(Key key);

	/** True when the player's setting for this key is "Toggle" (false = "Hold"). */
	boolean keyToggleMode(Key key);

	/** {@code ensureInactive} on the key, not counted as a press by the mod. */
	void releaseKeyForTest(Key key);

	/** The instrumented press counter of {@code ensureActive}/{@code ensureInactive}. */
	PressCounter presses();

	/** Forward or jump is held (the recovery walk). */
	boolean walkKeysDown();

	// ---- screens ----

	boolean screenOpen();

	/** Opens a plain test screen (for the screen-open rule). */
	void openTestScreen();

	/** Closes the test screen if it is the one open. */
	void closeTestScreen();

	// ---- player ----

	float yaw();

	float pitch();

	double x();

	double z();

	/** Sets the yaw at once (only the yaw), e.g. to try a turn from a chosen start direction. */
	void setYawForTest(float yaw);

	// ---- movement recovery ----

	RecoverySequence.Phase recoveryPhase();

	/** The current (or last) recovery turn, or null before the first turn of the attempt. */
	YawRotation recoveryRotation();

	/** The outcome of the last finished recovery attempt, or null. */
	RecoverySequence.Outcome lastRecoveryOutcome();

	int recoveryAttemptsStarted();

	/** The next periodic check in ACTIVE reports "stuck" (skips the stuck-detection wait). */
	void forceStuckDetection();

	/** The running recovery attempt ends with {@code outcome} on its next tick. */
	void injectRecoveryOutcome(RecoverySequence.Outcome outcome);

	/** Clears the stuck window (and a pending forced detection) and the attempt counter. */
	void resetRecovery();

	/** The stuck detector's current max distance in the window (blocks). */
	double stuckWindowMaxDistance();

	/** Starts the real smooth turn to the nearest cardinal direction, without jumping or walking. */
	void startTurnOnly();

	boolean turnOnlyRunning();

	/** The current (or last) turn-only rotation, or null. */
	YawRotation turnOnlyRotation();

	/** What the edge check sees in the 2 blocks ahead of the nearest cardinal direction, or null without a world. */
	EdgeReport edgeReport();

	// ---- stats, HUD, files ----

	AfkStats stats();

	/** Shows {@code snapshot} in the HUD with a "[PREVIEW]" tag (null = back to the real HUD). */
	void setHudPreview(HudText.Snapshot snapshot, boolean detailed);

	/** Writes a text file into the config folder and returns its path for display. */
	String writeConfigFile(String fileName, String content) throws IOException;

	void copyToClipboard(String text);

	/** Collects everything the debug report shows. */
	DebugReport.Input reportInput(List<TestResult> results, String overrides);

	/** A client-side chat line (never sent to the server). */
	void chat(String line);

	/** A {@code [TEST]} line in the game log and the debug log. */
	void log(String line);

	// ---- test lifecycle ----

	/** While on, every event, log line and stats effect is test-flagged; {@code label} is shown in the HUD tag. */
	void setTestMode(boolean on, String label);

	/** Undoes what a scenario may have left behind: turn-only rotation, walk keys, HUD preview, test screen, dry run. */
	void cleanupAfterTest();
}
