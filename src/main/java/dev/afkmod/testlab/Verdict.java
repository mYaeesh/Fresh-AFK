package dev.afkmod.testlab;

/** How a Test Lab scenario ended. An aborted scenario ends as {@link #INFO} with an "Aborted: ..." reason. */
public enum Verdict {
	/** Everything the scenario checks behaved as specified. */
	PASS,
	/** At least one check failed; the reason says which, with the measured values. */
	FAIL,
	/** A report without a pass/fail judgement (explain, edge check, preview, debug report, skipped or aborted runs). */
	INFO
}
