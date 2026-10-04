package dev.afkmod.stats;

/** One entry of the in-memory event log. {@code test} marks events from the Test Lab. */
public record StatsEvent(long timeMillis, EventType type, boolean test, String text) {
}
