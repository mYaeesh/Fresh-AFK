package dev.afkmod.testlab;

import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.logic.KeywordMatcher;
import dev.afkmod.logic.MessageSource;
import dev.afkmod.testlab.MessageExplainer.Effect;
import dev.afkmod.testlab.MessageExplainer.Explanation;

import java.util.List;

/** A. Messages. */
public final class MessageScenarios {
	/** A one-click example for the message detector, with what the default keywords make of it. */
	public record Preset(String label, String text, MessageSource source, KeywordMatcher.Result expected) {
	}

	public static final List<Preset> PRESETS = List.of(
			new Preset("Servers updating", "Servers are updating", MessageSource.SYSTEM, KeywordMatcher.Result.RESTART),
			new Preset("Whispered", "whispered servers", MessageSource.SYSTEM, KeywordMatcher.Result.IGNORED),
			new Preset("Queue #3", "restart queue #3", MessageSource.ACTION_BAR, KeywordMatcher.Result.RESTART),
			new Preset("Unrelated", "Welcome to the mining farm!", MessageSource.SYSTEM, KeywordMatcher.Result.NONE));

	private MessageScenarios() {
	}

	static Explanation explain(TestContext ctx, String text, MessageSource source) {
		return MessageExplainer.explain(text, source, ctx.config(), ctx.state(), ctx.machine().isInPostResumeCooldown());
	}

	/** A1: Explain (no trigger, works with the mod OFF) or Send for real (mod ON, the real handler). */
	public static final class A1 extends Scenario {
		public A1() {
			super("A1", Group.A, "Message detector",
					"Explain: shows the stripped text, the matched ignore/restart/queue keyword, whether the cooldown would "
							+ "suppress it, the state and what would happen, without triggering anything (works with the mod OFF). "
							+ "Send for real (mod ON): pushes it through the real handler and compares with the explanation.",
					AUTO);
		}

		@Override
		public boolean requiresModOn(TestOptions options) {
			return options.sendForReal;
		}

		@Override
		public String precondition(TestEnvironment env, TestOptions options) {
			return options.text == null || options.text.isBlank() ? "Type a message for A1 first." : null;
		}

		@Override
		public TestOptions autoOptions() {
			return TestOptions.defaults().text("[TEST] Servers are updating").messageType(MessageSource.SYSTEM);
		}

		@Override
		public Script script(TestOptions o) {
			if (!o.sendForReal) {
				return Script.of(ctx -> ctx.info(explain(ctx, o.text, o.messageType).summary()));
			}
			Explanation[] predicted = new Explanation[1];
			int[] mark = new int[1];
			KeywordMatcher.Result[] result = new KeywordMatcher.Result[1];
			State[] after = new State[1];
			return Script.builder()
					.run(ctx -> {
						predicted[0] = explain(ctx, o.text, o.messageType);
						mark[0] = ctx.mark();
						result[0] = ctx.env().injectMessage(o.messageType, o.text);
						after[0] = ctx.state();
					})
					// Watch what follows for a moment (the mod is put back to ACTIVE afterwards).
					.waitSeconds(3)
					.run(ctx -> {
						Explanation e = predicted[0];
						boolean expectRestart = e.effect() == Effect.STARTS_RESTART;
						boolean restarted = after[0] == State.RESTARTING;
						String text = "Sent as " + o.messageType + ": result " + result[0] + ", state after: " + after[0]
								+ ". Predicted: " + e.outcome() + ". Seen in 3 s: " + ctx.describe(mark[0]) + ".";
						ctx.check(result[0] == e.result(), "Mismatch: the handler returned " + result[0]
								+ " but Explain said " + e.result() + ". " + text);
						ctx.check(expectRestart == restarted, "Mismatch with Explain. " + text);
						ctx.pass(text);
					})
					.build();
		}
	}

	/** A2: the preset examples, each explained and checked against what the default keywords make of it. */
	public static final class A2 extends Scenario {
		public A2() {
			super("A2", Group.A, "Preset examples",
					"Explains \"Servers are updating\", \"whispered servers\", \"restart queue #3\" (action bar) and an "
							+ "unrelated text with your current keywords, and checks each classification (nothing is triggered).",
					AUTO);
		}

		@Override
		public Script script(TestOptions o) {
			return Script.of(ctx -> {
				StringBuilder sb = new StringBuilder();
				int failures = 0;
				for (Preset p : PRESETS) {
					Explanation e = explain(ctx, p.text(), p.source());
					boolean ok = e.result() == p.expected();
					if (!ok) failures++;
					sb.append(ok ? "OK " : "WRONG ").append('"').append(p.text()).append("\" (").append(p.source())
							.append("): ").append(e.result()).append(ok ? "" : " (expected " + p.expected() + ")")
							.append(" - would ").append(e.outcome()).append('\n');
				}
				String text = sb.toString().strip();
				if (failures > 0) ctx.fail(failures + " preset(s) classified differently (check your keywords):\n" + text);
				ctx.pass(text);
			});
		}
	}
}
