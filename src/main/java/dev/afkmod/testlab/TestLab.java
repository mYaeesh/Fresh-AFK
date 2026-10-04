package dev.afkmod.testlab;

import dev.afkmod.logic.AfkStateMachine;
import dev.afkmod.logic.AfkStateMachine.Reason;
import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.stats.EndReason;
import dev.afkmod.stats.EventType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The Test Lab: runs one scenario at a time (or the self-test, a queue of them), ticks its steps, records results and
 * puts the mod back as it was afterwards. Pure Java; the game is reached only through {@link TestEnvironment}.
 *
 * <p>Runner states: idle, or running one scenario (optionally inside a self-test). A run ends with a result when a step
 * passes/fails/reports, on an error, or on an abort. Afterwards, always: overrides cleared, the environment cleaned up,
 * and (unless the player caused the abort) the mod put back to ACTIVE with its timer, restart count and cooldown as
 * they were. The test flag is then switched off and, if the mod ended up OFF with the real session still open, that
 * session ends as {@link EndReason#TEST_LAB}.
 *
 * <p>API: {@link #listScenarios()}, {@link #run}, {@link #abort()}, {@link #getResults()}, {@link #isRunning()},
 * {@link #runSelfTest()}.
 */
public final class TestLab {
	public static final int MAX_RESULTS = 50;
	public static final String SELF_TEST_FILE = "afkmod-selftest.txt";
	public static final String REPORT_FILE = "afkmod-report.txt";
	/** Pause between self-test scenarios so the periodic check settles the keys again. */
	public static final double SUITE_GAP_SECONDS = 1.0;
	/** Instant steps completed within one tick at most (a guard against a scenario that never waits). */
	private static final int MAX_STEPS_PER_TICK = 200;

	/** Whether {@link #run} started the scenario, and if not, why. */
	public record StartResult(boolean started, String message) {
		static StartResult ok(String message) {
			return new StartResult(true, message);
		}

		static StartResult refused(String message) {
			return new StartResult(false, message);
		}
	}

	private final TestEnvironment env;
	private final LongSupplier clock;
	private final LongSupplier wallClockMs;
	private final ConfigOverrides overrides;
	private final List<Scenario> scenarios;
	private final RingBuffer<TestResult> results = new RingBuffer<>(MAX_RESULTS);
	private Consumer<TestResult> resultListener = r -> { };

	// The running scenario.
	private Scenario current;
	private TestContext ctx;
	private List<Step> steps;
	private int stepIndex;
	private boolean stepStarted;
	private long startedAt;
	private boolean wasActive;
	private AfkStateMachine.TestSnapshot snapshot;

	// The running self-test.
	private Suite suite;
	private String lastSelfTestReport;

	private static final class Suite {
		final List<Scenario> queue;
		final List<TestResult> results = new ArrayList<>();
		final long startedAt;
		final long startedEpochMs;
		final boolean modWasOn;
		int next;
		long gapUntil;
		boolean aborted;

		Suite(List<Scenario> queue, long startedAt, long startedEpochMs, boolean modWasOn) {
			this.queue = queue;
			this.startedAt = startedAt;
			this.startedEpochMs = startedEpochMs;
			this.modWasOn = modWasOn;
			this.gapUntil = startedAt;
		}
	}

	public TestLab(TestEnvironment env, LongSupplier nanoClock, LongSupplier wallClockMs, ConfigOverrides overrides,
	               List<Scenario> scenarios) {
		this.env = Objects.requireNonNull(env);
		this.clock = Objects.requireNonNull(nanoClock);
		this.wallClockMs = Objects.requireNonNull(wallClockMs);
		this.overrides = Objects.requireNonNull(overrides);
		this.scenarios = List.copyOf(scenarios);
	}

	// ---- API ----

	public List<Scenario> listScenarios() {
		return scenarios;
	}

	/** The scenario with this id (e.g. "B1"), or null. */
	public Scenario find(String id) {
		for (Scenario s : scenarios) {
			if (s.id().equalsIgnoreCase(id)) return s;
		}
		return null;
	}

	/** Recorded results, oldest first (the last {@value #MAX_RESULTS}). */
	public List<TestResult> getResults() {
		return results.all();
	}

	public TestResult lastResult() {
		List<TestResult> all = results.all();
		return all.isEmpty() ? null : all.getLast();
	}

	/** A scenario or the self-test is running. */
	public boolean isRunning() {
		return current != null || suite != null;
	}

	public boolean isSelfTestRunning() {
		return suite != null;
	}

	/** The running scenario, or null. */
	public Scenario current() {
		return current;
	}

	/** What the HUD tag shows, e.g. {@code B1 Full flow with reconnect} or {@code Self-test 3/31: B1 ...}; null when idle. */
	public String runningLabel() {
		if (suite == null) return current == null ? null : current.label();
		String prefix = "Self-test " + suite.next + "/" + suite.queue.size();
		return current == null ? prefix : prefix + ": " + current.label();
	}

	/** The text of the last self-test report, or null. */
	public String lastSelfTestReport() {
		return lastSelfTestReport;
	}

	public ConfigOverrides overrides() {
		return overrides;
	}

	public void setResultListener(Consumer<TestResult> listener) {
		this.resultListener = Objects.requireNonNull(listener);
	}

	/** Starts {@code scenario}, or refuses (another test running, the mod not ON, no confirmation, ...) and says why. */
	public StartResult run(Scenario scenario, TestOptions options) {
		Objects.requireNonNull(scenario);
		Objects.requireNonNull(options);
		if (isRunning()) return StartResult.refused("A test is already running: " + runningLabel() + ". Abort it first.");
		String why = refusal(scenario, options);
		if (why != null) return StartResult.refused(why);
		start(scenario, options);
		return StartResult.ok("Started " + scenario.label());
	}

	/** Why {@code scenario} can't run right now, or null if it can. */
	public String refusal(Scenario scenario, TestOptions options) {
		if (scenario.requiresWorld(options) && !env.inWorld()) {
			return scenario.label() + " needs a world: join a world first.";
		}
		if (scenario.requiresModOn(options) && env.machine().state() != State.ACTIVE) {
			return scenario.label() + " needs the mod ON and mining (ACTIVE); it is " + env.machine().state() + ".";
		}
		if (scenario.needsConfirm(options) && !options.confirmed) {
			return scenario.label() + " needs confirmation: it moves the player or can disconnect.";
		}
		if (options.allowRealDisconnect && !options.confirmed) {
			return "A real disconnect needs confirmation.";
		}
		return scenario.precondition(env, options);
	}

	/**
	 * Runs every scenario marked {@code safeForAuto}, in list order. Scenarios that can't run (e.g. the mod is OFF) are
	 * recorded as INFO "Skipped". At the end the report is saved to {@value #SELF_TEST_FILE}, and the overrides and all
	 * test data are cleared.
	 */
	public StartResult runSelfTest() {
		if (isRunning()) return StartResult.refused("A test is already running: " + runningLabel() + ". Abort it first.");
		List<Scenario> queue = scenarios.stream().filter(Scenario::safeForAuto).toList();
		boolean modOn = env.machine().state() == State.ACTIVE;
		suite = new Suite(queue, clock.getAsLong(), wallClockMs.getAsLong(), modOn);
		env.log("Self-test started: " + queue.size() + " scenarios" + (modOn ? "" : " (the mod is OFF: scenarios that need it are skipped)"));
		return StartResult.ok(modOn ? "Self-test started (" + queue.size() + " scenarios)"
				: "Self-test started; the mod is OFF, so the scenarios that need it will be skipped");
	}

	/** Cancels the running scenario (and the self-test), clears the overrides and puts the mod back. */
	public void abort() {
		abort("abort requested", true);
	}

	/** The player toggled the mod (keybind or GUI) while a test ran. */
	public void onUserToggle() {
		abort("the mod was toggled", false);
	}

	/** The player died while a test ran. */
	public void onDeath() {
		abort("the player died", false);
	}

	/** A real disconnect happened. Ends the scenario as expected if it asked for one, otherwise aborts it. */
	public void onRealDisconnect() {
		if (current == null) {
			if (suite != null) abort("a real disconnect happened", false);
			return;
		}
		Consumer<TestContext> handler = ctx.disconnectHandler();
		if (handler == null) {
			abort("a real disconnect happened", false);
			return;
		}
		try {
			handler.accept(ctx);
			finish(Verdict.INFO, "Disconnected", false);
		} catch (ScenarioFinish f) {
			finish(f.verdict(), f.reason(), false);
		} catch (RuntimeException e) {
			finish(Verdict.FAIL, "Error: " + e, false);
		}
	}

	/** Every state-machine transition (the scenario's assertions read them). */
	public void onTransition(State from, State to, Reason reason) {
		if (ctx != null) ctx.recordTransition(from, to, reason, clock.getAsLong());
	}

	/** Every client tick: advances the running scenario or the self-test. */
	public void tick() {
		if (current == null) {
			if (suite != null) suiteTick();
			return;
		}
		try {
			for (int guard = 0; guard < MAX_STEPS_PER_TICK && current != null; guard++) {
				if (stepIndex >= steps.size()) {
					finish(Verdict.FAIL, "The scenario ended without a result", true);
					return;
				}
				Step step = steps.get(stepIndex);
				if (!stepStarted) {
					stepStarted = true;
					step.start(ctx);
				}
				if (!step.tick(ctx)) return;
				stepIndex++;
				stepStarted = false;
			}
		} catch (ScenarioFinish f) {
			finish(f.verdict(), f.reason(), true);
		} catch (RuntimeException e) {
			finish(Verdict.FAIL, "Error: " + e, true);
		}
	}

	/** Every frame (for sampling a turn at frame rate). */
	public void onFrame() {
		if (current == null) return;
		Consumer<TestContext> listener = ctx.frameListener();
		if (listener == null) return;
		try {
			listener.accept(ctx);
		} catch (ScenarioFinish f) {
			finish(f.verdict(), f.reason(), true);
		} catch (RuntimeException e) {
			finish(Verdict.FAIL, "Error: " + e, true);
		}
	}

	// ---- internals ----

	private void start(Scenario scenario, TestOptions options) {
		AfkStateMachine machine = env.machine();
		wasActive = machine.state() == State.ACTIVE;
		snapshot = machine.captureForTest();
		overrides.clear();
		current = scenario;
		ctx = new TestContext(env, options, overrides, clock, this::getResults);
		stepIndex = 0;
		stepStarted = false;
		startedAt = clock.getAsLong();
		env.setTestMode(true, scenario.label());
		env.setDisconnectDryRun(true);
		env.log("Started " + scenario.label() + (options.confirmed ? " (confirmed)" : ""));
		try {
			steps = scenario.script(options).steps();
		} catch (RuntimeException e) {
			steps = List.of();
			finish(Verdict.FAIL, "Error building the scenario: " + e, true);
		}
	}

	private void abort(String why, boolean restore) {
		if (suite != null) suite.aborted = true;
		if (current != null) {
			env.log("Aborting " + current.label() + ": " + why);
			finish(Verdict.INFO, "Aborted: " + why, restore);
		} else if (suite != null) {
			finishSuite();
		}
	}

	private void finish(Verdict verdict, String reason, boolean restore) {
		Scenario scenario = current;
		if (scenario == null) return;
		double seconds = (clock.getAsLong() - startedAt) / (double) TimeUnit.SECONDS.toNanos(1);
		cleanup(restore);
		current = null;
		ctx = null;
		steps = null;
		TestResult result = new TestResult(scenario.id(), scenario.name(), verdict, reason, wallClockMs.getAsLong(), seconds);
		record(result);
		if (suite != null) {
			suite.results.add(result);
			suite.gapUntil = clock.getAsLong() + secondsToNanos(SUITE_GAP_SECONDS);
			if (suite.aborted) finishSuite();
		}
	}

	/** Undoes the scenario's effects. Never throws. */
	private void cleanup(boolean restore) {
		if (ctx != null) ctx.clearHandlers();
		overrides.clear();
		try {
			env.cleanupAfterTest();
		} catch (RuntimeException e) {
			env.log("Cleanup problem: " + e);
		}
		AfkStateMachine machine = env.machine();
		try {
			if (restore && wasActive && env.inWorld()) {
				if (machine.state() != State.ACTIVE) {
					env.log("Putting the mod back to ACTIVE (it was " + machine.state() + ")");
					machine.turnOff();
					machine.turnOn();
				}
				machine.restoreAfterTest(snapshot);
				env.resetRecovery();
				env.requestCheck();
			}
		} catch (RuntimeException e) {
			env.log("Could not restore the mod's state: " + e);
		}
		env.setTestMode(false, null);
		if (!machine.isOn() && env.stats().hasSession()) {
			// The test turned the mod off and it couldn't be put back: close the real session honestly.
			env.stats().endSession(EndReason.TEST_LAB);
		}
	}

	private void record(TestResult result) {
		results.add(result);
		env.stats().event(EventType.TEST_RESULT, result.line(), true);
		env.log("Result: " + result.line());
		try {
			resultListener.accept(result);
		} catch (RuntimeException ignored) {
			// A listener problem must never break the runner.
		}
	}

	private void suiteTick() {
		if (clock.getAsLong() < suite.gapUntil) return;
		if (suite.next >= suite.queue.size()) {
			finishSuite();
			return;
		}
		Scenario scenario = suite.queue.get(suite.next++);
		TestOptions options = scenario.autoOptions();
		String why = refusal(scenario, options);
		if (why != null) {
			TestResult skipped = new TestResult(scenario.id(), scenario.name(), Verdict.INFO, "Skipped: " + why,
					wallClockMs.getAsLong(), 0);
			record(skipped);
			suite.results.add(skipped);
			return;
		}
		start(scenario, options);
	}

	private void finishSuite() {
		Suite s = suite;
		suite = null;
		double seconds = (clock.getAsLong() - s.startedAt) / (double) TimeUnit.SECONDS.toNanos(1);
		String note = s.aborted ? "Aborted after " + s.results.size() + " of " + s.queue.size() + " scenarios."
				: s.modWasOn ? null : "The mod was OFF: scenarios that need it were skipped. Turn it ON and run again.";
		String report = SelfTestReport.format(s.results, s.startedEpochMs, seconds, s.queue.size(), note);
		lastSelfTestReport = report;
		String where;
		try {
			where = env.writeConfigFile(SELF_TEST_FILE, report);
		} catch (IOException | RuntimeException e) {
			where = null;
			env.log("Could not save " + SELF_TEST_FILE + ": " + e);
		}
		overrides.clear();
		env.stats().clearTestData();
		String summary = SelfTestReport.summary(s.results) + (s.aborted ? " (aborted)" : "")
				+ (where != null ? " - saved to " + where : "");
		env.log("Self-test finished: " + summary);
		env.chat("Self-test finished: " + summary);
	}

	private static long secondsToNanos(double seconds) {
		return Math.round(seconds * TimeUnit.SECONDS.toNanos(1));
	}
}
