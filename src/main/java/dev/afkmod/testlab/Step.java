package dev.afkmod.testlab;

/**
 * One step of a scenario script. The runner calls {@link #start} once, then {@link #tick} every client tick until it
 * returns true; several instant steps can complete within one tick. A step ends the whole scenario by calling
 * {@link TestContext#pass}, {@link TestContext#fail} or {@link TestContext#info}.
 */
public interface Step {
	default void start(TestContext ctx) {
	}

	/** Returns true when the step is done. */
	boolean tick(TestContext ctx);
}
