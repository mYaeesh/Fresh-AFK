package dev.afkmod.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The README's settings table is generated from {@link SettingInfo}. This fails when the table between the markers is
 * out of date, and prints the table to paste.
 */
class ReadmeSettingsTableTest {

	@Test
	void readmeSettingsTableMatchesTheRegistry() throws IOException {
		Path readme = Path.of("README.md");
		assertTrue(Files.exists(readme), "README.md not found (tests run from the project root)");
		String text = Files.readString(readme, StandardCharsets.UTF_8).replace("\r\n", "\n");
		int start = text.indexOf(SettingInfo.README_START);
		int end = text.indexOf(SettingInfo.README_END);
		assertTrue(start >= 0 && end > start, "README.md needs the markers:\n" + SettingInfo.README_START + "\n...\n" + SettingInfo.README_END);
		String actual = text.substring(start + SettingInfo.README_START.length(), end).strip();
		String expected = SettingInfo.markdownTable().strip();
		assertEquals(expected, actual, "README settings table is out of date. Replace it with:\n" + expected);
	}
}
