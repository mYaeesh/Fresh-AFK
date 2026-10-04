package dev.afkmod.logic;

import dev.afkmod.logic.ActivationOrder.Plan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivationOrderTest {

	@Test
	void fromAllOffPressesCrouchAndLeftClickButRightClickWaits() {
		Plan plan = ActivationOrder.plan(false, false, false, false);
		assertEquals(new Plan(false, true, true, false, true), plan);
	}

	@Test
	void rightClickGoesOnOnceCrouchIsConfirmed() {
		Plan plan = ActivationOrder.plan(true, true, true, false);
		assertEquals(new Plan(false, false, false, true, false), plan);
	}

	@Test
	void crouchKeyDownButNotYetSneakingKeepsRightClickWaiting() {
		Plan plan = ActivationOrder.plan(true, false, true, false);
		assertFalse(plan.pressUse());
		assertTrue(plan.followUp());
		assertFalse(plan.releaseUse());
	}

	@Test
	void rightClickOnWithoutCrouchIsReleasedAndRedoneAfterCrouch() {
		Plan first = ActivationOrder.plan(false, false, true, true);
		assertEquals(new Plan(true, true, false, false, true), first);
		// Next tick: crouch is on and confirmed, right click is off again.
		Plan second = ActivationOrder.plan(true, true, true, false);
		assertTrue(second.pressUse());
		assertFalse(second.followUp());
	}

	@Test
	void rightClickOnWhileCrouchKeyIsOnIsLeftAlone() {
		// Crouch is on but the sneak input lags a tick: no need to release right click.
		Plan plan = ActivationOrder.plan(true, false, true, true);
		assertEquals(new Plan(false, false, false, false, false), plan);
	}

	@Test
	void allOnDoesNothing() {
		assertEquals(new Plan(false, false, false, false, false), ActivationOrder.plan(true, true, true, true));
	}

	@Test
	void rightClickIsNeverPressedInTheSameCheckAsCrouch() {
		for (int bits = 0; bits < 16; bits++) {
			boolean crouchOn = (bits & 1) != 0, confirmed = (bits & 2) != 0, attackOn = (bits & 4) != 0, useOn = (bits & 8) != 0;
			Plan plan = ActivationOrder.plan(crouchOn, confirmed, attackOn, useOn);
			assertFalse(plan.pressCrouch() && plan.pressUse(), "pressed crouch and right click together for " + bits);
			assertFalse(plan.releaseUse() && plan.pressUse(), "released and pressed right click in one check for " + bits);
			if (plan.pressUse()) assertTrue(crouchOn && confirmed, "right click without confirmed crouch for " + bits);
		}
	}
}
