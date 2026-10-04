package dev.afkmod.client;

import dev.afkmod.AfkModClient;
import dev.afkmod.logic.KeywordMatcher;
import dev.afkmod.logic.MessageSource;
import dev.afkmod.stats.StatsEvent;
import dev.afkmod.testlab.RingBuffer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Debug logger for incoming text, controlled by {@code debugLogging} in the config. Works even while the mod
 * is OFF, so the restart message can be identified before relying on it. Writes to the game log and to
 * {@code config/afkmod-messages.log}.
 *
 * <p>Action bar text is tagged {@code [ACTION_BAR overlay]}. Restart events (queue text appearing/disappearing,
 * disconnect, join) are written as {@code [EVENT]} lines. Timestamps have milliseconds. While a Test Lab scenario
 * runs every line is prefixed with {@code [TEST]}. The last {@value #RECENT_LINES} lines written to the game log are
 * also kept in memory for the Test Lab's debug report.
 */
public final class MessageLog {
	public static final String FILE_NAME = "afkmod-messages.log";
	public static final int RECENT_LINES = 50;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

	private final RingBuffer<String> recent = new RingBuffer<>(RECENT_LINES);
	private boolean fileErrorReported;
	private boolean testMode;

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** While on, every line is tagged {@code [TEST]}. */
	public void setTestMode(boolean testMode) {
		this.testMode = testMode;
	}

	/** The last lines this logger wrote to the game log, oldest first. */
	public List<String> recentLines() {
		return recent.all();
	}

	public void log(MessageSource source, String text, KeywordMatcher.Result result) {
		if (!AfkModClient.config().debugLogging) return;
		String oneLine = text.replace("\r", "").replace("\n", "\\n");
		String tag = source == MessageSource.ACTION_BAR ? "ACTION_BAR overlay" : source.name();
		write("[message] " + tag + " (" + result + "): " + oneLine, "[" + tag + "] (" + result + ") " + oneLine, true);
	}

	/** A restart-related event (queue text shown/gone, disconnect, join). Always in the game log; in the file with debug logging on. */
	public void event(String description) {
		write("[event] " + description, "[EVENT] " + description, AfkModClient.config().debugLogging);
	}

	/** A stats event-log entry. Written (game log and file, as {@code [STATS]}) only when debug logging is on. */
	public void statsEvent(StatsEvent e) {
		if (!AfkModClient.config().debugLogging) return;
		String line = (e.test() ? "[TEST] " : "") + e.type() + " " + e.text();
		write("[stats] " + line, "[STATS] " + line, true);
	}

	/** A Test Lab line: always tagged {@code [TEST]} and in the game log; in the file with debug logging on. */
	public void test(String description) {
		String entry = "[TEST] " + description.replace("\r", "").replace("\n", " | ");
		write(entry, entry, AfkModClient.config().debugLogging);
	}

	private void write(String gameLine, String fileEntry, boolean toFile) {
		String tag = testMode && !fileEntry.startsWith("[TEST]") ? "[TEST] " : "";
		recent.add(timestamp() + " " + tag + fileEntry);
		AfkModClient.LOGGER.info("{}{}", tag, gameLine);
		if (toFile) append(tag + fileEntry);
	}

	private static String timestamp() {
		return LocalDateTime.now().format(TIME);
	}

	private void append(String entry) {
		String line = timestamp() + " " + entry + System.lineSeparator();
		try {
			Files.writeString(file(), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			if (!fileErrorReported) {
				fileErrorReported = true;
				AfkModClient.LOGGER.error("Could not write {}", file(), e);
			}
		}
	}
}
