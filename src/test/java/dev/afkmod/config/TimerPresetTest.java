package dev.afkmod.config;

import dev.afkmod.logic.DurationParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerPresetTest {

	@Test
	void aFreshConfigHasTheDefaultPresets() {
		AfkConfig c = new AfkConfig();
		assertEquals(4, c.timerPresets.size());
		assertEquals(3600, c.timerPresets.get(1).seconds);
	}

	@Test
	void presetsSurviveAJsonRoundTrip() {
		AfkConfig c = new AfkConfig();
		c.timerPresets = new ArrayList<>(List.of(new TimerPreset("Night", 9 * 3600 + 30 * 60)));
		AfkConfig loaded = AfkConfig.fromJson(c.toJson());
		assertEquals(1, loaded.timerPresets.size());
		assertEquals("Night", loaded.timerPresets.get(0).name);
		assertEquals(9 * 3600 + 30 * 60, loaded.timerPresets.get(0).seconds);
	}

	@Test
	void anOldConfigWithoutPresetsGetsTheDefaults() {
		assertEquals(TimerPreset.defaults().size(), AfkConfig.fromJson("{\"timerSeconds\": 60}").timerPresets.size());
	}

	@Test
	void anExplicitEmptyListIsKept() {
		assertTrue(AfkConfig.fromJson("{\"timerPresets\": []}").timerPresets.isEmpty());
	}

	@Test
	void cleanDropsBadEntriesTrimsAndCutsNames() {
		String longName = "x".repeat(40);
		List<TimerPreset> cleaned = TimerPreset.clean(new ArrayList<>(Arrays.asList(
				new TimerPreset("  ok  ", 60),
				new TimerPreset("   ", 60),
				new TimerPreset("zero", 0),
				new TimerPreset("negative", -5),
				new TimerPreset("too long", DurationParser.MAX_SECONDS + 1),
				null,
				new TimerPreset(longName, 120))));
		assertEquals(2, cleaned.size());
		assertEquals("ok", cleaned.get(0).name);
		assertEquals(TimerPreset.NAME_MAX, cleaned.get(1).name.length());
	}

	@Test
	void copyFromMakesIndependentCopies() {
		AfkConfig a = new AfkConfig();
		AfkConfig b = new AfkConfig();
		b.copyFrom(a);
		assertNotSame(a.timerPresets.get(0), b.timerPresets.get(0));
		b.timerPresets.get(0).seconds = 999;
		assertEquals(30 * 60, a.timerPresets.get(0).seconds);
	}

	@Test
	void reconnectSettingsAreClamped() {
		AfkConfig c = AfkConfig.fromJson("{\"reconnectAttempts\": 0, \"reconnectDelaySeconds\": 99999}");
		assertEquals(1, c.reconnectAttempts);
		assertEquals(600, c.reconnectDelaySeconds);
	}

	@Test
	void formatUnitsReadsBackThroughParse() {
		for (long seconds : new long[]{1, 59, 60, 61, 3600, 3661, 9 * 3600 + 30 * 60, DurationParser.MAX_SECONDS}) {
			assertEquals(seconds, DurationParser.parse(DurationParser.formatUnits(seconds)), "for " + seconds);
		}
		assertEquals("1h 30m", DurationParser.formatUnits(5400));
		assertEquals("0s", DurationParser.formatUnits(0));
	}
}
