package dev.afkmod.testlab;

import java.util.List;

/** Every Test Lab scenario, in the order the screen and the self-test use. */
public final class Scenarios {
	private Scenarios() {
	}

	public static List<Scenario> all() {
		return List.of(
				new MessageScenarios.A1(),
				new MessageScenarios.A2(),
				new RestartScenarios.B1(),
				new RestartScenarios.B2(),
				new RestartScenarios.B3(),
				new RestartScenarios.B4(),
				new RestartScenarios.B5(),
				new RestartScenarios.B6(),
				new RestartScenarios.B7(),
				new RestartScenarios.B8(),
				new RestartScenarios.B9(),
				new RestartScenarios.B10(),
				new RestartScenarios.B11(),
				new KeyScenarios.C1(),
				new KeyScenarios.C2(),
				new MovementScenarios.D1(),
				new MovementScenarios.D2(),
				new MovementScenarios.D3(),
				new MovementScenarios.D4(),
				new MovementScenarios.D5(),
				new MovementScenarios.D6(),
				new TimerScenarios.E1(),
				new TimerScenarios.E2(),
				new StatsScenarios.F1(),
				new StatsScenarios.F2(),
				new StatsScenarios.F3(),
				new StatsScenarios.F4(),
				new OtherScenarios.G1(),
				new OtherScenarios.I1());
	}
}
