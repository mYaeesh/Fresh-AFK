package dev.afkmod.testlab;

import dev.afkmod.config.AfkConfig;
import dev.afkmod.logic.AfkStateMachine.State;
import dev.afkmod.logic.KeywordMatcher;
import dev.afkmod.logic.MessageSource;

import java.util.Objects;

/**
 * The Test Lab's "Explain" mode: evaluates a message WITHOUT triggering anything and says what the mod would do. It
 * uses the same {@link KeywordMatcher} calls and the same rules as {@code AfkStateMachine.onMessage} (ignore rule
 * first, then restart keywords; an action bar message matching the queue keywords is the restart queue text; player
 * chat is never used for detection); a unit test checks that its verdicts agree with the real state machine.
 */
public final class MessageExplainer {
	/** What the mod would do with a message. */
	public enum Effect {
		/** Player chat, never used for detection. */
		LOGGED_ONLY,
		/** The ignore rule matched. */
		IGNORED,
		/** No restart keyword (and no queue text). */
		NO_MATCH,
		/** A restart match, but the mod is OFF. */
		MOD_OFF,
		/** A restart match, suppressed by the post-resume cooldown. */
		COOLDOWN,
		/** Would start a restart (ACTIVE/RECOVERING -> RESTARTING, counted). */
		STARTS_RESTART,
		/** Already RESTARTING: refreshes "last seen", not counted. */
		REFRESHES_RESTART,
		/** SETTLING -> RESTARTING, the same restart, not counted. */
		BACK_TO_RESTARTING,
		/** A restart match while RECONNECTING: no effect. */
		NO_EFFECT_RECONNECTING
	}

	/**
	 * @param strippedText     the text with formatting codes removed
	 * @param ignoreMatch      the ignore keyword that matched, or null
	 * @param restartMatch     the restart keyword that matched, or null
	 * @param queueMatch       the queue keyword that matched (action bar only), or null
	 * @param queueText        it is the restart queue text (action bar, queue keyword, no ignore keyword)
	 * @param result           the classification the real handler returns
	 * @param cooldownSuppress the post-resume cooldown would suppress it
	 */
	public record Explanation(String text, MessageSource source, String strippedText, String ignoreMatch,
			String restartMatch, String queueMatch, boolean queueText, KeywordMatcher.Result result,
			boolean cooldownSuppress, State state, Effect effect, String outcome) {

		/** Several short lines for the results list and the log. */
		public String summary() {
			return "Text (stripped): \"" + strippedText + "\" as " + source
					+ "\nIgnore keyword: " + quoteOrNone(ignoreMatch)
					+ "\nRestart keyword: " + quoteOrNone(restartMatch)
					+ (source == MessageSource.ACTION_BAR ? "\nQueue keyword: " + quoteOrNone(queueMatch)
					+ (queueText ? " (this is the restart queue text)" : "") : "")
					+ "\nCooldown would suppress it: " + (cooldownSuppress ? "yes" : "no")
					+ "\nState: " + state
					+ "\nWould: " + outcome;
		}

		private static String quoteOrNone(String s) {
			return s == null ? "none" : "\"" + s + "\"";
		}
	}

	private MessageExplainer() {
	}

	/**
	 * @param cooldownActive {@code AfkStateMachine.isInPostResumeCooldown()} right now
	 */
	public static Explanation explain(String text, MessageSource source, AfkConfig config, State state, boolean cooldownActive) {
		Objects.requireNonNull(config);
		Objects.requireNonNull(state);
		String raw = text == null ? "" : text;
		MessageSource src = source == null ? MessageSource.SYSTEM : source;
		String stripped = KeywordMatcher.stripFormatting(raw);

		if (src == MessageSource.CHAT) {
			return new Explanation(raw, src, stripped, null, null, null, false, KeywordMatcher.Result.NONE, false, state,
					Effect.LOGGED_ONLY, "nothing: player chat is only logged, never used for detection");
		}

		String ignore = KeywordMatcher.firstMatch(raw, config.ignoreKeywords);
		String restart = KeywordMatcher.firstMatch(raw, config.restartKeywords);
		boolean overlay = src == MessageSource.ACTION_BAR;
		String queue = overlay ? KeywordMatcher.firstMatch(raw, config.queueKeywords) : null;
		// Same calls as AfkStateMachine.onMessage / isQueueText.
		KeywordMatcher.Result result = KeywordMatcher.classify(raw, config.restartKeywords, config.ignoreKeywords, null);
		boolean queueText = overlay
				&& KeywordMatcher.classify(raw, config.queueKeywords, config.ignoreKeywords, null) == KeywordMatcher.Result.RESTART;
		if (queueText) result = KeywordMatcher.Result.RESTART;

		Effect effect;
		String outcome;
		boolean suppressed = false;
		if (result == KeywordMatcher.Result.IGNORED) {
			effect = Effect.IGNORED;
			outcome = "ignore it: it contains the ignore keyword \"" + ignore + "\" (the ignore rule wins over everything)";
		} else if (result != KeywordMatcher.Result.RESTART) {
			effect = Effect.NO_MATCH;
			outcome = "nothing: no restart keyword" + (overlay ? " and no queue keyword" : "");
		} else {
			String what = queueText && restart == null ? "the restart queue text" : "a restart message";
			switch (state) {
				case OFF -> {
					effect = Effect.MOD_OFF;
					outcome = "only log it: it is " + what + ", but the mod is OFF";
				}
				case ACTIVE, RECOVERING -> {
					if (cooldownActive) {
						effect = Effect.COOLDOWN;
						suppressed = true;
						outcome = "ignore it: " + what + " during the post-resume cooldown";
					} else {
						effect = Effect.STARTS_RESTART;
						outcome = "start a restart: " + state + " -> RESTARTING, release left and right click, restart count +1"
								+ (state == State.RECOVERING ? ", cancel the recovery" : "");
					}
				}
				case RESTARTING -> {
					effect = Effect.REFRESHES_RESTART;
					outcome = "refresh the current restart (not counted again)" + (queueText ? "; the queue text counts as on screen" : "");
				}
				case SETTLING -> {
					effect = Effect.BACK_TO_RESTARTING;
					outcome = "go back to RESTARTING (the same restart, not counted again)";
				}
				default -> {
					effect = Effect.NO_EFFECT_RECONNECTING;
					outcome = "nothing while RECONNECTING (only the rejoin or the grace period matter)";
				}
			}
		}
		return new Explanation(raw, src, stripped, ignore, restart, queue, queueText, result, suppressed, state, effect, outcome);
	}
}
