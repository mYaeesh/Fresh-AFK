package dev.afkmod.testlab;

import dev.afkmod.config.AfkConfig;
import dev.afkmod.logic.AfkStateMachine;
import dev.afkmod.logic.AfkStateMachine.Reason;
import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.testlab.TestEnvironment.Key;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** What a running scenario sees: the environment, its options, the clock, the transitions since it began, and helpers. */
public final class TestContext {
	/** A state-machine transition seen while the scenario ran. */
	public record Transition(State from, State to, Reason reason, long nanos) {
		@Override
		public String toString() {
			return from + " -> " + to + " (" + reason + ")";
		}
	}

	private final TestEnvironment env;
	private final TestOptions options;
	private final ConfigOverrides overrides;
	private final LongSupplier clock;
	private final Supplier<List<TestResult>> results;
	private final long startedAt;
	private final List<Transition> transitions = new ArrayList<>();
	private Consumer<TestContext> frameListener;
	private Consumer<TestContext> disconnectHandler;

	TestContext(TestEnvironment env, TestOptions options, ConfigOverrides overrides, LongSupplier clock,
	            Supplier<List<TestResult>> results) {
		this.env = Objects.requireNonNull(env);
		this.options = Objects.requireNonNull(options);
		this.overrides = Objects.requireNonNull(overrides);
		this.clock = Objects.requireNonNull(clock);
		this.results = Objects.requireNonNull(results);
		this.startedAt = clock.getAsLong();
	}

	// ---- basics ----

	public TestEnvironment env() {
		return env;
	}

	public TestOptions options() {
		return options;
	}

	public ConfigOverrides overrides() {
		return overrides;
	}

	public AfkStateMachine machine() {
		return env.machine();
	}

	public State state() {
		return env.machine().state();
	}

	public AfkConfig config() {
		return env.config();
	}

	/** The periodic check interval in seconds. */
	public double interval() {
		return env.checkIntervalSeconds();
	}

	public long now() {
		return clock.getAsLong();
	}

	public double secondsSince(long nanos) {
		return (clock.getAsLong() - nanos) / (double) TimeUnit.SECONDS.toNanos(1);
	}

	public static double seconds(long nanos) {
		return nanos / (double) TimeUnit.SECONDS.toNanos(1);
	}

	/** Seconds since the scenario started. */
	public double elapsed() {
		return secondsSince(startedAt);
	}

	/** Results recorded so far (oldest first). */
	public List<TestResult> results() {
		return results.get();
	}

	public void log(String line) {
		env.log(line);
	}

	// ---- ending the scenario ----

	public void pass(String reason) {
		throw new ScenarioFinish(Verdict.PASS, reason);
	}

	public void fail(String reason) {
		throw new ScenarioFinish(Verdict.FAIL, reason);
	}

	public void info(String reason) {
		throw new ScenarioFinish(Verdict.INFO, reason);
	}

	/** FAILs the scenario with {@code reason} unless {@code condition} holds. */
	public void check(boolean condition, String reason) {
		if (!condition) fail(reason);
	}

	// ---- transitions ----

	void recordTransition(State from, State to, Reason reason, long nanos) {
		transitions.add(new Transition(from, to, reason, nanos));
	}

	/** A position in the transition list; pass it to {@link #since} later. */
	public int mark() {
		return transitions.size();
	}

	public List<Transition> since(int mark) {
		return List.copyOf(transitions.subList(Math.min(mark, transitions.size()), transitions.size()));
	}

	/** The first transition to {@code to} since {@code mark}, or null. */
	public Transition firstTo(State to, int mark) {
		for (Transition t : since(mark)) {
			if (t.to() == to) return t;
		}
		return null;
	}

	public long count(int mark, Reason reason) {
		return since(mark).stream().filter(t -> t.reason() == reason).count();
	}

	public String describe(int mark) {
		List<Transition> list = since(mark);
		if (list.isEmpty()) return "no transitions";
		StringJoiner joiner = new StringJoiner(", ");
		for (Transition t : list) joiner.add(t.toString());
		return joiner.toString();
	}

	/** FAILs unless the states entered since {@code mark} are exactly {@code expected}, in order. */
	public void expectStates(int mark, State... expected) {
		List<State> actual = since(mark).stream().map(Transition::to).toList();
		check(actual.equals(Arrays.asList(expected)),
				"Expected the transitions " + Arrays.toString(expected) + " but saw: " + describe(mark));
	}

	// ---- keys ----

	public boolean leftRightReleased() {
		return !env.keyActive(Key.ATTACK) && !env.keyActive(Key.USE);
	}

	public boolean allKeysActive() {
		return env.keyActive(Key.CROUCH) && env.keyActive(Key.ATTACK) && env.keyActive(Key.USE);
	}

	public boolean noKeysActive() {
		return !env.keyActive(Key.CROUCH) && !env.keyActive(Key.ATTACK) && !env.keyActive(Key.USE);
	}

	/** E.g. {@code C on, L off, R off}. */
	public String keys() {
		return "C " + onOff(env.keyActive(Key.CROUCH)) + ", L " + onOff(env.keyActive(Key.ATTACK)) + ", R "
				+ onOff(env.keyActive(Key.USE));
	}

	private static String onOff(boolean on) {
		return on ? "on" : "off";
	}

	// ---- helpers for scenarios ----

	/**
	 * Starts a test countdown of {@code seconds} through the real timer code. The value lives in the override layer (the
	 * saved timer is never touched) and the real countdown is put back when the scenario ends.
	 */
	public void startTestTimer(int seconds) {
		overrides.set(ConfigOverrides.Key.TIMER_SECONDS, seconds);
		machine().setTimerSeconds(seconds);
	}

	/** This scenario's final disconnect is real (only after the caller confirmed it). */
	public void allowRealDisconnect() {
		env.setDisconnectDryRun(false);
	}

	/** Called every frame while set (e.g. to sample the yaw during a turn). */
	public void onFrame(Consumer<TestContext> listener) {
		this.frameListener = listener;
	}

	/**
	 * The scenario expects a real disconnect: when it happens, {@code handler} judges it (it should pass or fail)
	 * instead of the disconnect aborting the scenario.
	 */
	public void expectRealDisconnect(Consumer<TestContext> handler) {
		this.disconnectHandler = handler;
	}

	Consumer<TestContext> frameListener() {
		return frameListener;
	}

	Consumer<TestContext> disconnectHandler() {
		return disconnectHandler;
	}

	void clearHandlers() {
		frameListener = null;
		disconnectHandler = null;
	}

	/** {@code 3.1 s}. */
	public static String s(double seconds) {
		return String.format(Locale.ROOT, "%.1f s", seconds);
	}
}
