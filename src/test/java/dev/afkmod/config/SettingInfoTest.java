package dev.afkmod.config;

import dev.afkmod.config.SettingInfo.Entry;
import dev.afkmod.config.SettingInfo.Kind;
import dev.afkmod.config.SettingInfo.Scope;
import dev.afkmod.gui.TooltipText;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the single source of truth for every setting: a new config field without a registry entry (or with an empty
 * description) fails here, and the defaults shown in tooltips must equal the real defaults of {@link AfkConfig}.
 */
class SettingInfoTest {

	/** Every saved field of the config class, found by reflection (not through the registry). */
	private static List<Field> configFields() {
		List<Field> fields = new ArrayList<>();
		for (Field f : AfkConfig.class.getDeclaredFields()) {
			int m = f.getModifiers();
			if (Modifier.isStatic(m) || Modifier.isTransient(m) || !Modifier.isPublic(m)) continue;
			fields.add(f);
		}
		return fields;
	}

	@Test
	void everyConfigFieldHasARegistryEntryWithADescription() {
		List<Field> fields = configFields();
		assertFalse(fields.isEmpty(), "reflection found no config fields");
		for (Field f : fields) {
			Entry e = SettingInfo.find(f.getName()).orElse(null);
			if (e == null) fail("AfkConfig." + f.getName() + " has no SettingInfo entry. Add one to SettingInfo.");
			assertEquals(Scope.CONFIG, e.scope(), f.getName());
			assertNotNull(e.description(), f.getName());
			assertFalse(e.description().isBlank(), "empty description for " + f.getName());
			assertFalse(e.displayName().isBlank(), "empty display name for " + f.getName());
			assertTrue(e.description().length() >= 20, "description too short for " + f.getName());
		}
	}

	@Test
	void noEntryRefersToAFieldThatNoLongerExists() {
		Set<String> names = new HashSet<>();
		for (Field f : configFields()) names.add(f.getName());
		for (Entry e : SettingInfo.all()) {
			if (e.scope() == Scope.CONFIG) assertTrue(names.contains(e.key()), "stale SettingInfo entry " + e.key());
		}
	}

	@Test
	void testLabOptionsAreRegisteredWithDescriptions() {
		for (String key : List.of("testRestartSeconds", "testTimerSeconds", "testStartYaw", "testFullTimerEnd",
				"testRealDisconnect", "reportRedactIdentity")) {
			Entry e = SettingInfo.get(key);
			assertFalse(e.description().isBlank(), key);
		}
		assertEquals(Scope.TEST_LAB, SettingInfo.get("testRestartSeconds").scope());
	}

	/** The expected display of a real default, worked out here without using the registry's formatter. */
	private static String expectedDefault(Object real) {
		if (real == null) return "none";
		if (real instanceof Boolean b) return b ? "on" : "off";
		if (real instanceof Float f) return new BigDecimal(Float.toString(f)).stripTrailingZeros().toPlainString();
		if (real instanceof Double d) return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
		if (real instanceof Number n) return Long.toString(n.longValue());
		if (real instanceof List<?> list) return list.isEmpty() ? "none" : String.join(", ", list.stream().map(Object::toString).toList());
		if (real instanceof HudCorner c) return c.displayName();
		return real.toString();
	}

	@Test
	void displayedDefaultsEqualTheRealDefaults() throws IllegalAccessException {
		AfkConfig real = new AfkConfig();
		for (Field f : configFields()) {
			Entry e = SettingInfo.get(f.getName());
			String expected = expectedDefault(f.get(real));
			assertEquals(expected, e.defaultValueText(), "default shown for " + f.getName());
			assertTrue(e.defaultLine().startsWith("Default: " + expected), e.defaultLine());
			assertTrue(TooltipText.forSetting(f.getName()).footer().get(0).startsWith("Default: " + expected),
					"tooltip default for " + f.getName());
		}
	}

	@Test
	void keyDefaultsMatchTheValuesTheSpecNames() {
		assertEquals("Default: 20 ticks - Range: 1-200 ticks", SettingInfo.get("checkIntervalTicks").defaultLine());
		assertEquals("Default: servers, restart queue", SettingInfo.get("restartKeywords").defaultLine());
		assertEquals("Default: 3 s - Range: 0-3600 s", SettingInfo.get("queueGoneSeconds").defaultLine());
		assertEquals("Default: 15 min - Range: 1-600 min", SettingInfo.get("maxRestartWaitMinutes").defaultLine());
		assertEquals("Default: off", SettingInfo.get("releaseCrouchOnRestart").defaultLine());
		assertEquals("Default: on", SettingInfo.get("hudEnabled").defaultLine());
		assertEquals("Default: Top left", SettingInfo.get("hudCorner").defaultLine());
		assertEquals("Default: 0.75 x - Range: 0.5-2 x", SettingInfo.get("hudScale").defaultLine());
		assertEquals("Default: 0.5 s - Range: 0-10 s", SettingInfo.get("recoveryTurnSeconds").defaultLine());
		assertEquals("Default: 0 s (none) - Range: 0 s-100 h", SettingInfo.get("timerSeconds").defaultLine());
		assertEquals("Default: 15 s - Range: 1-600 s", SettingInfo.get("testRestartSeconds").defaultLine());
	}

	@Test
	void everyDefaultLineIsOneShortFinalLine() {
		for (Entry e : SettingInfo.all()) {
			assertEquals(1, TooltipText.forSetting(e.key()).footer().size(), "default line wraps for " + e.key() + ": " + e.defaultLine());
			assertTrue(e.defaultLine().startsWith("Default: "), e.key());
		}
	}

	@Test
	void numericRangesContainTheDefaultAndMatchSanitize() throws Exception {
		AfkConfig real = new AfkConfig();
		for (Field f : configFields()) {
			Entry e = SettingInfo.get(f.getName());
			if (!e.hasRange() || e.unused()) continue;
			double def = ((Number) f.get(real)).doubleValue();
			assertTrue(def >= e.min() && def <= e.max(), "default outside the range for " + f.getName());

			// A far-too-large hand-edited value is clamped into the registered range.
			AfkConfig big = new AfkConfig();
			set(f, big, e.max() * 10 + 1000);
			big.sanitize();
			double after = ((Number) f.get(big)).doubleValue();
			assertTrue(after <= e.max() + 1e-4 && after >= e.min() - 1e-4, "sanitize left " + f.getName() + " = " + after);

			// A too-small value ends up inside the range too (below-minimum values may fall back to the default).
			AfkConfig small = new AfkConfig();
			set(f, small, e.min() - 1000);
			small.sanitize();
			double low = ((Number) f.get(small)).doubleValue();
			assertTrue(low >= e.min() - 1e-4 && low <= e.max() + 1e-4, "sanitize left " + f.getName() + " = " + low);
		}
	}

	private static void set(Field f, AfkConfig c, double value) throws IllegalAccessException {
		Class<?> t = f.getType();
		if (t == int.class) f.setInt(c, (int) value);
		else if (t == long.class) f.setLong(c, (long) value);
		else if (t == double.class) f.setDouble(c, value);
		else if (t == float.class) f.setFloat(c, (float) value);
		else fail("unexpected numeric type " + t + " for " + f.getName());
	}

	@Test
	void numberFormatting() {
		assertEquals("3", SettingInfo.number(3.0));
		assertEquals("0.75", SettingInfo.number(0.75));
		assertEquals("0.5", SettingInfo.number(0.5));
		assertEquals("0.1", SettingInfo.number(0.1));
		assertEquals("120", SettingInfo.number(120));
	}

	@Test
	void clampHelpersUseTheRegisteredRange() {
		assertEquals(3600, SettingInfo.clampInt("queueGoneSeconds", 999999));
		assertEquals(0, SettingInfo.clampInt("queueGoneSeconds", -4));
		assertEquals(2.0, SettingInfo.clampDouble("hudScale", 9.0));
		assertEquals(Kind.DECIMAL, SettingInfo.get("hudScale").kind());
	}

	@Test
	void unknownKeyThrowsAClearMessage() {
		var ex = assertThrows(IllegalArgumentException.class, () -> SettingInfo.get("nope"));
		assertTrue(ex.getMessage().contains("nope"));
	}
}
