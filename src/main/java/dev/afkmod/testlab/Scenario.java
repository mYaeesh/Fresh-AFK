package dev.afkmod.testlab;

import java.util.Objects;

/**
 * One Test Lab scenario. Subclasses describe themselves through the constructor and build a fresh {@link Script} for
 * every run. A scenario only talks to the game through {@link TestEnvironment}, which feeds the mod's real handlers.
 */
public abstract class Scenario {
	/** Needs the mod ON and mining (ACTIVE). Implies {@link #WORLD} and {@link #IN_GAME}. */
	public static final int MOD_ON = 1;
	/** Moves the player or can disconnect: the caller must pass {@code confirmed}. */
	public static final int CONFIRM = 1 << 1;
	/** Part of the self-test (no player movement, no disconnects). */
	public static final int AUTO = 1 << 2;
	/** Needs a loaded world (but not necessarily the mod ON). */
	public static final int WORLD = 1 << 3;
	/** Runs in the game with no screen open (the Test Lab screen closes first). */
	public static final int IN_GAME = 1 << 4;

	public enum Group {
		A("A. Messages"),
		B("B. Restart flow"),
		C("C. Key control"),
		D("D. Movement recovery"),
		E("E. Timer"),
		F("F. Stats"),
		G("G. HUD preview"),
		I("I. Debug report");

		private final String title;

		Group(String title) {
			this.title = title;
		}

		public String title() {
			return title;
		}
	}

	private final String id;
	private final Group group;
	private final String name;
	private final String description;
	private final boolean requiresModOn;
	private final boolean needsConfirm;
	private final boolean safeForAuto;
	private final boolean requiresWorld;
	private final boolean runsInGame;

	protected Scenario(String id, Group group, String name, String description, int flags) {
		this.id = Objects.requireNonNull(id);
		this.group = Objects.requireNonNull(group);
		this.name = Objects.requireNonNull(name);
		this.description = Objects.requireNonNull(description);
		this.requiresModOn = (flags & MOD_ON) != 0;
		this.needsConfirm = (flags & CONFIRM) != 0;
		this.safeForAuto = (flags & AUTO) != 0;
		this.requiresWorld = requiresModOn || (flags & WORLD) != 0;
		this.runsInGame = requiresModOn || (flags & IN_GAME) != 0;
	}

	public String id() {
		return id;
	}

	public Group group() {
		return group;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	/** E.g. {@code B1 Full flow with reconnect}. */
	public String label() {
		return id + " " + name;
	}

	public boolean requiresModOn() {
		return requiresModOn;
	}

	public boolean needsConfirm() {
		return needsConfirm;
	}

	public boolean safeForAuto() {
		return safeForAuto;
	}

	public boolean requiresWorld() {
		return requiresWorld;
	}

	/** Whether this run needs the mod ON (some scenarios only need it in one mode). */
	public boolean requiresModOn(TestOptions options) {
		return requiresModOn;
	}

	/** Whether this run needs confirmation (some scenarios only need it in one mode). */
	public boolean needsConfirm(TestOptions options) {
		return needsConfirm;
	}

	public boolean requiresWorld(TestOptions options) {
		return requiresWorld || requiresModOn(options);
	}

	/** Whether this run plays out in the game (so the Test Lab screen must close first). */
	public boolean runsInGame(TestOptions options) {
		return runsInGame || requiresModOn(options);
	}

	/** An extra reason to refuse this run, or null. */
	public String precondition(TestEnvironment env, TestOptions options) {
		return null;
	}

	/** The options the self-test uses for this scenario. */
	public TestOptions autoOptions() {
		return TestOptions.defaults();
	}

	/** Builds the steps for one run. */
	public abstract Script script(TestOptions options);

	@Override
	public String toString() {
		return label();
	}
}
