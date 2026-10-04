package dev.afkmod.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerTimeWatcherTest {
	private final ServerTimeWatcher watcher = new ServerTimeWatcher();

	@Test
	void notRespondingBeforeAnyServerTime() {
		assertFalse(watcher.advancedSinceLastCheck());
		assertFalse(watcher.advancedSinceLastCheck());
	}

	@Test
	void firstCheckOnlyTakesABaseline() {
		watcher.onServerTime(100);
		assertFalse(watcher.advancedSinceLastCheck());
		watcher.onServerTime(120);
		assertTrue(watcher.advancedSinceLastCheck());
	}

	@Test
	void frozenServerIsNotResponding() {
		watcher.onServerTime(100);
		watcher.advancedSinceLastCheck();
		watcher.onServerTime(100); // the tick freeze: time re-sent but not advancing
		assertFalse(watcher.advancedSinceLastCheck());
		assertFalse(watcher.advancedSinceLastCheck()); // nothing received at all
	}

	@Test
	void respondingAgainAfterFreeze() {
		watcher.onServerTime(100);
		watcher.advancedSinceLastCheck();
		assertFalse(watcher.advancedSinceLastCheck());
		watcher.onServerTime(140);
		assertTrue(watcher.advancedSinceLastCheck());
	}

	@Test
	void resetForgetsTheOldWorld() {
		watcher.onServerTime(5000);
		watcher.advancedSinceLastCheck();
		watcher.reset();
		watcher.onServerTime(10); // new world/server: time jumped backwards
		assertFalse(watcher.advancedSinceLastCheck());
		watcher.onServerTime(30);
		assertTrue(watcher.advancedSinceLastCheck());
	}
}
