package dev.afkmod.client;

import dev.afkmod.AfkModClient;
import dev.afkmod.client.KeyControl.Action;
import dev.afkmod.config.AfkConfig;
import dev.afkmod.logic.AfkStateMachine;
import dev.afkmod.logic.DurationParser;
import dev.afkmod.logic.HudText;
import dev.afkmod.logic.KeywordMatcher;
import dev.afkmod.logic.MessageSource;
import dev.afkmod.logic.RecoverySequence;
import dev.afkmod.logic.YawRotation;
import dev.afkmod.stats.AfkStats;
import dev.afkmod.testlab.DebugReport;
import dev.afkmod.testlab.EdgeReport;
import dev.afkmod.testlab.PressCounter;
import dev.afkmod.testlab.TestEnvironment;
import dev.afkmod.testlab.TestResult;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The Test Lab's view of the game. Every call goes into the mod's REAL code: messages through
 * {@link AfkController#onIncomingText} (an action bar message is also set as the real action bar, a title as the real
 * title, like the packet handlers do), disconnects and joins through the controller's own handling, keys through
 * {@link KeyControl}, recovery hooks through {@link MovementRecovery}. Nothing here sends anything to the server.
 */
final class GameTestEnvironment implements TestEnvironment {
	private final AfkController controller;

	GameTestEnvironment(AfkController controller) {
		this.controller = controller;
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	private static Action action(Key key) {
		return switch (key) {
			case CROUCH -> Action.CROUCH;
			case ATTACK -> Action.ATTACK;
			case USE -> Action.USE;
		};
	}

	// ---- world and mod ----

	@Override
	public boolean inWorld() {
		Minecraft mc = mc();
		return mc.player != null && mc.level != null && mc.getConnection() != null;
	}

	@Override
	public AfkStateMachine machine() {
		return controller.machine();
	}

	@Override
	public AfkConfig config() {
		return AfkModClient.config();
	}

	@Override
	public double checkIntervalSeconds() {
		return Math.max(1, AfkModClient.config().checkIntervalTicks) / 20.0;
	}

	@Override
	public long checkCount() {
		return controller.checkCount();
	}

	@Override
	public void requestCheck() {
		controller.requestCheck();
	}

	// ---- messages ----

	@Override
	public KeywordMatcher.Result injectMessage(MessageSource source, String text) {
		Minecraft mc = mc();
		Component component = Component.literal(text);
		// Show it where the server's packet would (the real handler then reads it like any other message).
		switch (source) {
			case ACTION_BAR -> mc.gui.setOverlayMessage(component, false);
			case TITLE -> mc.gui.setTitle(component);
			case SUBTITLE -> mc.gui.setSubtitle(component);
			case SYSTEM -> mc.gui.getChat().addClientSystemMessage(text.startsWith("[TEST]") ? component
					: Component.literal("[TEST] ").append(component));
			default -> { }
		}
		if (source == MessageSource.CHAT) return KeywordMatcher.Result.NONE; // player chat is never used for detection
		return controller.onIncomingText(source, component);
	}

	@Override
	public void clearActionBar() {
		mc().gui.setOverlayMessage(Component.empty(), false);
	}

	@Override
	public boolean queueTextVisible() {
		return controller.isQueueTextVisibleNow();
	}

	// ---- connection ----

	@Override
	public void simulateServerDisconnect() {
		log("Simulated server-initiated disconnect (the connection is NOT closed)");
		controller.simulateDisconnect(false);
	}

	@Override
	public void simulateClientDisconnect() {
		log("Simulated client-initiated disconnect (the connection is NOT closed)");
		controller.simulateDisconnect(true);
	}

	@Override
	public void simulateJoin() {
		log("Simulated join (rejoin after the reconnect)");
		controller.simulateJoin();
	}

	@Override
	public void setDisconnectDryRun(boolean dryRun) {
		controller.setDisconnectDryRun(dryRun);
	}

	@Override
	public int wouldDisconnectCount() {
		return controller.wouldDisconnectCount();
	}

	@Override
	public int realDisconnectCount() {
		return controller.realDisconnectCount();
	}

	// ---- keys ----

	@Override
	public boolean keyActive(Key key) {
		return KeyControl.isActive(mc(), action(key));
	}

	@Override
	public boolean keyToggleMode(Key key) {
		return KeyControl.isToggleMode(mc().options, action(key));
	}

	@Override
	public void releaseKeyForTest(Key key) {
		PressCounter presses = KeyControl.presses();
		presses.setMuted(true);
		try {
			KeyControl.ensureInactive(mc(), action(key));
		} finally {
			presses.setMuted(false);
		}
	}

	@Override
	public PressCounter presses() {
		return KeyControl.presses();
	}

	@Override
	public boolean walkKeysDown() {
		Minecraft mc = mc();
		return mc.options.keyUp.isDown() || mc.options.keyJump.isDown();
	}

	// ---- screens ----

	@Override
	public boolean screenOpen() {
		return mc().screen != null;
	}

	@Override
	public void openTestScreen() {
		mc().setScreen(new TestLabDummyScreen());
	}

	@Override
	public void closeTestScreen() {
		Minecraft mc = mc();
		if (mc.screen instanceof TestLabDummyScreen) mc.setScreen(null);
	}

	// ---- player ----

	@Override
	public float yaw() {
		LocalPlayer player = mc().player;
		return player == null ? 0f : player.getYRot();
	}

	@Override
	public float pitch() {
		LocalPlayer player = mc().player;
		return player == null ? 0f : player.getXRot();
	}

	@Override
	public double x() {
		LocalPlayer player = mc().player;
		return player == null ? 0 : player.getX();
	}

	@Override
	public double z() {
		LocalPlayer player = mc().player;
		return player == null ? 0 : player.getZ();
	}

	@Override
	public void setYawForTest(float yaw) {
		controller.recovery().setYawForTest(yaw);
	}

	// ---- movement recovery ----

	@Override
	public RecoverySequence.Phase recoveryPhase() {
		return controller.recovery().sequence().phase();
	}

	@Override
	public YawRotation recoveryRotation() {
		return controller.recovery().sequence().rotation();
	}

	@Override
	public RecoverySequence.Outcome lastRecoveryOutcome() {
		return controller.recovery().lastOutcome();
	}

	@Override
	public int recoveryAttemptsStarted() {
		return controller.recovery().attemptsStarted();
	}

	@Override
	public void forceStuckDetection() {
		controller.recovery().forceStuckDetection();
	}

	@Override
	public void injectRecoveryOutcome(RecoverySequence.Outcome outcome) {
		controller.recovery().injectAttemptOutcome(outcome);
	}

	@Override
	public void resetRecovery() {
		controller.recovery().resetForTest();
	}

	@Override
	public double stuckWindowMaxDistance() {
		return controller.recovery().detector().maxDistanceFromCurrent();
	}

	@Override
	public void startTurnOnly() {
		controller.recovery().startTestTurn(AfkModClient.config().recoveryTurnSeconds);
	}

	@Override
	public boolean turnOnlyRunning() {
		return controller.recovery().isTestTurnRunning();
	}

	@Override
	public YawRotation turnOnlyRotation() {
		return controller.recovery().testTurnRotation();
	}

	@Override
	public EdgeReport edgeReport() {
		return controller.recovery().edgeReport();
	}

	// ---- stats, HUD, files ----

	@Override
	public AfkStats stats() {
		return controller.stats();
	}

	@Override
	public void setHudPreview(HudText.Snapshot snapshot, boolean detailed) {
		AfkHud.setPreview(snapshot, detailed);
	}

	@Override
	public String writeConfigFile(String fileName, String content) throws IOException {
		Path dir = FabricLoader.getInstance().getConfigDir();
		Files.createDirectories(dir);
		Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8);
		return "config/" + fileName;
	}

	@Override
	public void copyToClipboard(String text) {
		mc().keyboardHandler.setClipboard(text);
	}

	@Override
	public DebugReport.Input reportInput(List<TestResult> results, String overrides) {
		Minecraft mc = mc();
		AfkStateMachine machine = controller.machine();
		AfkConfig config = AfkModClient.config();
		List<String> keybinds = new ArrayList<>();
		for (KeyMapping key : controller.keyMappings()) {
			keybinds.add(Component.translatable(key.getName()).getString() + ": " + key.getTranslatedKeyMessage().getString());
		}
		for (KeyMapping key : List.of(mc.options.keyShift, mc.options.keyAttack, mc.options.keyUse)) {
			keybinds.add(Component.translatable(key.getName()).getString() + ": " + key.getTranslatedKeyMessage().getString());
		}
		keybinds.add("Toggle crouch/attack/use: " + mc.options.toggleCrouch().get() + "/" + mc.options.toggleAttack().get()
				+ "/" + mc.options.toggleUse().get());
		String state = "State " + machine.state() + " (last reason " + machine.lastReason() + "), restarts this session "
				+ machine.restartCount() + ", timer " + (machine.hasTimer()
				? DurationParser.format(machine.timerRemainingSeconds()) + (machine.isTimerPaused() ? " (paused)" : "") : "none")
				+ ", post-resume cooldown " + (machine.isInPostResumeCooldown() ? "on" : "off")
				+ ", in a world " + (inWorld() ? "yes" : "no") + ", screen " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())
				+ ", recovery phase " + recoveryPhase();
		ServerData server = mc.getCurrentServer();
		return new DebugReport.Input(
				version("afkmod"), SharedConstants.getCurrentVersion().name(), version("fabricloader"), version("fabric-api"),
				System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")",
				System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"),
				state, config.toJson(), overrides, keybinds, controller.stats().getRecentEvents(DebugReport.MAX_EVENTS), results,
				controller.messageLog().recentLines(), server == null ? null : server.ip, mc.getUser().getName(),
				AfkModClient.savedConfig().reportRedactIdentity, System.currentTimeMillis(), null);
	}

	private static String version(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("not found");
	}

	@Override
	public void chat(String line) {
		Minecraft mc = mc();
		if (mc.player != null) mc.gui.getChat().addClientSystemMessage(Component.literal("[TEST] " + line));
	}

	@Override
	public void log(String line) {
		controller.messageLog().test(line);
	}

	// ---- test lifecycle ----

	@Override
	public void setTestMode(boolean on, String label) {
		controller.setTestMode(on, label);
	}

	@Override
	public void cleanupAfterTest() {
		Minecraft mc = mc();
		controller.recovery().cancelTestTurn();
		// Release forward/jump if something left them held outside a recovery (the recovery releases its own).
		if (controller.machine().state() != AfkStateMachine.State.RECOVERING && walkKeysDown()) {
			mc.options.keyUp.setDown(false);
			mc.options.keyJump.setDown(false);
		}
		AfkHud.setPreview(null, false);
		closeTestScreen();
		KeyControl.presses().setMuted(false);
	}
}
