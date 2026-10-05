package dev.afkmod.logic;

import dev.afkmod.config.AfkConfig;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The mod's core state, free of Minecraft types so it can be unit tested. The Minecraft layer feeds it
 * events (messages, join, disconnect, death, toggles) and calls {@link #tick} once per check interval;
 * it reacts to transitions through a {@link Listener} (pressing/releasing keys, disconnecting on timer end).
 *
 * <p>This object lives for the whole game session, so it survives the server-initiated reconnect during a restart.
 *
 * <p>States:
 * <ul>
 *   <li>{@link State#OFF}: the mod does nothing.</li>
 *   <li>{@link State#ACTIVE}: keep crouch, left and right click active; the timer runs.</li>
 *   <li>{@link State#RESTARTING}: a restart message was seen; keys released. Ends only when the "restart queue"
 *       action bar text has been gone for {@code queueGoneSeconds} AND either the player rejoined (the server's
 *       reconnect) or, with no disconnect at all, {@code noReconnectFallbackSeconds} passed. Turns the mod off
 *       after {@code maxRestartWaitMinutes}.</li>
 *   <li>{@link State#SETTLING}: the restart is over; waiting {@code settleDelaySeconds} after the world is ready
 *       before going back to ACTIVE.</li>
 *   <li>{@link State#RECONNECTING}: the connection dropped while ACTIVE with no restart message; waiting up to
 *       {@code reconnectGraceSeconds} for a rejoin (a relog) before turning off.</li>
 *   <li>{@link State#RECOVERING}: a sub-state of ACTIVE. The player stopped moving, so the Minecraft layer runs a
 *       movement recovery attempt. Everything ACTIVE reacts to (restart, join, disconnect, death, timer end)
 *       still applies and cancels the recovery.</li>
 * </ul>
 * The timer only counts down in ACTIVE and RECOVERING; it is paused in every other state. Turning the mod OFF keeps the
 * remaining time (it resumes on the next turn on) unless the timer itself ended; {@link #resetTimer} starts it over.
 */
public final class AfkStateMachine {
	public enum State {
		OFF,
		ACTIVE,
		RESTARTING,
		SETTLING,
		RECONNECTING,
		/** Movement recovery in progress (a sub-state of ACTIVE). */
		RECOVERING
	}

	public enum Reason {
		TOGGLED_ON,
		TOGGLED_OFF,
		RESTART_DETECTED,
		/** The queue text showed up again while settling, so the restart isn't over. */
		QUEUE_TEXT_SHOWN,
		REJOINED,
		/** Queue text gone and no reconnect came: resumed after {@code noReconnectFallbackSeconds}. */
		NO_RECONNECT_FALLBACK,
		/** The restart wasn't over after {@code maxRestartWaitMinutes}. */
		RESTART_TIMEOUT,
		SETTLED,
		CONNECTION_LOST,
		GRACE_EXPIRED,
		MANUAL_DISCONNECT,
		DEATH,
		TIMER_END,
		/** The player hasn't moved for a full window: a recovery attempt starts. */
		STUCK_DETECTED,
		/** The recovery walk succeeded. */
		RECOVERED,
		/** The recovery attempt failed, retries are left: back to ACTIVE until the next stuck window. */
		RECOVERY_FAILED,
		/** The recovery was stopped (e.g. a screen opened); not counted as an attempt. */
		RECOVERY_CANCELLED,
		/** Every recovery attempt failed: the mod turns off and disconnects to the server list. */
		RECOVERY_GAVE_UP
	}

	public enum DisconnectCause {
		/** The player left (pause menu "Disconnect" and similar). Turns the mod off immediately. */
		CLIENT_INITIATED,
		/** The mod itself disconnected (timer end). Turns the mod off immediately. */
		MOD_INITIATED,
		/** The server dropped, kicked or reconnected (transferred) the player. May be the restart reconnect, so the mod waits. */
		UNEXPECTED
	}

	@FunctionalInterface
	public interface Listener {
		void onTransition(State from, State to, Reason reason);
	}

	private final Supplier<AfkConfig> config;
	private final LongSupplier nanoClock;
	private final PausableTimer timer;
	private Listener listener = (from, to, reason) -> { };
	private LongConsumer timerListener = seconds -> { };

	private State state = State.OFF;
	private Reason lastReason;
	private int restartCount;

	// The current restart, kept from its first message until back in ACTIVE (also across SETTLING -> RESTARTING).
	private boolean inRestart;
	private long restartStartedAt;
	/** Last time any restart signal was seen (a restart message or the queue text). */
	private long lastRestartSignalAt;
	/** Last time the queue text was known to be on screen (or the restart start if it never was). */
	private long queueLastSeenAt;
	/** The connection dropped during the restart and no rejoin has come yet. */
	private boolean awaitingRejoin;
	/** The player rejoined (the server's reconnect) since the restart began. */
	private boolean rejoined;
	private boolean settleStarted;
	private long settleStartedAt;
	private long disconnectedAt;
	private boolean cooldownActive;
	private long resumedAt;

	public AfkStateMachine(Supplier<AfkConfig> config, LongSupplier nanoClock) {
		this.config = Objects.requireNonNull(config);
		this.nanoClock = Objects.requireNonNull(nanoClock);
		this.timer = new PausableTimer(nanoClock);
	}

	public void setListener(Listener listener) {
		this.listener = Objects.requireNonNull(listener);
	}

	// ---- commands ----

	public void toggle() {
		if (state == State.OFF) turnOn();
		else turnOff();
	}

	public void turnOn() {
		if (state != State.OFF) return;
		cooldownActive = false;
		// A timer left paused by an earlier stop carries on from where it was; otherwise start a fresh one.
		long configured = config.get().timerSeconds;
		boolean keep = timer.hasTimer() && !timer.isExpired() && timer.durationSeconds() == configured;
		if (!keep) timer.start(configured, true);
		transition(State.ACTIVE, Reason.TOGGLED_ON);
	}

	/** Restarts the countdown from the configured duration. Stays paused unless the mod is ACTIVE or RECOVERING. */
	public void resetTimer() {
		timer.start(config.get().timerSeconds, !isActiveOrRecovering());
	}

	public void turnOff() {
		if (state != State.OFF) transition(State.OFF, Reason.TOGGLED_OFF);
	}

	/** Sets a new timer (0 = none). While the mod is on, the countdown restarts from the new value. */
	public void setTimerSeconds(long seconds) {
		config.get().timerSeconds = Math.max(0, seconds);
		timer.start(config.get().timerSeconds, !isActiveOrRecovering());
		timerListener.accept(config.get().timerSeconds);
	}

	/** Called with the new value (0 = none) whenever {@link #setTimerSeconds} runs. */
	public void setTimerListener(LongConsumer timerListener) {
		this.timerListener = Objects.requireNonNull(timerListener);
	}

	/** ACTIVE -> RECOVERING: the player is stuck. Returns false (and does nothing) in any other state. */
	public boolean startRecovery() {
		if (state != State.ACTIVE) return false;
		transition(State.RECOVERING, Reason.STUCK_DETECTED);
		return true;
	}

	/**
	 * RECOVERING -> ACTIVE with {@link Reason#RECOVERED}, {@link Reason#RECOVERY_FAILED} or
	 * {@link Reason#RECOVERY_CANCELLED}. Does nothing outside RECOVERING (e.g. a restart already took over).
	 */
	public void endRecovery(Reason reason) {
		if (reason != Reason.RECOVERED && reason != Reason.RECOVERY_FAILED && reason != Reason.RECOVERY_CANCELLED) {
			throw new IllegalArgumentException("Not a recovery end reason: " + reason);
		}
		if (state == State.RECOVERING) transition(State.ACTIVE, reason);
	}

	/** Every recovery attempt failed: turn the mod off (the Minecraft layer then disconnects to the server list). */
	public void giveUpRecovery() {
		if (state == State.ACTIVE || state == State.RECOVERING) transition(State.OFF, Reason.RECOVERY_GAVE_UP);
	}

	// ---- events ----

	/** Handles one incoming chat/system, title, subtitle or boss bar message (not the action bar). */
	public KeywordMatcher.Result onMessage(String text) {
		return onMessage(text, false);
	}

	/**
	 * Handles one incoming message. {@code overlay} is true for action bar text; an action bar message matching
	 * {@code queueKeywords} is the "restart queue" text, which starts or refreshes a restart and marks the queue
	 * text as on screen right now.
	 */
	public KeywordMatcher.Result onMessage(String text, boolean overlay) {
		AfkConfig c = config.get();
		// Restart-end keywords are no longer used: this server sends no "all clear" message.
		KeywordMatcher.Result result = KeywordMatcher.classify(text, c.restartKeywords, c.ignoreKeywords, null);
		boolean queueText = overlay && isQueueText(text);
		if (queueText) result = KeywordMatcher.Result.RESTART;
		if (result != KeywordMatcher.Result.RESTART) return result;

		long now = nanoClock.getAsLong();
		switch (state) {
			// Restart handling takes priority over a movement recovery.
			case ACTIVE, RECOVERING -> {
				if (isInPostResumeCooldown()) return result;
				restartCount++;
				beginRestart(now);
				if (queueText) queueLastSeenAt = now;
				transition(State.RESTARTING, Reason.RESTART_DETECTED);
			}
			// Still the same restart: not counted again, just refreshed.
			case RESTARTING -> {
				lastRestartSignalAt = now;
				if (queueText) queueLastSeenAt = now;
			}
			case SETTLING -> {
				continueRestart(now);
				if (queueText) queueLastSeenAt = now;
				transition(State.RESTARTING, Reason.RESTART_DETECTED);
			}
			default -> { }
		}
		return result;
	}

	/** True if {@code text} is the restart queue text: it matches {@code queueKeywords} and no ignore keyword. */
	public boolean isQueueText(String text) {
		AfkConfig c = config.get();
		return KeywordMatcher.classify(text, c.queueKeywords, c.ignoreKeywords, null) == KeywordMatcher.Result.RESTART;
	}

	/** The player joined a world (Fabric {@code ClientPlayConnectionEvents.JOIN}); after a restart this is the relog. */
	public void onJoin() {
		switch (state) {
			// ACTIVE: a join with no preceding disconnect (e.g. a proxy relog). The player can only be in a
			// world while ACTIVE, so this is a relog without a message: pause the timer and settle first.
			case ACTIVE, RECOVERING, RECONNECTING -> enterSettling(Reason.REJOINED);
			// The server's reconnect. Resuming still waits for the queue text to be gone (checked in tick),
			// so this works whether the text disappeared before or after the reconnect.
			case RESTARTING -> {
				awaitingRejoin = false;
				rejoined = true;
			}
			case SETTLING -> settleStarted = false;
			default -> { }
		}
	}

	public void onDisconnect(DisconnectCause cause) {
		if (state == State.OFF) return;
		switch (cause) {
			case CLIENT_INITIATED -> transition(State.OFF, Reason.MANUAL_DISCONNECT);
			case MOD_INITIATED -> transition(State.OFF, Reason.TIMER_END);
			case UNEXPECTED -> {
				switch (state) {
					case ACTIVE, RECOVERING -> {
						disconnectedAt = nanoClock.getAsLong();
						transition(State.RECONNECTING, Reason.CONNECTION_LOST);
					}
					case RESTARTING -> {
						awaitingRejoin = true;
						rejoined = false;
					}
					case SETTLING -> {
						continueRestart(nanoClock.getAsLong());
						awaitingRejoin = true;
						rejoined = false;
						transition(State.RESTARTING, Reason.CONNECTION_LOST);
					}
					default -> { }
				}
			}
		}
	}

	public void onDeath() {
		if (state != State.OFF) transition(State.OFF, Reason.DEATH);
	}

	/**
	 * The periodic check (every {@code checkIntervalTicks}).
	 *
	 * @param worldReady       a world is loaded and the local player is valid
	 * @param queueTextVisible the restart queue text is on screen right now (read from the client's action bar
	 *                         state). Pass false when that state can't be read: the time of the last queue
	 *                         message is then the only signal, which is the fallback.
	 */
	public void tick(boolean worldReady, boolean queueTextVisible) {
		AfkConfig c = config.get();
		long now = nanoClock.getAsLong();
		if (queueTextVisible && inRestart) {
			queueLastSeenAt = now;
			lastRestartSignalAt = now;
		}
		switch (state) {
			case ACTIVE, RECOVERING -> {
				if (timer.isExpired()) transition(State.OFF, Reason.TIMER_END);
			}
			case RESTARTING -> {
				if (elapsed(now, restartStartedAt, c.maxRestartWaitSeconds())) {
					transition(State.OFF, Reason.RESTART_TIMEOUT);
					return;
				}
				// Disconnected: only the rejoin (or the max wait) can end this.
				if (awaitingRejoin || !worldReady) return;
				if (!elapsed(now, queueLastSeenAt, c.queueGoneSeconds)) return;
				Reason reason = rejoined ? Reason.REJOINED
						: elapsed(now, lastRestartSignalAt, Math.max(c.queueGoneSeconds, c.noReconnectFallbackSeconds))
						? Reason.NO_RECONNECT_FALLBACK : null;
				if (reason != null) {
					enterSettling(reason);
					// The world is ready right now, so the settle delay starts now.
					settleStarted = true;
					settleStartedAt = now;
				}
			}
			case SETTLING -> {
				if (queueTextVisible && inRestart) {
					// The queue text is back: the server is frozen again.
					transition(State.RESTARTING, Reason.QUEUE_TEXT_SHOWN);
					return;
				}
				if (!worldReady) {
					settleStarted = false;
					return;
				}
				if (!settleStarted) {
					settleStarted = true;
					settleStartedAt = now;
				}
				if (elapsed(now, settleStartedAt, c.settleDelaySeconds)) {
					inRestart = false;
					cooldownActive = true;
					resumedAt = now;
					transition(State.ACTIVE, Reason.SETTLED);
				}
			}
			case RECONNECTING -> {
				if (elapsed(now, disconnectedAt, c.reconnectGraceSeconds)) transition(State.OFF, Reason.GRACE_EXPIRED);
			}
			default -> { }
		}
	}

	// ---- queries ----

	public State state() {
		return state;
	}

	public boolean isOn() {
		return state != State.OFF;
	}

	/** ACTIVE or its RECOVERING sub-state. */
	public boolean isActiveOrRecovering() {
		return state == State.ACTIVE || state == State.RECOVERING;
	}

	/** Reason for the most recent transition, or null before the first one. */
	public Reason lastReason() {
		return lastReason;
	}

	/** Restarts detected this game session (repeats of the same restart are not counted). */
	public int restartCount() {
		return restartCount;
	}

	/** True from the first restart message until the mod is back in ACTIVE (also while settling after it). */
	public boolean isInRestart() {
		return inRestart;
	}

	/** Seconds since the current restart began (its first message), or 0 when no restart is in progress. */
	public long restartElapsedSeconds() {
		if (!inRestart) return 0;
		return Math.max(0, (nanoClock.getAsLong() - restartStartedAt) / 1_000_000_000L);
	}

	public boolean isAwaitingRejoin() {
		return state == State.RESTARTING && awaitingRejoin;
	}

	/** In RESTARTING: the server's reconnect has happened; now only waiting for the queue text to be gone. */
	public boolean hasRejoinedDuringRestart() {
		return state == State.RESTARTING && rejoined;
	}

	/** True for a while after resuming from a restart; restart messages are ignored during it. */
	public boolean isInPostResumeCooldown() {
		return isActiveOrRecovering() && cooldownActive
				&& !elapsed(nanoClock.getAsLong(), resumedAt, config.get().postResumeCooldownSeconds);
	}

	public boolean hasTimer() {
		return timer.hasTimer();
	}

	public boolean isTimerPaused() {
		return timer.isPaused();
	}

	public long timerRemainingSeconds() {
		return timer.remainingSeconds();
	}

	public long timerRemainingNanos() {
		return timer.remainingNanos();
	}

	// ---- Test Lab ----

	/** What a Test Lab scenario may change and must put back afterwards. */
	public record TestSnapshot(int restartCount, boolean cooldownActive, long resumedAt, boolean hasTimer,
			long timerRemainingNanos) {
	}

	/** Captures the restart count, the post-resume cooldown and the timer before a Test Lab scenario. */
	public TestSnapshot captureForTest() {
		return new TestSnapshot(restartCount, cooldownActive, resumedAt, timer.hasTimer(), timer.remainingNanos());
	}

	/**
	 * Puts back what {@link #captureForTest} saw: the restart count, the cooldown and the timer's remaining time. Call
	 * it in the state the mod should continue in (the timer runs only in ACTIVE/RECOVERING). Fires no transition.
	 */
	public void restoreAfterTest(TestSnapshot snapshot) {
		restartCount = snapshot.restartCount();
		cooldownActive = snapshot.cooldownActive() && isActiveOrRecovering();
		resumedAt = snapshot.resumedAt();
		if (state == State.OFF) return;
		boolean paused = !isActiveOrRecovering();
		if (snapshot.hasTimer()) timer.startNanos(Math.max(1, snapshot.timerRemainingNanos()), paused);
		else timer.start(0, paused);
	}

	// ---- internals ----

	/** A new restart: every "since" time starts now; the queue text counts as gone until it is seen. */
	private void beginRestart(long now) {
		inRestart = true;
		restartStartedAt = now;
		lastRestartSignalAt = now;
		queueLastSeenAt = now;
		awaitingRejoin = false;
		rejoined = false;
	}

	/** Back to RESTARTING from SETTLING: the same restart if one is in progress, otherwise a new one (not counted). */
	private void continueRestart(long now) {
		if (!inRestart) {
			beginRestart(now);
		} else {
			lastRestartSignalAt = now;
		}
	}

	private void enterSettling(Reason reason) {
		settleStarted = false;
		transition(State.SETTLING, reason);
	}

	private void transition(State to, Reason reason) {
		State from = state;
		state = to;
		lastReason = reason;
		boolean activeOrRecovering = to == State.ACTIVE || to == State.RECOVERING;
		if (activeOrRecovering) timer.resume();
		// Stopping the mod only pauses the timer (it resumes on the next turn on); a finished timer is discarded.
		else if (to == State.OFF && reason == Reason.TIMER_END) timer.stop();
		else timer.pause();
		if (!activeOrRecovering) cooldownActive = false;
		if (to == State.OFF) inRestart = false;
		listener.onTransition(from, to, reason);
	}

	private static boolean elapsed(long now, long since, long seconds) {
		return now - since >= TimeUnit.SECONDS.toNanos(seconds);
	}
}
