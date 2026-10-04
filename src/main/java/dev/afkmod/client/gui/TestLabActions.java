package dev.afkmod.client.gui;

import dev.afkmod.client.AfkController;
import dev.afkmod.gui.FieldValidator;
import dev.afkmod.logic.MessageSource;
import dev.afkmod.testlab.Scenario;
import dev.afkmod.testlab.ScenarioInfo;
import dev.afkmod.testlab.TestLab;
import dev.afkmod.testlab.TestOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The Test Lab's logic for the GUI: the option values, running scenarios (with the confirm dialogs), the self-test,
 * abort and the debug report. It was the body of the old standalone Test Lab screen; the Test Lab tab uses it, so there
 * is one copy. All the work is done by the existing {@link TestLab} API; nothing here changes how a test runs.
 * The option values and the last status line are static, so they survive reopening the screen.
 */
final class TestLabActions {
	// Kept across openings of the screen. The defaults come from TestOptions, the same place the tooltips read them from.
	static String messageText = TestOptions.defaults().text;
	static MessageSource messageType = TestOptions.defaults().messageType;
	static String yawText = "";
	static String restartSecondsText = Integer.toString(TestOptions.defaults().testRestartSeconds);
	static String timerSecondsText = Integer.toString(TestOptions.defaults().timerSeconds);
	static boolean fullTimerEnd = TestOptions.defaults().fullTimerEnd;
	static boolean realDisconnect = TestOptions.defaults().allowRealDisconnect;

	private static String statusText = "";
	private static int statusColor = Ui.TEXT;

	private final AfkMenuScreen screen;

	TestLabActions(AfkMenuScreen screen) {
		this.screen = screen;
	}

	static TestLab lab() {
		return AfkController.get().testLab();
	}

	static String status() {
		return statusText;
	}

	static int statusColor() {
		return statusColor;
	}

	static void setStatus(String text, int color) {
		statusText = text;
		statusColor = color;
	}

	/** The first invalid option field, as a message, or null when every option is valid. */
	static @Nullable String optionError() {
		FieldValidator.Parsed<Double> yaw = FieldValidator.optionalDecimal(yawText);
		if (!yaw.ok()) return "Start yaw: " + yaw.error();
		FieldValidator.Parsed<Integer> restart = FieldValidator.wholeNumber(restartSecondsText, "testRestartSeconds");
		if (!restart.ok()) return "Restart length: " + restart.error();
		FieldValidator.Parsed<Integer> timer = FieldValidator.wholeNumber(timerSecondsText, "testTimerSeconds");
		if (!timer.ok()) return "Test timer: " + timer.error();
		return null;
	}

	/** The options as typed; a field that fails validation keeps its default (callers check {@link #optionError()} first). */
	static TestOptions options() {
		TestOptions o = TestOptions.defaults()
				.text(messageText)
				.messageType(messageType)
				.fullTimerEnd(fullTimerEnd)
				.allowRealDisconnect(realDisconnect);
		FieldValidator.Parsed<Double> yaw = FieldValidator.optionalDecimal(yawText);
		if (yaw.ok()) o.yaw = yaw.value();
		FieldValidator.Parsed<Integer> restart = FieldValidator.wholeNumber(restartSecondsText, "testRestartSeconds");
		if (restart.ok()) o.testRestartSeconds(restart.value());
		FieldValidator.Parsed<Integer> timer = FieldValidator.wholeNumber(timerSecondsText, "testTimerSeconds");
		if (timer.ok()) o.timerSeconds(timer.value());
		return o;
	}

	/**
	 * Why {@code s} can't be started right now (the mod is OFF but it needs it ON, no world, another test running), or
	 * null if it can. The confirmation is not counted: it is asked for when the button is pressed.
	 */
	static @Nullable String disabledReason(Scenario s, boolean sendForReal) {
		TestLab lab = lab();
		if (lab.isRunning()) return "A test is running: " + lab.runningLabel() + ". Abort it first.";
		TestOptions o = options().confirmed(true).sendForReal(sendForReal);
		if (!"D5".equals(s.id())) o.allowRealDisconnect(false);
		return lab.refusal(s, o);
	}

	// ---- actions ----

	void runA1(boolean send) {
		Scenario a1 = lab().find("A1");
		start(a1, options().sendForReal(send).allowRealDisconnect(false));
	}

	void runScenario(Scenario s) {
		TestOptions o = options();
		if (!"D5".equals(s.id())) o.allowRealDisconnect(false); // only D5 uses the real-disconnect switch
		if ("A1".equals(s.id())) o.sendForReal(false);
		start(s, o);
	}

	void copyReport() {
		Scenario i1 = lab().find("I1");
		start(i1, TestOptions.defaults());
	}

	void runSelfTest() {
		TestLab.StartResult result = lab().runSelfTest();
		setStatus(result.message(), result.started() ? Ui.GREEN : Ui.ERROR);
		if (result.started()) Minecraft.getInstance().setScreen(null);
	}

	void abort() {
		if (lab().isRunning()) {
			lab().abort();
			setStatus("Aborted", Ui.TEXT);
		} else {
			setStatus("Nothing is running", Ui.GREY);
		}
	}

	/** Asks for confirmation when needed (a second time for a real disconnect), then starts. */
	private void start(@Nullable Scenario s, TestOptions o) {
		if (s == null) return;
		String problem = optionError();
		if (problem != null) {
			setStatus(problem, Ui.ERROR);
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		boolean confirm = s.needsConfirm(o) || o.allowRealDisconnect;
		if (confirm && !o.confirmed) {
			String warning = ScenarioInfo.warning(s.id());
			String what = warning != null ? warning : "This test moves the player or can disconnect.";
			mc.setScreen(new ConfirmScreen(yes -> {
				if (!yes) {
					mc.setScreen(screen);
					return;
				}
				if (o.allowRealDisconnect) {
					// A separate confirmation for the real disconnect.
					mc.setScreen(new ConfirmScreen(sure -> {
						if (sure) launch(s, o.confirmed(true));
						else mc.setScreen(screen);
					}, Component.literal("Really disconnect?"),
							Component.literal("At the end of " + s.label() + " the mod will really disconnect you. Continue?")));
				} else {
					launch(s, o.confirmed(true));
				}
			}, Component.literal("Run " + s.label() + "?"), Component.literal(what + " " + s.description())));
			return;
		}
		launch(s, o);
	}

	private void launch(Scenario s, TestOptions o) {
		Minecraft mc = Minecraft.getInstance();
		TestLab.StartResult result = lab().run(s, o);
		if (!result.started()) {
			setStatus(result.message(), Ui.ERROR);
			if (mc.screen != screen) mc.setScreen(screen);
			return;
		}
		setStatus(result.message(), Ui.GREEN);
		if (s.runsInGame(o)) {
			// The mod does nothing while a screen is open, so the test plays out with no screen.
			mc.setScreen(null);
		} else if (mc.screen != screen) {
			mc.setScreen(screen);
		}
	}
}
