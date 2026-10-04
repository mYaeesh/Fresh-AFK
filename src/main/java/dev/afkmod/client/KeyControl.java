package dev.afkmod.client;

import dev.afkmod.testlab.PressCounter;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.player.LocalPlayer;

/**
 * Presses and releases crouch, attack and use like a single key press would, for both "Toggle" and "Hold"
 * modes (Options > Controls).
 *
 * <p>In 26.1.2 all three are {@link ToggleKeyMapping}s. In toggle mode {@code setDown(true)} flips the state
 * and {@code setDown(false)} does nothing; in hold mode {@code setDown} sets the state directly. Either way
 * {@link KeyMapping#isDown()} is the effective state, so each helper looks first and presses at most once:
 * it can never toggle a key back off by pressing twice.
 */
public final class KeyControl {
	public enum Action {
		CROUCH,
		ATTACK,
		USE
	}

	/** Counts every press these helpers send (the Test Lab's key-check instrumentation). Action order = counter index. */
	private static final PressCounter PRESSES = new PressCounter();

	private KeyControl() {
	}

	public static PressCounter presses() {
		return PRESSES;
	}

	public static KeyMapping mapping(Options options, Action action) {
		return switch (action) {
			case CROUCH -> options.keyShift;
			case ATTACK -> options.keyAttack;
			case USE -> options.keyUse;
		};
	}

	/** True if the key is in toggle mode (the player's setting). */
	public static boolean isToggleMode(Options options, Action action) {
		return switch (action) {
			case CROUCH -> options.toggleCrouch().get();
			case ATTACK -> options.toggleAttack().get();
			case USE -> options.toggleUse().get();
		};
	}

	/**
	 * The real current state. Crouch also counts the player's actual sneak input, which follows the key one
	 * tick later; attack and use are the key mapping's state (which is the toggled state in toggle mode).
	 */
	public static boolean isActive(Minecraft mc, Action action) {
		if (mapping(mc.options, action).isDown()) return true;
		LocalPlayer player = mc.player;
		return action == Action.CROUCH && player != null && player.isShiftKeyDown();
	}

	/** Makes the action active with at most one simulated press. Returns true if it pressed. */
	public static boolean ensureActive(Minecraft mc, Action action) {
		if (isActive(mc, action)) return false;
		// Toggle mode: one press flips off -> on. Hold mode: holds the key down.
		mapping(mc.options, action).setDown(true);
		PRESSES.record(action.ordinal());
		return true;
	}

	/** Makes the action inactive with at most one simulated press/release. Returns true if it changed anything. */
	public static boolean ensureInactive(Minecraft mc, Action action) {
		KeyMapping key = mapping(mc.options, action);
		forgetScreenRestore(mc, key);
		if (!key.isDown()) return false;
		if (isToggleMode(mc.options, action)) {
			key.setDown(true); // one press flips on -> off; setDown(false) is ignored in toggle mode
		} else {
			key.setDown(false);
		}
		PRESSES.record(action.ordinal());
		return true;
	}

	/**
	 * Opening a screen releases toggled keys but remembers them, and closing the screen turns them back on
	 * ({@code KeyMapping.restoreToggleStatesOnScreenClosed}). When the mod releases a key while a screen is
	 * open, that memory must be cleared or the key comes back on by itself. The public
	 * {@code shouldRestoreStateOnScreenClosed()} reads and clears it.
	 */
	private static void forgetScreenRestore(Minecraft mc, KeyMapping key) {
		if (mc.screen != null && key instanceof ToggleKeyMapping toggle) {
			toggle.shouldRestoreStateOnScreenClosed();
		}
	}
}
