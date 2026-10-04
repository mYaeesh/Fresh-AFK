package dev.afkmod.testlab;

import dev.afkmod.config.AfkConfig;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * The Test Lab's in-memory override layer. Scenarios shorten timings here; {@link #effective(AfkConfig)} returns the
 * saved config itself while nothing is overridden, and otherwise a separate copy with the overrides applied. The saved
 * config object is never written to, so nothing a test overrides can reach {@code config/afkmod.json}.
 *
 * <p>The copy is made once per change of the overrides (or of the saved config object) and then reused, so a value the
 * mod writes into the effective config during a test (e.g. the timer a scenario starts) stays for the rest of the test
 * and disappears with the copy when the overrides are cleared.
 */
public final class ConfigOverrides {
	public enum Key {
		QUEUE_GONE_SECONDS("queueGoneSeconds"),
		NO_RECONNECT_FALLBACK_SECONDS("noReconnectFallbackSeconds"),
		/** {@code maxRestartWaitMinutes}, but in seconds so a test can use a few seconds. */
		MAX_RESTART_WAIT_SECONDS("maxRestartWait (s)"),
		STUCK_WINDOW_SECONDS("stuckWindowSeconds"),
		SETTLE_DELAY_SECONDS("settleDelaySeconds"),
		RECONNECT_GRACE_SECONDS("reconnectGraceSeconds"),
		POST_RESUME_COOLDOWN_SECONDS("postResumeCooldownSeconds"),
		TIMER_SECONDS("timerSeconds"),
		/** 1 = on, 0 = off. */
		MOVEMENT_CHECK_ENABLED("movementCheckEnabled");

		private final String label;

		Key(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	private final Map<Key, Double> values = new EnumMap<>(Key.class);
	private int version;
	private int cachedVersion = -1;
	private AfkConfig cachedBase;
	private AfkConfig cachedEffective;

	public void set(Key key, double value) {
		if (!Double.isFinite(value)) throw new IllegalArgumentException("Not a finite value: " + value);
		values.put(Objects.requireNonNull(key), value);
		version++;
	}

	public void set(Key key, boolean value) {
		set(key, value ? 1 : 0);
	}

	/** Removes every override; the effective config is the saved one again. */
	public void clear() {
		if (values.isEmpty()) return;
		values.clear();
		version++;
		cachedEffective = null;
		cachedBase = null;
	}

	public boolean isActive() {
		return !values.isEmpty();
	}

	/** A copy of the current overrides. */
	public Map<Key, Double> active() {
		return values.isEmpty() ? Map.of() : new EnumMap<>(values);
	}

	/** E.g. {@code queueGoneSeconds=1, noReconnectFallbackSeconds=5}, or {@code none}. */
	public String describe() {
		if (values.isEmpty()) return "none";
		StringJoiner joiner = new StringJoiner(", ");
		values.forEach((key, value) -> joiner.add(key.label() + "=" + format(value)));
		return joiner.toString();
	}

	/**
	 * The config the mod should use right now: {@code base} itself when nothing is overridden, otherwise a copy of
	 * {@code base} with the overrides applied. {@code base} is never modified.
	 */
	public AfkConfig effective(AfkConfig base) {
		if (values.isEmpty()) return base;
		if (cachedEffective == null || cachedBase != base || cachedVersion != version) {
			AfkConfig copy = new AfkConfig();
			copy.copyFrom(base);
			values.forEach((key, value) -> apply(copy, key, value));
			cachedEffective = copy;
			cachedBase = base;
			cachedVersion = version;
		}
		return cachedEffective;
	}

	private static void apply(AfkConfig c, Key key, double v) {
		int seconds = (int) Math.max(0, Math.round(v));
		switch (key) {
			case QUEUE_GONE_SECONDS -> c.queueGoneSeconds = seconds;
			case NO_RECONNECT_FALLBACK_SECONDS -> c.noReconnectFallbackSeconds = seconds;
			case MAX_RESTART_WAIT_SECONDS -> c.maxRestartWaitSecondsOverride = Math.max(1, seconds);
			case STUCK_WINDOW_SECONDS -> c.stuckWindowSeconds = Math.max(1, seconds);
			case SETTLE_DELAY_SECONDS -> c.settleDelaySeconds = seconds;
			case RECONNECT_GRACE_SECONDS -> c.reconnectGraceSeconds = seconds;
			case POST_RESUME_COOLDOWN_SECONDS -> c.postResumeCooldownSeconds = seconds;
			case TIMER_SECONDS -> c.timerSeconds = seconds;
			case MOVEMENT_CHECK_ENABLED -> c.movementCheckEnabled = v != 0;
		}
	}

	private static String format(double v) {
		return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.2f", v);
	}
}
