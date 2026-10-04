package dev.afkmod.logic;

import dev.afkmod.logic.DurationParser.DurationParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationParserTest {
	@Test
	void unitForms() {
		assertEquals(2 * 3600 + 30 * 60, DurationParser.parse("2h30m"));
		assertEquals(3600 + 5 * 60 + 3, DurationParser.parse("1h 5m 3s"));
		assertEquals(45 * 60, DurationParser.parse("45m"));
		assertEquals(90, DurationParser.parse("90s"));
		assertEquals(3 * 3600, DurationParser.parse("3H"));
		assertEquals(30 * 60 + 2 * 3600, DurationParser.parse("30m2h"));
		assertEquals(120 * 60, DurationParser.parse("120m"));
	}

	@Test
	void surroundingWhitespaceIsIgnored() {
		assertEquals(150, DurationParser.parse("  2m 30s  "));
	}

	@Test
	void bareNumberIsSeconds() {
		assertEquals(90, DurationParser.parse("90"));
		assertEquals(0, DurationParser.parse("0"));
	}

	@Test
	void clockForms() {
		assertEquals(3600 + 30 * 60, DurationParser.parse("1:30:00"));
		assertEquals(30 * 60, DurationParser.parse("30:00"));
		assertEquals(65, DurationParser.parse("1:05"));
	}

	@Test
	void rejectsInvalidInput() {
		for (String bad : new String[] {"", "   ", "abc", "2x", "h", "-5m", "1.5h", "1h1h", "5m 3m", "1:60", "1:60:00", "1:00:60", "2h30", "1::2"}) {
			assertThrows(DurationParseException.class, () -> DurationParser.parse(bad), bad);
		}
		assertThrows(DurationParseException.class, () -> DurationParser.parse(null));
	}

	@Test
	void rejectsTooLong() {
		assertThrows(DurationParseException.class, () -> DurationParser.parse("101h"));
		assertThrows(DurationParseException.class, () -> DurationParser.parse("99999999999999999999"));
		assertEquals(DurationParser.MAX_SECONDS, DurationParser.parse("100h"));
	}

	@Test
	void fromFields() {
		assertEquals(3600 + 2 * 60 + 3, DurationParser.fromFields("1", "2", "3"));
		assertEquals(90 * 60, DurationParser.fromFields("", "90", " "));
		assertEquals(0, DurationParser.fromFields(null, null, null));
		assertThrows(DurationParseException.class, () -> DurationParser.fromFields("a", "", ""));
		assertThrows(DurationParseException.class, () -> DurationParser.fromFields("-1", "", ""));
		assertThrows(DurationParseException.class, () -> DurationParser.fromFields("200", "", ""));
	}

	@Test
	void format() {
		assertEquals("1:23:45", DurationParser.format(3600 + 23 * 60 + 45));
		assertEquals("5:07", DurationParser.format(5 * 60 + 7));
		assertEquals("0:00", DurationParser.format(0));
		assertEquals("0:00", DurationParser.format(-5));
		assertEquals("100:00:00", DurationParser.format(DurationParser.MAX_SECONDS));
	}
}
