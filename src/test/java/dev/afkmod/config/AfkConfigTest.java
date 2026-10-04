package dev.afkmod.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkConfigTest {
	@TempDir
	Path dir;

	@Test
	void defaultsMatchSpec() {
		AfkConfig c = new AfkConfig();
		assertEquals(20, c.checkIntervalTicks);
		assertEquals(List.of("servers", "restart queue"), c.restartKeywords);
		assertEquals(List.of("whispered"), c.ignoreKeywords);
		assertEquals(List.of("restart queue"), c.queueKeywords);
		assertEquals(List.of(), c.restartEndKeywords);
		assertEquals(3, c.queueGoneSeconds);
		assertEquals(30, c.noReconnectFallbackSeconds);
		assertEquals(15, c.maxRestartWaitMinutes);
		assertEquals(10, c.restartClearTimeoutSeconds);
		assertEquals(3, c.settleDelaySeconds);
		assertEquals(15, c.postResumeCooldownSeconds);
		assertEquals(120, c.reconnectGraceSeconds);
		assertFalse(c.releaseCrouchOnRestart);
		assertEquals(0, c.timerSeconds);
		assertFalse(c.debugLogging);
		assertTrue(c.hudEnabled);
		assertFalse(c.hudDetailed);
		assertEquals(HudCorner.TOP_LEFT, c.hudCorner);
		assertTrue(c.hudScale < 1.0f, "HUD defaults to a small scale");
	}

	@Test
	void loadCreatesFileWithDefaultsWhenMissing() throws IOException {
		Path file = dir.resolve("config").resolve(AfkConfig.FILE_NAME);
		AfkConfig c = AfkConfig.load(file);
		assertTrue(Files.exists(file));
		assertEquals(20, c.checkIntervalTicks);
		String json = Files.readString(file);
		for (String key : List.of("checkIntervalTicks", "restartKeywords", "ignoreKeywords", "queueKeywords", "restartEndKeywords",
				"queueGoneSeconds", "noReconnectFallbackSeconds", "maxRestartWaitMinutes",
				"restartClearTimeoutSeconds", "settleDelaySeconds", "postResumeCooldownSeconds", "reconnectGraceSeconds",
				"releaseCrouchOnRestart", "timerSeconds", "debugLogging", "hudEnabled", "hudDetailed", "hudCorner", "hudScale")) {
			assertTrue(json.contains("\"" + key + "\""), key);
		}
	}

	@Test
	void saveAndLoadRoundTrip() throws IOException {
		Path file = dir.resolve(AfkConfig.FILE_NAME);
		AfkConfig c = new AfkConfig();
		c.timerSeconds = 9000;
		c.restartKeywords = List.of("servers", "reboot");
		c.hudCorner = HudCorner.BOTTOM_RIGHT;
		c.releaseCrouchOnRestart = true;
		c.save(file);

		AfkConfig loaded = AfkConfig.load(file);
		assertEquals(9000, loaded.timerSeconds);
		assertEquals(List.of("servers", "reboot"), loaded.restartKeywords);
		assertEquals(HudCorner.BOTTOM_RIGHT, loaded.hudCorner);
		assertTrue(loaded.releaseCrouchOnRestart);
	}

	@Test
	void missingFieldsGetDefaults() {
		AfkConfig c = AfkConfig.fromJson("{\"timerSeconds\": 60}");
		assertEquals(60, c.timerSeconds);
		assertEquals(List.of("servers", "restart queue"), c.restartKeywords);
		assertEquals(List.of("whispered"), c.ignoreKeywords);
		assertEquals(20, c.checkIntervalTicks);
	}

	@Test
	void invalidValuesAreSanitized() {
		AfkConfig c = AfkConfig.fromJson("""
				{"checkIntervalTicks": 0, "settleDelaySeconds": -4, "timerSeconds": -1,
				 "hudScale": 50, "hudCorner": null, "restartKeywords": ["", "  servers  ", null],
				 "queueGoneSeconds": -1, "noReconnectFallbackSeconds": -5, "maxRestartWaitMinutes": 0}
				""");
		assertEquals(20, c.checkIntervalTicks);
		assertEquals(0, c.settleDelaySeconds);
		assertEquals(0, c.timerSeconds);
		assertEquals(AfkConfig.HUD_SCALE_MAX, c.hudScale);
		assertEquals(HudCorner.TOP_LEFT, c.hudCorner);
		assertEquals(List.of("servers"), c.restartKeywords);
		assertEquals(0, c.queueGoneSeconds);
		assertEquals(0, c.noReconnectFallbackSeconds);
		assertEquals(15, c.maxRestartWaitMinutes);
	}

	@Test
	void explicitEmptyListIsKept() {
		AfkConfig c = AfkConfig.fromJson("{\"ignoreKeywords\": []}");
		assertEquals(List.of(), c.ignoreKeywords);
	}

	@Test
	void corruptFileIsBackedUpAndReplacedWithDefaults() throws IOException {
		Path file = dir.resolve(AfkConfig.FILE_NAME);
		Files.writeString(file, "{ this is not json");
		AfkConfig c = AfkConfig.load(file);
		assertEquals(20, c.checkIntervalTicks);
		assertTrue(Files.exists(dir.resolve(AfkConfig.FILE_NAME + ".bak")));
		assertEquals(20, AfkConfig.load(file).checkIntervalTicks);
	}

	@Test
	void hudCornerCycles() {
		assertEquals(HudCorner.TOP_RIGHT, HudCorner.TOP_LEFT.next());
		assertEquals(HudCorner.TOP_LEFT, HudCorner.BOTTOM_LEFT.next());
	}

	@Test
	void movementRecoveryDefaults() {
		AfkConfig c = new AfkConfig();
		assertTrue(c.movementCheckEnabled);
		assertEquals(20, c.stuckWindowSeconds);
		assertEquals(3.0, c.stuckDistanceBlocks);
		assertEquals(0.5, c.recoveryTurnSeconds);
		assertEquals(2.0, c.recoveryWalkBlocks);
		assertEquals(5.0, c.recoveryWalkTimeoutSeconds);
		assertEquals(2, c.recoveryRetries);
		assertTrue(c.recoveryEdgeCheck);
	}

	@Test
	void movementRecoverySanitizeAndCopy() {
		AfkConfig c = AfkConfig.fromJson("""
				{"stuckWindowSeconds": 0, "stuckDistanceBlocks": -1, "recoveryTurnSeconds": -2,
				 "recoveryWalkBlocks": 1000, "recoveryWalkTimeoutSeconds": 0, "recoveryRetries": -3}""");
		assertEquals(20, c.stuckWindowSeconds);
		assertEquals(0.1, c.stuckDistanceBlocks);
		assertEquals(0.0, c.recoveryTurnSeconds, "0 = instant snap is allowed");
		assertEquals(64.0, c.recoveryWalkBlocks);
		assertEquals(0.5, c.recoveryWalkTimeoutSeconds);
		assertEquals(0, c.recoveryRetries);

		AfkConfig old = AfkConfig.fromJson("{\"checkIntervalTicks\": 20}");
		assertTrue(old.movementCheckEnabled, "an old config file gets the new defaults");

		AfkConfig copy = new AfkConfig();
		c.movementCheckEnabled = false;
		c.recoveryEdgeCheck = false;
		copy.copyFrom(c);
		assertFalse(copy.movementCheckEnabled);
		assertFalse(copy.recoveryEdgeCheck);
		assertEquals(64.0, copy.recoveryWalkBlocks);
		assertEquals(0, copy.recoveryRetries);
	}
}
