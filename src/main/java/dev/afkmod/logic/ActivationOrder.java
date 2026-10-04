package dev.afkmod.logic;

/**
 * The order the mod turns crouch, left click and right click on in: crouch and left click first, right click last and
 * only once crouch is confirmed (the player is really sneaking). If right click is on while crouch is fully off, right
 * click is released first and pressed again after crouch, so it is never used without shift held.
 *
 * <p>Each key is pressed at most once per check. When right click has to wait, {@link Plan#followUp()} asks for
 * another check on the next tick instead of a full interval later.
 */
public final class ActivationOrder {
	private ActivationOrder() {
	}

	/**
	 * @param releaseUse release right click before anything else
	 * @param pressCrouch press crouch (first press of the check)
	 * @param pressAttack press left click (after crouch)
	 * @param pressUse press right click (last)
	 * @param followUp right click still has to be turned on: check again on the next tick
	 */
	public record Plan(boolean releaseUse, boolean pressCrouch, boolean pressAttack, boolean pressUse, boolean followUp) {
	}

	/**
	 * @param crouchOn the crouch key (or the player's sneak input) is on
	 * @param crouchConfirmed the player is really sneaking, so right click may be pressed
	 * @param attackOn left click is on
	 * @param useOn right click is on
	 */
	public static Plan plan(boolean crouchOn, boolean crouchConfirmed, boolean attackOn, boolean useOn) {
		boolean releaseUse = useOn && !crouchOn;
		boolean useWanted = !useOn || releaseUse;
		boolean pressUse = useWanted && !releaseUse && crouchOn && crouchConfirmed;
		return new Plan(releaseUse, !crouchOn, !attackOn, pressUse, useWanted && !pressUse);
	}
}
