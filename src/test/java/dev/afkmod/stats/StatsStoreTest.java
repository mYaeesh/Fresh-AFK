package dev.afkmod.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loading and saving the stats file: it must never crash, whatever is on disk. */
class StatsStoreTest {
	@TempDir
	Path dir;

	private final TestClock clock = new TestClock();
	private Path file;

	@BeforeEach
	void setUp() {
		file = dir.resolve("afkmod-stats.json");
	}

	private AfkStats open() {
		return clock.stats(file);
	}

	private void assertFresh(AfkStats stats) {
		assertEquals(0, stats.getLifetime().sessions);
		assertTrue(stats.getRecentSessions().isEmpty());
		assertEquals(0, stats.getLifetime().wallMs);
	}

	@Test
	void missingFileStartsFresh() {
		assertFresh(open());
		assertFalse(Files.exists(file), "loading alone does not create the file");
	}

	@Test
	void emptyFileStartsFresh() throws IOException {
		Files.writeString(file, "");
		assertFresh(assertDoesNotThrow(this::open));
	}

	@Test
	void corruptFileStartsFreshAndIsMovedAside() throws IOException {
		Files.writeString(file, "{ this is not json ::: ");
		AfkStats stats = assertDoesNotThrow(this::open);

		assertFresh(stats);
		assertTrue(Files.exists(dir.resolve("afkmod-stats.json.corrupt")));
		assertFalse(Files.exists(file));
	}

	@Test
	void binaryGarbageStartsFresh() throws IOException {
		Files.write(file, new byte[]{0, (byte) 0xFF, (byte) 0xFE, 12, 99, 0, 1});
		assertFresh(assertDoesNotThrow(this::open));
	}

	@Test
	void wrongTypesStartFresh() throws IOException {
		Files.writeString(file, "{\"lifetime\": 5, \"recent\": \"x\"}");
		assertFresh(assertDoesNotThrow(this::open));
	}

	@Test
	void valuesThatAreJsonNullAreRepaired() throws IOException {
		Files.writeString(file, "{\"lifetime\": null, \"recent\": null, \"inProgress\": null}");
		AfkStats stats = assertDoesNotThrow(this::open);
		assertFresh(stats);
		// And it is fully usable afterwards.
		stats.startSession();
		clock.advanceSeconds(5);
		assertEquals(5_000, stats.endSession(EndReason.MANUAL_TOGGLE).wallMs);
	}

	@Test
	void nullEntriesAndTestEntriesInTheHistoryAreDropped() throws IOException {
		Files.writeString(file, "{\"recent\": [null, {\"wallMs\": 7, \"test\": true}, {\"wallMs\": 9, \"endReason\": \"DEATH\"}]}");
		AfkStats stats = open();
		assertEquals(1, stats.getRecentSessions().size());
		assertEquals(9, stats.getRecentSessions().get(0).wallMs);
	}

	@Test
	void anUnknownEndReasonDoesNotBreakLoading() throws IOException {
		Files.writeString(file, "{\"recent\": [{\"wallMs\": 9, \"endReason\": \"SOMETHING_NEW\"}]}");
		assertEquals(1, assertDoesNotThrow(this::open).getRecentSessions().size());
	}

	@Test
	void anOversizedHistoryIsTrimmedToTheNewest20() throws IOException {
		StringBuilder sb = new StringBuilder("{\"recent\": [");
		for (int i = 1; i <= 30; i++) sb.append(i > 1 ? "," : "").append("{\"wallMs\": ").append(i).append("}");
		Files.writeString(file, sb.append("]}").toString());

		AfkStats stats = open();
		assertEquals(20, stats.getRecentSessions().size());
		assertEquals(1, stats.getRecentSessions().get(0).wallMs, "the file lists newest first, so the head is kept");
	}

	@Test
	void freshStartAfterCorruptionCanBeSavedAndReloaded() throws IOException {
		Files.writeString(file, "garbage");
		AfkStats stats = open();
		stats.startSession();
		clock.advanceSeconds(30);
		stats.endSession(EndReason.TIMER_ENDED);

		AfkStats reloaded = open();
		assertEquals(1, reloaded.getLifetime().sessions);
		assertEquals(30_000, reloaded.getLifetime().wallMs);
	}

	@Test
	void saveCreatesMissingFolders() {
		Path nested = dir.resolve("a").resolve("b").resolve("stats.json");
		AfkStats stats = clock.stats(nested);
		stats.startSession();
		stats.endSession(EndReason.MANUAL_TOGGLE);
		assertTrue(Files.exists(nested));
	}

	@Test
	void saveReplacesAnExistingFileAndLeavesNoTempFile() throws IOException {
		AfkStats stats = open();
		for (int i = 0; i < 3; i++) {
			stats.startSession();
			clock.advanceSeconds(1);
			stats.endSession(EndReason.MANUAL_TOGGLE);
		}
		assertEquals(3, open().getLifetime().sessions);
		try (var files = Files.list(dir)) {
			assertEquals(1, files.count(), "only the stats file exists, no .tmp");
		}
	}

	@Test
	void aFailedWriteDoesNotThrowAndReturnsFalse() throws IOException {
		// The parent "folder" is a regular file, so the write cannot succeed.
		Path blocker = dir.resolve("blocker");
		Files.writeString(blocker, "i am a file");
		AfkStats stats = clock.stats(blocker.resolve("stats.json"));

		stats.startSession();
		clock.advanceSeconds(1);
		assertDoesNotThrow(() -> stats.endSession(EndReason.MANUAL_TOGGLE));
		assertFalse(assertDoesNotThrow(stats::saveNow));
		assertEquals(1, stats.getLifetime().sessions, "the in-memory stats still work");
	}

	@Test
	void theFileIsWrittenAsReadableJsonWithTheDocumentedFields() throws IOException {
		AfkStats stats = open();
		stats.startSession();
		clock.advanceSeconds(10);
		stats.endSession(EndReason.TIMER_ENDED);

		String json = Files.readString(file);
		for (String key : new String[]{"\"lifetime\"", "\"recent\"", "\"wallMs\"", "\"activeMs\"", "\"restartMs\"",
				"\"endReason\": \"TIMER_ENDED\"", "\"endedInLogout\": true", "\"startEpochMs\"", "\"endEpochMs\""}) {
			assertTrue(json.contains(key), "missing " + key);
		}
	}
}
