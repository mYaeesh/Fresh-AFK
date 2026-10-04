package dev.afkmod.testlab;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/** The self-test (H1) report saved to {@code config/afkmod-selftest.txt}. */
public final class SelfTestReport {
	private SelfTestReport() {
	}

	/** E.g. {@code 27 PASS, 1 FAIL, 4 INFO}. */
	public static String summary(List<TestResult> results) {
		long pass = results.stream().filter(r -> r.verdict() == Verdict.PASS).count();
		long fail = results.stream().filter(r -> r.verdict() == Verdict.FAIL).count();
		long info = results.stream().filter(r -> r.verdict() == Verdict.INFO).count();
		return pass + " PASS, " + fail + " FAIL, " + info + " INFO";
	}

	/**
	 * @param planned how many scenarios the self-test meant to run
	 * @param note    an extra line (aborted, mod was OFF...), or null
	 */
	public static String format(List<TestResult> results, long startedEpochMs, double seconds, int planned, String note) {
		StringBuilder sb = new StringBuilder();
		sb.append("Fresh AFK self-test\n");
		sb.append("Started: ").append(DebugReport.time(startedEpochMs, ZoneId.systemDefault())).append('\n');
		sb.append(String.format(Locale.ROOT, "Duration: %.1f s%n", seconds));
		sb.append("Ran: ").append(results.size()).append(" of ").append(planned).append(" scenarios\n");
		sb.append("Summary: ").append(summary(results)).append('\n');
		if (note != null) sb.append(note).append('\n');
		sb.append('\n');
		for (TestResult r : results) sb.append(r.line()).append('\n');
		long failed = results.stream().filter(TestResult::failed).count();
		sb.append('\n').append(failed == 0 ? "OVERALL: PASS" : "OVERALL: FAIL (" + failed + " failed)").append('\n');
		return sb.toString();
	}
}
