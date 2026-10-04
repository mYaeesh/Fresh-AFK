package dev.afkmod.testlab;

import java.io.IOException;

/** G. HUD preview and I. Debug report. */
public final class OtherScenarios {
	private OtherScenarios() {
	}

	/** G1: cycles the HUD through its states without touching the real mod state. */
	public static final class G1 extends Scenario {
		static final double SECONDS_PER_FRAME = 4;

		public G1() {
			super("G1", Group.G, "HUD preview",
					"Shows the HUD as mining, restarting, recovering, paused, no timer and timer running, about 4 s each, in "
							+ "compact and then detailed mode, WITHOUT changing the real mod state. The HUD shows [PREVIEW].",
					WORLD | IN_GAME | AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			Script.Builder b = Script.builder();
			Object[] stateAtStart = new Object[1];
			b.run(ctx -> stateAtStart[0] = ctx.state());
			for (boolean detailed : new boolean[]{false, true}) {
				for (HudPreviewFrames.Frame frame : HudPreviewFrames.frames()) {
					b.run(ctx -> {
						ctx.env().setHudPreview(frame.snapshot(), detailed);
						ctx.log("HUD preview: " + frame.label() + (detailed ? " (detailed)" : " (compact)"));
					});
					b.waitSeconds(SECONDS_PER_FRAME);
				}
			}
			b.run(ctx -> {
				ctx.env().setHudPreview(null, false);
				int frames = HudPreviewFrames.frames().size();
				ctx.info("Showed " + frames + " HUD states in compact and detailed mode (" + 2 * frames + " frames, "
						+ (int) SECONDS_PER_FRAME + " s each). The real state was " + stateAtStart[0] + " before and " + ctx.state() + " after.");
			});
			return b.build();
		}
	}

	/** I1: the debug report, copied to the clipboard and saved. */
	public static final class I1 extends Scenario {
		public I1() {
			super("I1", Group.I, "Debug report",
					"Builds a text report (versions, Java/OS, state, effective config and overrides, keybinds, last 100 events, "
							+ "last test results, last 50 debug log lines), copies it to the clipboard and saves "
							+ TestLab.REPORT_FILE + ". Server address and player name are redacted unless reportRedactIdentity is false.",
					0);
		}

		@Override
		public Script script(TestOptions o) {
			return Script.of(ctx -> {
				DebugReport.Input input = ctx.env().reportInput(ctx.results(), ctx.overrides().describe());
				String report = DebugReport.build(input);
				ctx.env().copyToClipboard(report);
				String where;
				try {
					where = ctx.env().writeConfigFile(TestLab.REPORT_FILE, report);
				} catch (IOException e) {
					ctx.info("Copied the report to the clipboard, but saving " + TestLab.REPORT_FILE + " failed: " + e);
					return;
				}
				ctx.info("Copied the debug report to the clipboard and saved it to " + where + " ("
						+ report.lines().count() + " lines, identity " + (input.redact() ? "redacted" : "NOT redacted") + ").");
			});
		}
	}
}
