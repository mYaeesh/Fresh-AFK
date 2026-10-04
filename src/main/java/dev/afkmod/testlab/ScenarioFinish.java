package dev.afkmod.testlab;

/** Thrown by {@link TestContext#pass}, {@link TestContext#fail} and {@link TestContext#info} to end the scenario. */
public final class ScenarioFinish extends RuntimeException {
	private final Verdict verdict;

	public ScenarioFinish(Verdict verdict, String reason) {
		super(reason, null, false, false);
		this.verdict = verdict;
	}

	public Verdict verdict() {
		return verdict;
	}

	public String reason() {
		return getMessage() == null ? "" : getMessage();
	}
}
