package dev.afkmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.afkmod.AfkModClient;
import dev.afkmod.client.KeyControl.Action;
import dev.afkmod.client.gui.AfkMenuScreen;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.logic.AfkStateMachine;
import dev.afkmod.logic.AfkStateMachine.DisconnectCause;
import dev.afkmod.logic.AfkStateMachine.Reason;
import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.logic.DisconnectTracker;
import dev.afkmod.logic.KeywordMatcher;
import dev.afkmod.logic.MessageSource;
import dev.afkmod.mixin.GuiAccessor;
import dev.afkmod.stats.AfkStats;
import dev.afkmod.stats.StatsRecorder;
import dev.afkmod.testlab.Scenarios;
import dev.afkmod.testlab.TestLab;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Connects the Minecraft client to the pure-Java {@link AfkStateMachine}: keybinds, the periodic check,
 * incoming text, join/disconnect/death events, and reacting to state transitions (releasing keys,
 * disconnecting when the timer ends).
 *
 * <p>One instance lives for the whole game session, so the mod's state survives the server-initiated
 * reconnect (connecting screen, then rejoin) during a restart. Nothing per-world is cached: the player and
 * level are read from {@link Minecraft} each time, and world-scoped helpers are reset on every join.
 *
 * <p>It also hosts the {@link TestLab}: the scenarios reach the same handlers through {@link GameTestEnvironment}
 * ({@link #onIncomingText}, {@link #simulateDisconnect}, {@link #simulateJoin}), and while a test runs everything is
 * test-flagged and the mod's own disconnects only log "WOULD DISCONNECT" unless the test was confirmed for a real one.
 */
public final class AfkController {
	private static AfkController instance;

	private final AfkStateMachine machine = new AfkStateMachine(AfkModClient::config, System::nanoTime);
	private final DisconnectTracker disconnects = new DisconnectTracker();
	private final MessageLog messageLog = new MessageLog();
	/** Session stats, lifetime totals and the event log, persisted to {@code config/afkmod-stats.json}. */
	private final AfkStats stats = new AfkStats(FabricLoader.getInstance().getConfigDir().resolve("afkmod-stats.json"));
	private final StatsRecorder statsRecorder = new StatsRecorder(stats);
	/** Stuck detection and recovery. Its final "log out" defaults to the server-list disconnect after the tick. */
	private final MovementRecovery recovery = new MovementRecovery(machine, messageLog, stats, () -> recoveryDisconnectPending = true);
	private final GameTestEnvironment testEnvironment = new GameTestEnvironment(this);
	private final TestLab testLab = new TestLab(testEnvironment, System::nanoTime, System::currentTimeMillis,
			AfkModClient.overrides(), Scenarios.all());

	private KeyMapping toggleKey;
	private KeyMapping menuKey;
	private KeyMapping testLabKey;
	private KeyMapping abortTestKey;
	private int ticksSinceCheck;
	private long checkCount;
	private boolean timerDisconnectPending;
	private boolean recoveryDisconnectPending;
	/** Last known state of the restart queue text, only for logging when it appears and disappears. */
	private boolean queueTextShown;
	/** Cleared if the action bar state can't be read; the time of the last queue message is used instead. */
	private boolean overlayReadable = true;
	private long lastQueueMessageAt;

	// Test Lab state.
	private boolean testMode;
	private String testLabel;
	private boolean disconnectDryRun;
	private int wouldDisconnects;
	private int realDisconnects;

	private AfkController() {
	}

	/** Null until {@link #init()} has run (before that, mixin hooks have nothing to report to). */
	public static AfkController get() {
		return instance;
	}

	public static void init() {
		instance = new AfkController();
		instance.register();
	}

	public AfkStateMachine machine() {
		return machine;
	}

	public MovementRecovery recovery() {
		return recovery;
	}

	/** Stats and event log API for the GUI and the Test Lab. */
	public AfkStats stats() {
		return stats;
	}

	/** The Test Lab API: listScenarios, run, abort, getResults, isRunning, runSelfTest. */
	public TestLab testLab() {
		return testLab;
	}

	MessageLog messageLog() {
		return messageLog;
	}

	/** The mod's keybinds, for the debug report. */
	List<KeyMapping> keyMappings() {
		return List.of(toggleKey, menuKey, testLabKey, abortTestKey);
	}

	private void register() {
		KeyMapping.Category category =
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath(AfkModClient.MOD_ID, AfkModClient.MOD_ID));
		toggleKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.afkmod.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_K, category));
		menuKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.afkmod.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_J, category));
		// Both unbound by default (InputConstants.UNKNOWN is KEYSYM -1).
		testLabKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.afkmod.testlab", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		abortTestKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.afkmod.abort_test", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));

		machine.setListener(this::onTransition);
		machine.setTimerListener(statsRecorder::onTimerSet);
		stats.setEventSink(messageLog::statsEvent);
		// Client-side block breaks while the mod mines (the client's own break, not a server confirmation).
		ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> {
			if (machine.isActiveOrRecovering() && !testMode) stats.blockMined();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			if (testLab.isRunning()) testLab.abort();
			stats.shutdown();
		});

		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
		AfkHud.register();
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> addPauseMenuButton(screen));

		ClientReceiveMessageEvents.GAME.register((message, overlay) ->
				onIncomingText(overlay ? MessageSource.ACTION_BAR : MessageSource.SYSTEM, message));
		// Player chat is disabled on the target server; it's only logged (with debug logging on) to help diagnose.
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
				messageLog.log(MessageSource.CHAT, message.getString(), KeywordMatcher.Result.NONE));

		ClientPlayConnectionEvents.JOIN.register((listener, sender, mc) -> onJoin());
		ClientPlayConnectionEvents.DISCONNECT.register((listener, mc) -> {
			// May run on the network thread: read the cause now, act on the client thread.
			DisconnectCause cause = disconnects.consume();
			mc.execute(() -> {
				// A real disconnect ends (or aborts) a running test before the mod reacts to it.
				testLab.onRealDisconnect();
				onDisconnect(cause);
			});
		});
	}

	// ---- input from mixins and events (client thread) ----

	/** Every incoming system, action bar, title, subtitle and boss bar text. Ignore rule, then match rule. */
	public KeywordMatcher.Result onIncomingText(MessageSource source, Component text) {
		if (text == null) return KeywordMatcher.Result.NONE;
		String plain = text.getString();
		boolean overlay = source == MessageSource.ACTION_BAR;
		KeywordMatcher.Result result = machine.onMessage(plain, overlay);
		messageLog.log(source, plain, result);
		if (overlay && machine.isQueueText(plain)) {
			lastQueueMessageAt = System.nanoTime();
			setQueueTextShown(true);
		}
		return result;
	}

	/** The local player died (combat kill packet for our own entity id). */
	public void onLocalPlayerDeath() {
		if (testLab.isRunning()) testLab.onDeath();
		machine.onDeath();
	}

	/** Once per frame, before rendering (from {@code MouseHandlerMixin}): the smooth recovery turn. */
	public void onFrame() {
		recovery.onFrame();
		testLab.onFrame();
	}

	/** {@code ClientLevel.disconnect} was called: the player (not the server) is leaving the world. */
	public void onClientQuit() {
		disconnects.markClientQuit();
	}

	/** The ON/OFF button in the GUI. During a test this aborts it and turns the mod off. */
	public void toggleFromGui(Minecraft mc) {
		if (testLab.isRunning()) {
			testLab.onUserToggle();
			machine.turnOff();
		} else if (machine.isOn()) {
			machine.turnOff();
		} else if (mc.player != null) {
			machine.turnOn();
		}
	}

	private void onJoin() {
		messageLog.event("Joined world while " + machine.state());
		statsRecorder.onJoin(machine.state());
		disconnects.reset();
		recovery.onJoin();
		machine.onJoin();
		ticksSinceCheck = 0;
	}

	private void onDisconnect(DisconnectCause cause) {
		// UNEXPECTED includes the server's own reconnect/transfer: only the pause menu (or quitting the game)
		// and the mod's timer count as a manual disconnect.
		messageLog.event("Disconnected (" + cause + ") while " + machine.state());
		machine.onDisconnect(cause);
	}

	// ---- Test Lab entry points (the same handlers the real events use; nothing is sent to the server) ----

	/**
	 * Runs the mod's disconnect handling as if the connection had closed: the real {@link DisconnectTracker} classifies
	 * it (a client quit if {@code clientQuit}, otherwise "unexpected", like a server reconnect) and
	 * {@link #onDisconnect} reacts. The connection is untouched.
	 */
	void simulateDisconnect(boolean clientQuit) {
		if (clientQuit) disconnects.markClientQuit();
		onDisconnect(disconnects.consume());
	}

	/** Runs the mod's join handling (the rejoin after a reconnect). */
	void simulateJoin() {
		onJoin();
	}

	/** Runs the periodic check on the next tick. */
	void requestCheck() {
		ticksSinceCheck = Integer.MAX_VALUE - 1;
	}

	long checkCount() {
		return checkCount;
	}

	/** What the periodic check would read now: is the restart queue text on screen? */
	boolean isQueueTextVisibleNow() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && isQueueTextVisible(mc);
	}

	void setDisconnectDryRun(boolean dryRun) {
		disconnectDryRun = dryRun;
	}

	int wouldDisconnectCount() {
		return wouldDisconnects;
	}

	int realDisconnectCount() {
		return realDisconnects;
	}

	/** Test mode: stats, events and log lines are test-flagged; {@code label} is shown in the HUD tag. */
	void setTestMode(boolean on, String label) {
		testMode = on;
		testLabel = on ? label : null;
		statsRecorder.setTestMode(on);
		recovery.setStatsTest(on);
		messageLog.setTestMode(on);
		if (!on) disconnectDryRun = false;
	}

	/** The HUD tag text while a test runs, or null. */
	public String testTag() {
		String label = testLab.runningLabel();
		return label != null ? label : testLabel;
	}

	// ---- tick loop ----

	private void onEndTick(Minecraft mc) {
		// Keybinds are handled every tick so they feel instant.
		while (toggleKey.consumeClick()) {
			if (testLab.isRunning()) {
				// Toggling during a test aborts it and turns the mod off.
				testLab.onUserToggle();
				machine.turnOff();
			} else if (machine.isOn()) {
				machine.turnOff();
			} else if (mc.player != null) {
				machine.turnOn();
			}
		}
		while (menuKey.consumeClick()) {
			if (mc.screen == null) mc.setScreen(new AfkMenuScreen(null));
		}
		while (testLabKey.consumeClick()) {
			// The standalone Test Lab keybind opens the same screen on its Test Lab tab.
			if (mc.screen == null) mc.setScreen(new AfkMenuScreen(null, AfkMenuScreen.TAB_TEST_LAB));
		}
		while (abortTestKey.consumeClick()) {
			if (testLab.isRunning()) testLab.abort();
		}

		AfkConfig config = AfkModClient.config();
		// The recovery sequence needs per-tick precision: the one exception to the 20-tick rule.
		if (machine.state() == State.RECOVERING) recovery.onTick(mc, config);
		recovery.onTestTurnTick();
		if (++ticksSinceCheck >= Math.max(1, config.checkIntervalTicks)) {
			ticksSinceCheck = 0;
			runCheck(mc, config);
		}

		if (timerDisconnectPending) {
			timerDisconnectPending = false;
			disconnectToServerList(mc, "Timer finished");
		}
		if (recoveryDisconnectPending) {
			recoveryDisconnectPending = false;
			disconnectToServerList(mc, "Movement recovery failed");
		}
		testLab.tick();
	}

	/** The periodic check: state machine timeouts, then correct the keys. */
	private void runCheck(Minecraft mc, AfkConfig config) {
		checkCount++;
		KeyControl.presses().beginCheck();
		LocalPlayer player = mc.player;
		boolean worldReady = player != null && mc.level != null && mc.getConnection() != null;

		// Fallback for death (the combat-kill packet hook is the instant path).
		if (worldReady && machine.isOn() && player.isDeadOrDying()) {
			if (testLab.isRunning()) testLab.onDeath();
			machine.onDeath();
			return;
		}

		boolean queueVisible = mc.level != null && isQueueTextVisible(mc);
		if (mc.level != null) setQueueTextShown(queueVisible);
		machine.tick(worldReady, queueVisible);
		recovery.onCheck(mc, config, worldReady);
		stats.maybeAutoSave();

		// Never touch keys while a screen (chat, inventory, menus) is open; re-verify after it closes.
		if (!worldReady || mc.screen != null) return;

		switch (machine.state()) {
			case ACTIVE -> {
				press(mc, Action.CROUCH);
				press(mc, Action.ATTACK);
				press(mc, Action.USE);
			}
			case RESTARTING, SETTLING, RECONNECTING -> {
				// Keep them released (closing a screen can restore toggled keys by itself).
				release(mc, Action.ATTACK);
				release(mc, Action.USE);
				if (config.releaseCrouchOnRestart) release(mc, Action.CROUCH);
			}
			default -> { }
		}
	}

	/**
	 * Whether the restart queue text is on screen right now, read from the client's own action bar state. If that
	 * can't be read, falls back to "a queue message arrived within the vanilla display time (60 ticks)".
	 */
	private boolean isQueueTextVisible(Minecraft mc) {
		if (overlayReadable) {
			try {
				GuiAccessor gui = (GuiAccessor) mc.gui;
				Component text = gui.afkmod$getOverlayMessage();
				return text != null && gui.afkmod$getOverlayMessageTime() > 0 && machine.isQueueText(text.getString());
			} catch (RuntimeException | LinkageError e) {
				overlayReadable = false;
				AfkModClient.LOGGER.warn("Can't read the action bar state, using message timing for the queue text", e);
			}
		}
		return lastQueueMessageAt != 0 && System.nanoTime() - lastQueueMessageAt < 3_000_000_000L;
	}

	private void setQueueTextShown(boolean shown) {
		if (shown == queueTextShown) return;
		queueTextShown = shown;
		messageLog.event(shown ? "Queue text appeared" : "Queue text disappeared");
		if (!shown) statsRecorder.onQueueTextGone(machine.state());
	}

	// ---- reacting to transitions ----

	private void onTransition(State from, State to, Reason reason) {
		AfkModClient.LOGGER.info("{}{} -> {} ({})", testMode ? "[TEST] " : "", from, to, reason);
		Minecraft mc = Minecraft.getInstance();
		// Clears the stuck window; leaving RECOVERING early cancels the attempt (releases W and jump).
		recovery.onTransition(from, to);
		statsRecorder.onTransition(from, to, reason);
		testLab.onTransition(from, to, reason);

		if (to == State.OFF) {
			release(mc, Action.ATTACK);
			release(mc, Action.USE);
			release(mc, Action.CROUCH);
		} else if (to != State.ACTIVE && to != State.RECOVERING) {
			// RESTARTING, SETTLING or RECONNECTING (the recovery releases its own keys, ACTIVE presses them).
			release(mc, Action.ATTACK);
			release(mc, Action.USE);
			if (AfkModClient.config().releaseCrouchOnRestart) release(mc, Action.CROUCH);
		}

		if (to == State.ACTIVE) {
			// Verify the keys on the next tick rather than up to a full interval later.
			ticksSinceCheck = Integer.MAX_VALUE - 1;
		}
		if (reason == Reason.TIMER_END) {
			// Disconnect after the state machine call returns, never from inside it.
			timerDisconnectPending = true;
		}
	}

	// ---- pause menu button ----

	/** Adds an "Fresh AFK" button under the last button of the pause menu. */
	private static void addPauseMenuButton(Screen screen) {
		if (!(screen instanceof PauseScreen pause) || !pause.showsPauseMenu()) return;
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		AbstractWidget lowest = null;
		for (AbstractWidget widget : widgets) {
			if (widget instanceof Button && (lowest == null || widget.getY() > lowest.getY())) lowest = widget;
		}
		if (lowest == null) return;
		Button button = Button.builder(Component.literal("Fresh AFK"),
						b -> Minecraft.getInstance().setScreen(new AfkMenuScreen(screen)))
				.bounds(lowest.getX(), lowest.getY() + lowest.getHeight() + 4, lowest.getWidth(), 20).build();
		widgets.add(button);
	}

	private static void press(Minecraft mc, Action action) {
		if (KeyControl.ensureActive(mc, action)) AfkModClient.LOGGER.debug("Pressed {}", action);
	}

	private static void release(Minecraft mc, Action action) {
		if (KeyControl.ensureInactive(mc, action)) AfkModClient.LOGGER.debug("Released {}", action);
	}

	/**
	 * Timer end or failed movement recovery: leave the world and land on the multiplayer server list (not the title
	 * screen). Marked as the mod's own disconnect, so it is never mistaken for a server relog. During a Test Lab
	 * scenario that wasn't confirmed for a real disconnect, only "WOULD DISCONNECT" is logged.
	 */
	private void disconnectToServerList(Minecraft mc, String why) {
		if (mc.getConnection() == null && mc.level == null) return;
		if (disconnectDryRun) {
			wouldDisconnects++;
			messageLog.test("WOULD DISCONNECT to the server list (" + why + "); skipped because a test is running");
			statsRecorder.onLogout("WOULD DISCONNECT (test): " + why);
			return;
		}
		realDisconnects++;
		AfkModClient.LOGGER.info("{}{}, disconnecting to the server list", testMode ? "[TEST] " : "", why);
		statsRecorder.onLogout(why);
		disconnects.markModDisconnect();
		// Same sequence as the pause menu's Disconnect button, but always ending on the multiplayer screen.
		if (mc.level != null) mc.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
		mc.disconnect(new JoinMultiplayerScreen(new TitleScreen()), false);
	}
}
