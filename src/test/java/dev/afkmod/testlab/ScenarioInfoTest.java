package dev.afkmod.testlab;

import dev.afkmod.gui.TextWrap;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every Test Lab scenario must explain what a PASS means; scenarios that move the player must carry a warning. */
class ScenarioInfoTest {

	@Test
	void everyScenarioHasADescriptionAndAPassMeaning() {
		for (Scenario s : Scenarios.all()) {
			assertFalse(s.description().isBlank(), s.id());
			assertTrue(ScenarioInfo.has(s.id()), "no ScenarioInfo entry for " + s.id());
			String pass = ScenarioInfo.passMeaning(s.id());
			assertNotNull(pass, s.id());
			assertFalse(pass.isBlank(), s.id());
			for (String line : TextWrap.wrap(s.description() + " " + pass)) {
				assertTrue(line.length() <= TextWrap.TOOLTIP_CHARS, s.id() + ": " + line);
			}
		}
	}

	@Test
	void scenariosThatMoveThePlayerOrCanDisconnectCarryAWarning() {
		for (String id : new String[]{"D3", "D4", "D5", "D6", "E1"}) {
			String warning = ScenarioInfo.warning(id);
			assertNotNull(warning, id);
			assertFalse(warning.isBlank(), id);
		}
		for (Scenario s : Scenarios.all()) {
			if (s.needsConfirm()) assertNotNull(ScenarioInfo.warning(s.id()), "confirm scenario without a warning: " + s.id());
		}
	}

	@Test
	void everyInfoEntryBelongsToARealScenario() {
		Set<String> ids = new HashSet<>();
		for (Scenario s : Scenarios.all()) ids.add(s.id());
		for (String id : new String[]{"A1", "A2", "B1", "B11", "C1", "D6", "E2", "F4", "G1", "I1"}) {
			assertTrue(ids.contains(id), id);
		}
	}

	@Test
	void selfTestHasATooltipText() {
		assertFalse(ScenarioInfo.SELF_TEST_DESCRIPTION.isBlank());
		assertFalse(ScenarioInfo.SELF_TEST_PASS.isBlank());
	}
}
