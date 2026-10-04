package dev.afkmod.testlab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

/**
 * Judges a sampled recovery turn (D3, D6) against the Movement Recovery rules: the pitch never changes, the yaw moves
 * monotonically toward the target without overshoot, the speed ramps up and then down (ease-in-out), the turn takes
 * the configured time (within a tolerance), and it ends exactly on the target, a multiple of 90.
 */
public final class RotationAnalyzer {
	/** One sample, taken every frame (and every tick) during the turn. */
	public record Sample(long nanos, float yaw, float pitch) {
	}

	/**
	 * @param measuredSeconds from the rotation start to the first sample exactly on the target (NaN if never reached)
	 * @param peakSpeed       degrees per second
	 * @param easeJudged      false when there were too few samples (or no turn) to judge the speed curve
	 */
	public record Analysis(int samples, double arcDegrees, boolean pitchConstant, float pitchMin, float pitchMax,
			boolean monotonic, boolean noOvershoot, boolean easeJudged, boolean easeInOut, double peakSpeed,
			double peakAtFraction, double measuredSeconds, double expectedSeconds, boolean durationOk, float finalYaw,
			boolean finalExact, boolean finalCardinal, List<String> failures) {

		public boolean passed() {
			return failures.isEmpty();
		}

		public String summary() {
			return String.format(Locale.ROOT,
					"turn %+.1f deg in %s (expected %.2f s), peak %.0f deg/s at %.0f%%, pitch %s, final yaw %s%s",
					arcDegrees, Double.isNaN(measuredSeconds) ? "never reached the target" : String.format(Locale.ROOT, "%.2f s", measuredSeconds),
					expectedSeconds, peakSpeed, peakAtFraction * 100,
					pitchConstant ? "unchanged" : String.format(Locale.ROOT, "CHANGED (%.3f..%.3f)", pitchMin, pitchMax),
					YawTable.num(finalYaw), ", " + samples + " samples");
		}

		public String failureText() {
			StringJoiner joiner = new StringJoiner("; ");
			failures.forEach(joiner::add);
			return joiner.toString();
		}
	}

	public static final double DURATION_TOLERANCE_SECONDS = 0.15;
	private static final double YAW_EPSILON = 1e-3;

	private RotationAnalyzer() {
	}

	/**
	 * @param samples         in time order, taken from the rotation start (earlier ones are ignored)
	 * @param pitchBefore     the pitch just before the turn
	 * @param startYaw        the rotation's start yaw
	 * @param endYaw          the rotation's exact target
	 * @param startNanos      the rotation's start time
	 * @param expectedSeconds {@code recoveryTurnSeconds}
	 */
	public static Analysis analyze(List<Sample> samples, float pitchBefore, float startYaw, float endYaw, long startNanos,
	                               double expectedSeconds) {
		List<Sample> s = new ArrayList<>();
		for (Sample sample : samples) {
			if (sample.nanos() >= startNanos) s.add(sample);
		}
		List<String> failures = new ArrayList<>();
		double arc = (double) endYaw - startYaw;
		double sign = Math.signum(arc);

		float pitchMin = pitchBefore;
		float pitchMax = pitchBefore;
		boolean pitchConstant = true;
		for (Sample sample : s) {
			if (Float.compare(sample.pitch(), pitchBefore) != 0) pitchConstant = false;
			pitchMin = Math.min(pitchMin, sample.pitch());
			pitchMax = Math.max(pitchMax, sample.pitch());
		}
		if (!pitchConstant) failures.add(String.format(Locale.ROOT, "the pitch changed (%.3f..%.3f, was %.3f)", pitchMin, pitchMax, pitchBefore));

		boolean monotonic = true;
		boolean noOvershoot = true;
		double lo = Math.min(startYaw, endYaw) - YAW_EPSILON;
		double hi = Math.max(startYaw, endYaw) + YAW_EPSILON;
		float previous = startYaw;
		for (Sample sample : s) {
			if ((sample.yaw() - (double) previous) * sign < -YAW_EPSILON) monotonic = false;
			if (sample.yaw() < lo || sample.yaw() > hi) noOvershoot = false;
			previous = sample.yaw();
		}
		if (!monotonic) failures.add("the yaw moved backwards during the turn");
		if (!noOvershoot) failures.add("the yaw overshot the start..target range");

		// Duration: from the start to the first sample exactly on the target.
		double measured = Double.NaN;
		int finishIndex = -1;
		for (int i = 0; i < s.size(); i++) {
			if (Float.compare(s.get(i).yaw(), endYaw) == 0) {
				measured = seconds(s.get(i).nanos() - startNanos);
				finishIndex = i;
				break;
			}
		}
		// Already exactly on a cardinal yaw: there is nothing to turn, so there is no duration to judge.
		boolean noTurn = Math.abs(arc) < 1e-6;
		boolean durationOk = !Double.isNaN(measured)
				&& (noTurn || Math.abs(measured - expectedSeconds) <= DURATION_TOLERANCE_SECONDS);
		if (Double.isNaN(measured)) failures.add("the yaw never reached the exact target " + YawTable.num(endYaw));
		else if (!durationOk) {
			failures.add(String.format(Locale.ROOT, "expected the turn to take %.2f s (+-%.2f), got %.2f s",
					expectedSeconds, DURATION_TOLERANCE_SECONDS, measured));
		}

		float finalYaw = s.isEmpty() ? startYaw : s.getLast().yaw();
		boolean finalExact = Float.compare(finalYaw, endYaw) == 0;
		boolean finalCardinal = endYaw % 90f == 0f;
		if (!finalExact) failures.add("the final yaw " + YawTable.num(finalYaw) + " is not exactly the target " + YawTable.num(endYaw));
		if (!finalCardinal) failures.add("the target " + YawTable.num(endYaw) + " is not a multiple of 90");

		// Speed curve: intervals between consecutive samples, from the start point up to the finish sample.
		List<double[]> speeds = new ArrayList<>(); // {midpoint fraction of the duration, deg/s}
		double durationNanos = expectedSeconds * TimeUnit.SECONDS.toNanos(1);
		long prevNanos = startNanos;
		float prevYaw = startYaw;
		int last = finishIndex >= 0 ? finishIndex : s.size() - 1;
		for (int i = 0; i <= last; i++) {
			Sample sample = s.get(i);
			long dt = sample.nanos() - prevNanos;
			if (dt <= 0) continue;
			double speed = Math.abs(sample.yaw() - (double) prevYaw) / seconds(dt);
			double mid = durationNanos > 0 ? ((prevNanos + sample.nanos()) / 2.0 - startNanos) / durationNanos : 0;
			speeds.add(new double[]{mid, speed});
			prevNanos = sample.nanos();
			prevYaw = sample.yaw();
		}
		double peak = 0;
		double peakAt = 0;
		for (double[] sp : speeds) {
			if (sp[1] > peak) {
				peak = sp[1];
				peakAt = sp[0];
			}
		}
		boolean easeJudged = expectedSeconds > 0 && Math.abs(arc) > 0.5 && speeds.size() >= 4;
		boolean easeInOut = true;
		if (easeJudged) {
			double early = averageSpeed(speeds, 0.0, 0.2);
			double late = averageSpeed(speeds, 0.8, 1.0);
			boolean peakInMiddle = peakAt >= 0.2 && peakAt <= 0.8;
			boolean slowEnds = (Double.isNaN(early) || early < 0.6 * peak) && (Double.isNaN(late) || late < 0.6 * peak);
			easeInOut = peakInMiddle && slowEnds;
			if (!easeInOut) {
				failures.add(String.format(Locale.ROOT,
						"the speed doesn't ease in and out (peak %.0f deg/s at %.0f%%, start %.0f, end %.0f)",
						peak, peakAt * 100, early, late));
			}
		}
		return new Analysis(s.size(), arc, pitchConstant, pitchMin, pitchMax, monotonic, noOvershoot, easeJudged, easeInOut,
				peak, peakAt, measured, expectedSeconds, durationOk, finalYaw, finalExact, finalCardinal, List.copyOf(failures));
	}

	/** Average speed of the intervals whose midpoint lies in [from, to) of the duration; NaN if none. */
	private static double averageSpeed(List<double[]> speeds, double from, double to) {
		double sum = 0;
		int n = 0;
		for (double[] sp : speeds) {
			if (sp[0] >= from && sp[0] < to) {
				sum += sp[1];
				n++;
			}
		}
		return n == 0 ? Double.NaN : sum / n;
	}

	private static double seconds(long nanos) {
		return nanos / (double) TimeUnit.SECONDS.toNanos(1);
	}
}
