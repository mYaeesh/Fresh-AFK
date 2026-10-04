package dev.afkmod.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Reads and writes the stats file ({@code config/afkmod-stats.json}). Neither method ever throws: a missing, empty
 * or corrupt file loads as fresh data, and a failed write is logged and reported as {@code false}. Writes go to a
 * temporary file in the same folder and are then moved over the real file (atomically where the file system allows).
 * The file is replaceable ({@link #setFile}) so tests never touch the real stats file.
 */
final class StatsStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("afkmod");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private Path file;
	private boolean writeErrorReported;

	StatsStore(Path file) {
		this.file = Objects.requireNonNull(file);
	}

	Path file() {
		return file;
	}

	void setFile(Path file) {
		this.file = Objects.requireNonNull(file);
		writeErrorReported = false;
	}

	/** Never null and never throws. A corrupt file is moved aside as {@code .corrupt} and fresh data is returned. */
	StatsData load() {
		try {
			if (Files.notExists(file)) return new StatsData();
			StatsData data;
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				data = GSON.fromJson(reader, StatsData.class);
			}
			if (data == null) return new StatsData(); // empty file
			data.sanitize();
			return data;
		} catch (Exception e) {
			LOGGER.warn("Could not read {}, starting with fresh stats", file, e);
			moveAside();
			return new StatsData();
		}
	}

	/** Atomic write. Returns false (and logs once) on failure; never throws. */
	boolean save(StatsData data) {
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Path dir = file.getParent();
			if (dir != null) Files.createDirectories(dir);
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
			try {
				Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			writeErrorReported = false;
			return true;
		} catch (Exception e) {
			if (!writeErrorReported) {
				writeErrorReported = true;
				LOGGER.warn("Could not write {}", file, e);
			}
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException | RuntimeException ignored) {
				// best effort
			}
			return false;
		}
	}

	private void moveAside() {
		try {
			Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception ignored) {
			// If it can't be moved, the next save simply overwrites it.
		}
	}
}
