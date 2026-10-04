package dev.afkmod.testlab;

import java.util.Locale;
import java.util.Objects;

/**
 * The result of one scenario run: PASS / FAIL / INFO plus a short reason with the measured values.
 *
 * @param epochMs         when the scenario finished
 * @param durationSeconds how long it ran
 */
public record TestResult(String scenarioId, String scenarioName, Verdict verdict, String reason, long epochMs,
		double durationSeconds) {
	public TestResult {
		Objects.requireNonNull(scenarioId);
		Objects.requireNonNull(scenarioName);
		Objects.requireNonNull(verdict);
		reason = reason == null ? "" : reason;
	}

	/** One line for the results list, the logs and the reports, e.g. {@code B1 Full flow with reconnect: PASS (24.1 s) - ...}. */
	public String line() {
		return String.format(Locale.ROOT, "%s %s: %s (%.1f s) - %s", scenarioId, scenarioName, verdict, durationSeconds,
				reason.replace("\r", "").replace("\n", " | "));
	}

	public boolean passed() {
		return verdict == Verdict.PASS;
	}

	public boolean failed() {
		return verdict == Verdict.FAIL;
	}
}
