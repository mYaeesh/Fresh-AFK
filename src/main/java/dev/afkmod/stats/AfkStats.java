package dev.afkmod.stats;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Session statistics, lifetime totals, recent-session history and the event log. Free of Minecraft types so it can be
 * unit tested; the client code feeds it (see {@code StatsRecorder} and {@code MovementRecovery}). Client thread only.
 *
 * <p>Every update and event carries a {@code test} flag. Test-flagged updates never touch the real session, the
 * lifetime totals or the history: they go to a separate in-memory test-data structure (test counters and injected test
 * sessions) that {@link #clearTestData()} empties. Test data is never written to the stats file.
 *
 * <p>API for the GUI and the Test Lab: {@link #getCurrentSession()}, {@link #getLifetime()},
 * {@link #getRecentSessions()}, {@link #getRecentEvents(int)}, {@link #resetLifetime()},
 * {@link #injectTestSession(SessionRecord)}, {@link #clearTestData()} and {@link #setStoreFile(Path)}.
 */
public final class AfkStats {
	public static final int MAX_RECENT = 20;
	public static final int MAX_EVENTS = 100;
	public static final int MAX_TEST_SESSIONS = 50;
	/** How often the running session is saved. */
	public static final long AUTOSAVE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(3);

	private final LongSupplier nanoClock;
	private final LongSupplier wallClockMs;
	private final StatsStore store;

	private StatsData data = new StatsData();
	private StatsSession current;
	private long lastSaveNanos;

	private StatsSession testCounters;
	private final List<SessionRecord> testSessions = new ArrayList<>();

	private final Deque<StatsEvent> events = new ArrayDeque<>();
	private Consumer<StatsEvent> eventSink = e -> { };

	public AfkStats(Path file, LongSupplier nanoClock, LongSupplier wallClockMs) {
		this.store = new StatsStore(file);
		this.nanoClock = Objects.requireNonNull(nanoClock);
		this.wallClockMs = Objects.requireNonNull(wallClockMs);
		load();
	}

	public AfkStats(Path file) {
		this(file, System::nanoTime, System::currentTimeMillis);
	}

	// ---- store ----

	/** Points the store at another file and loads it (a missing or corrupt file means fresh stats). Tests use this. */
	public void setStoreFile(Path file) {
		store.setFile(file);
		load();
	}

	public Path storeFile() {
		return store.file();
	}

	/** Receives every event as it is recorded (the client wires this to the debug log). Exceptions are swallowed. */
	public void setEventSink(Consumer<StatsEvent> sink) {
		this.eventSink = Objects.requireNonNull(sink);
	}

	private void load() {
		data = store.load();
		SessionRecord leftover = data.inProgress;
		if (leftover != null) {
			// The game stopped or crashed with a session open: keep it as a finished INTERRUPTED session.
			data.inProgress = null;
			leftover.endReason = EndReason.INTERRUPTED;
			leftover.endEpochMs = leftover.startEpochMs + leftover.wallMs;
			leftover.endedInLogout = false;
			archive(leftover);
			store.save(data);
		}
	}

	/** Writes the stats file now, including the running session (if any). Never throws. */
	public boolean saveNow() {
		data.inProgress = current == null ? null : current.snapshot(nanoClock.getAsLong(), wallClockMs.getAsLong(), null);
		lastSaveNanos = nanoClock.getAsLong();
		return store.save(data);
	}

	/** Saves if a session is running and the last save was more than {@link #AUTOSAVE_INTERVAL_MS} ago. */
	public void maybeAutoSave() {
		if (current == null) return;
		if (nanoClock.getAsLong() - lastSaveNanos >= TimeUnit.MILLISECONDS.toNanos(AUTOSAVE_INTERVAL_MS)) saveNow();
	}

	/** The game is closing: finish an open session as INTERRUPTED, then save. */
	public void shutdown() {
		if (current != null) endSession(EndReason.INTERRUPTED);
		else saveNow();
	}

	// ---- sessions ----

	/** Starts a real session (the mod was toggled ON). An unfinished earlier session is ended as INTERRUPTED. */
	public void startSession() {
		if (current != null) endSession(EndReason.INTERRUPTED);
		current = new StatsSession(false, wallClockMs.getAsLong(), nanoClock.getAsLong());
		lastSaveNanos = nanoClock.getAsLong();
	}

	/** Ends the running session: adds it to the lifetime totals and the history and saves. Returns it, or null if none. */
	public SessionRecord endSession(EndReason reason) {
		if (current == null) return null;
		long now = nanoClock.getAsLong();
		current.restartEnded(now); // a restart still open at the end is closed at the end time
		SessionRecord record = current.snapshot(now, wallClockMs.getAsLong(), Objects.requireNonNull(reason));
		current = null;
		archive(record);
		data.inProgress = null;
		lastSaveNanos = now;
		store.save(data);
		return record.copy();
	}

	private void archive(SessionRecord record) {
		data.lifetime.add(record);
		data.recent.add(0, record);
		while (data.recent.size() > MAX_RECENT) data.recent.remove(data.recent.size() - 1);
	}

	public boolean hasSession() {
		return current != null;
	}

	// ---- updates (each takes the test flag) ----

	/** A restart begins. A repeat while one is open is ignored. */
	public void restartStarted(boolean test) {
		StatsSession s = target(test);
		if (s != null) s.restartStarted(nanoClock.getAsLong());
	}

	/** The restart ends (back to ACTIVE). Returns its duration in ms, or -1 if none was open. */
	public long restartEnded(boolean test) {
		StatsSession s = target(test);
		return s == null ? -1 : s.restartEnded(nanoClock.getAsLong());
	}

	public void stuckDetected(boolean test) {
		StatsSession s = target(test);
		if (s != null) s.stuckDetected();
	}

	public void recoveryAttempt(boolean test) {
		StatsSession s = target(test);
		if (s != null) s.recoveryAttempt();
	}

	public void recoveryResult(boolean success, boolean test) {
		StatsSession s = target(test);
		if (s != null) s.recoveryResult(success);
	}

	/** One block broken by the player while the mod is on (real data only). */
	public void blockMined() {
		if (current != null) current.blockMined();
	}

	/** The real session, or the test counters (created on first use) for test updates. Null = nothing to update. */
	private StatsSession target(boolean test) {
		if (!test) return current;
		if (testCounters == null) testCounters = new StatsSession(true, wallClockMs.getAsLong(), nanoClock.getAsLong());
		return testCounters;
	}

	// ---- event log ----

	/** Adds an event to the ring buffer (the last {@link #MAX_EVENTS} are kept) and forwards it to the event sink. */
	public StatsEvent event(EventType type, String text, boolean test) {
		StatsEvent e = new StatsEvent(wallClockMs.getAsLong(), Objects.requireNonNull(type), test, text == null ? "" : text);
		events.addLast(e);
		while (events.size() > MAX_EVENTS) events.removeFirst();
		try {
			eventSink.accept(e);
		} catch (RuntimeException ignored) {
			// A logging problem must never break the mod.
		}
		return e;
	}

	// ---- API for the GUI and the Test Lab ----

	/** A snapshot of the running session, or null when the mod is off. */
	public SessionRecord getCurrentSession() {
		return current == null ? null : current.snapshot(nanoClock.getAsLong(), wallClockMs.getAsLong(), null);
	}

	/** A copy of the lifetime totals (finished real sessions only). */
	public LifetimeStats getLifetime() {
		return data.lifetime.copy();
	}

	/** Copies of the most recent finished real sessions, newest first (at most {@link #MAX_RECENT}). */
	public List<SessionRecord> getRecentSessions() {
		List<SessionRecord> out = new ArrayList<>(data.recent.size());
		for (SessionRecord r : data.recent) out.add(r.copy());
		return Collections.unmodifiableList(out);
	}

	/** The last {@code n} events (real and test), oldest first. */
	public List<StatsEvent> getRecentEvents(int n) {
		if (n <= 0) return List.of();
		List<StatsEvent> all = new ArrayList<>(events);
		return List.copyOf(all.subList(Math.max(0, all.size() - n), all.size()));
	}

	/**
	 * Clears the lifetime totals and the recent-session list, and saves. The running session is unaffected. The GUI
	 * asks for confirmation before calling this.
	 */
	public void resetLifetime() {
		data.lifetime = new LifetimeStats();
		data.recent.clear();
		saveNow();
	}

	/** Adds a session to the test data only (its {@code test} flag is forced on). Returns the stored copy. */
	public SessionRecord injectTestSession(SessionRecord record) {
		SessionRecord copy = Objects.requireNonNull(record).copy();
		copy.test = true;
		testSessions.add(copy);
		while (testSessions.size() > MAX_TEST_SESSIONS) testSessions.remove(0);
		return copy.copy();
	}

	/** The injected test sessions, oldest first. */
	public List<SessionRecord> getTestSessions() {
		List<SessionRecord> out = new ArrayList<>(testSessions.size());
		for (SessionRecord r : testSessions) out.add(r.copy());
		return Collections.unmodifiableList(out);
	}

	/** Totals of the test-flagged updates (restarts, stuck detections, attempts...), or null if there were none. */
	public SessionRecord getTestCounters() {
		return testCounters == null ? null : testCounters.snapshot(nanoClock.getAsLong(), wallClockMs.getAsLong(), null);
	}

	/** Empties the test data: test counters, injected test sessions and test-flagged events. */
	public void clearTestData() {
		testCounters = null;
		testSessions.clear();
		events.removeIf(StatsEvent::test);
	}
}
