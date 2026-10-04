package dev.afkmod.testlab;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a PASS means for each Test Lab scenario, and the warning for scenarios that move the player or can disconnect.
 * The scenario's own description says what it does; this says how to read the verdict. The GUI shows both in the info
 * tooltip. {@code ScenarioInfoTest} fails if a scenario has no entry here. No Minecraft imports.
 */
public final class ScenarioInfo {
	/** The self-test (H1) is a screen button, not a scenario class, so it has its own id. */
	public static final String SELF_TEST_ID = "H1";
	public static final String SELF_TEST_DESCRIPTION =
			"Runs every scenario marked auto in sequence: messages, restart flow, key control, yaw table, edge check, "
					+ "short timer, timer pause, stats and the HUD preview. Scenarios that need the mod ON are skipped "
					+ "(INFO) when it is OFF. Nothing moves you and nothing disconnects. Saves a report to "
					+ TestLab.SELF_TEST_FILE + ".";
	public static final String SELF_TEST_PASS =
			"Every scenario that ran passed. Skipped scenarios are reported as INFO and do not fail the run.";

	private record Info(String pass, String warning, String tag) {
	}

	private static final Map<String, Info> INFO = new LinkedHashMap<>();

	private ScenarioInfo() {
	}

	/** What a PASS means for the scenario, or null if unknown. */
	public static String passMeaning(String id) {
		Info info = INFO.get(id);
		return info == null ? null : info.pass();
	}

	/** A warning to show before running (null if the scenario is harmless). */
	public static String warning(String id) {
		Info info = INFO.get(id);
		return info == null ? null : info.warning();
	}

	/** A very short warning label for the scenario list (e.g. "moves you"), or null if the scenario is harmless. */
	public static String tag(String id) {
		Info info = INFO.get(id);
		return info == null ? null : info.tag();
	}

	public static boolean has(String id) {
		return INFO.containsKey(id);
	}

	private static void add(String id, String pass) {
		INFO.put(id, new Info(pass, null, null));
	}

	private static void add(String id, String pass, String warning, String tag) {
		INFO.put(id, new Info(pass, warning, tag));
	}

	static {
		add("A1", "Explain: the text was classified (the verdict is INFO, nothing is triggered). Send for real: the real "
				+ "handler did exactly what the explanation predicted.");
		add("A2", "All four example messages were classified as expected with your current keywords.");

		add("B1", "The mod went ACTIVE, RESTARTING, SETTLING, ACTIVE in order, left and right click were released, the timer "
				+ "paused, the restart was counted once, and crouch, left and right click came back on.");
		add("B2", "With no reconnect the mod still resumed on its own once the no-reconnect fallback time had passed.");
		add("B3", "Seeing only the queue text was enough to start a restart, and the mod resumed afterwards.");
		add("B4", "The mod stayed in RESTARTING after the reconnect until the queue text had been gone for the full queue-gone "
				+ "time.");
		add("B5", "Repeated restart messages and queue text were counted as one restart.");
		add("B6", "A short gap in the queue text did not end the restart; a gap longer than the queue-gone time did.");
		add("B7", "A restart message and queue text during the post-resume cooldown were ignored.");
		add("B8", "Messages containing a whispered word were ignored and did not start a restart.");
		add("B9", "After the maximum wait the mod turned OFF, released everything and did not disconnect.");
		add("B10", "A manual disconnect turned the mod OFF at once, and the real disconnect-handling code classified it as "
				+ "manual.");
		add("B11", "A server reconnect while mining kept the mod ON when you rejoined in time, and turned it OFF when the "
				+ "grace period ran out.");

		add("C1", "Each key was switched off, the mod turned it back on within 2 checks, and it pressed at most once per "
				+ "check (no toggle-off by accident).");
		add("C2", "The mod pressed nothing while a screen was open, and put crouch, left and right click back afterwards.");

		add("D1", "Every value in the edge table (and your yaw) snapped to the correct exact direction.");
		add("D2", "The edge check produced a report and a decision (INFO). Nothing moved.");
		add("D3", "The pitch never changed, the yaw moved smoothly to an exact north/east/south/west in the right time, the "
				+ "walk began only after the turn, and the keys were restored.",
				"Moves you: this turns the camera, jumps and walks forward a few blocks. Stand somewhere safe.", "moves you");
		add("D4", "Standing still made the real stuck detector fire in time, and the recovery that followed ran correctly.",
				"Moves you: when the detector fires the mod turns the camera, jumps and walks. Stand somewhere safe and "
						+ "do not move for about 5 seconds.", "moves you");
		add("D5", "Every attempt failed, the number of attempts matched 1 + retries, and the final logout ran (as a logged "
				+ "WOULD DISCONNECT unless you allowed a real disconnect).",
				"Can disconnect you: with Real disconnect on, you really leave to the server list at the end.",
				"can disconnect");
		add("D6", "The turn finished exactly on a cardinal direction in the right time, with no pitch change, and nothing "
				+ "else (no jump, no walk) happened.",
				"Turns the camera: your view rotates to the nearest north, east, south or west.", "turns camera");

		add("E1", "The timer ran out through the real timer-end path: keys released, crouch off, mod OFF, and the final "
				+ "disconnect handled as chosen.",
				"Full mode really disconnects you to the server list at the end (you are asked to confirm).",
				"full: can disconnect");
		add("E2", "The timer stood still during the restart (within 1 second of the restart length) and counted down again "
				+ "afterwards.");

		add("F1", "A fake session appeared in the test data while the real history and lifetime totals did not change.");
		add("F2", "A session saved to a temporary file and loaded back identical.");
		add("F3", "A corrupt file did not crash anything: it was moved aside as .corrupt, stats started fresh, and saving "
				+ "still worked.");
		add("F4", "All test data was removed and the real stats were untouched.");

		add("G1", "Every HUD variant was shown without changing the real mod state (look at the HUD: it shows [PREVIEW]).");
		add("I1", "The report was copied to the clipboard and saved to " + TestLab.REPORT_FILE + ".");
	}
}
