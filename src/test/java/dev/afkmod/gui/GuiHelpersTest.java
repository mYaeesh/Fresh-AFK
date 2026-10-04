package dev.afkmod.gui;

import dev.afkmod.config.SettingInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests of the pure GUI helpers: wrapping, tooltip content and placement, field validation and row scrolling. */
class GuiHelpersTest {

	// ---- TextWrap ----

	@Test
	void wrapsAtTheLimitWithoutBreakingWords() {
		List<String> lines = TextWrap.wrap("How long the queue text must stay gone before the mod believes the restart is over.", 40);
		for (String line : lines) assertTrue(line.length() <= 40, line);
		assertEquals("How long the queue text must stay gone", lines.get(0));
		assertEquals("How long the queue text must stay gone before the mod believes the restart is over.",
				String.join(" ", lines));
	}

	@Test
	void splitsAWordLongerThanALine() {
		List<String> lines = TextWrap.wrap("a " + "x".repeat(95) + " b", 40);
		for (String line : lines) assertTrue(line.length() <= 40, line);
		assertEquals("a", lines.get(0));
		assertEquals("x".repeat(15) + " b", lines.get(lines.size() - 1));
		assertEquals(95, String.join("", lines).chars().filter(c -> c == 'x').count());
	}

	@Test
	void keepsExplicitNewlinesAndHandlesEmptyText() {
		assertEquals(List.of("one", "two"), TextWrap.wrap("one\ntwo", 40));
		assertTrue(TextWrap.wrap("", 40).isEmpty());
		assertTrue(TextWrap.wrap(null, 40).isEmpty());
		assertEquals(List.of("a", "b"), TextWrap.wrap("  a   b  ", 1));
	}

	// ---- TooltipText ----

	@Test
	void everySettingTooltipWrapsAtFortyAndEndsWithTheDefaultLine() {
		for (SettingInfo.Entry e : SettingInfo.all()) {
			TooltipText t = TooltipText.forSetting(e.key());
			assertFalse(t.body().isEmpty(), e.key());
			for (String line : t.allLines()) assertTrue(line.length() <= TextWrap.TOOLTIP_CHARS, e.key() + ": " + line);
			List<String> all = t.allLines();
			assertEquals(e.defaultLine(), all.get(all.size() - 1), e.key());
		}
	}

	// ---- TooltipPlacement ----

	@Test
	void tooltipStaysOnScreenOnEveryEdge() {
		int sw = 320, sh = 240, w = 200, h = 80;
		int[][] anchors = {{0, 0}, {sw, 0}, {0, sh}, {sw, sh}, {sw / 2, sh / 2}, {sw - 3, sh / 2}, {5, sh - 2}};
		for (int[] a : anchors) {
			int[] p = TooltipPlacement.place(sw, sh, a[0], a[1], w, h);
			assertTrue(p[0] >= TooltipPlacement.MARGIN, "left " + p[0]);
			assertTrue(p[1] >= TooltipPlacement.MARGIN, "top " + p[1]);
			assertTrue(p[0] + w <= sw - TooltipPlacement.MARGIN, "right " + (p[0] + w));
			assertTrue(p[1] + h <= sh - TooltipPlacement.MARGIN, "bottom " + (p[1] + h));
		}
	}

	@Test
	void tooltipFlipsLeftNearTheRightEdgeAndFollowsThePointerOtherwise() {
		int[] mid = TooltipPlacement.place(800, 600, 100, 300, 150, 60);
		assertEquals(112, mid[0]);
		assertEquals(288, mid[1]);
		int[] right = TooltipPlacement.place(800, 600, 700, 300, 150, 60);
		assertTrue(right[0] + 150 <= 700, "flipped to the left of the pointer");
	}

	@Test
	void tooltipLargerThanTheScreenSitsAtTheMargin() {
		int[] p = TooltipPlacement.place(100, 100, 50, 50, 300, 300);
		assertEquals(TooltipPlacement.MARGIN, p[0]);
		assertEquals(TooltipPlacement.MARGIN, p[1]);
	}

	// ---- FieldValidator ----

	@Test
	void wholeNumbersAreCheckedAgainstTheRegisteredRange() {
		assertEquals(5, FieldValidator.wholeNumber(" 5 ", "queueGoneSeconds").value());
		assertEquals(3600, FieldValidator.wholeNumber("3600", "queueGoneSeconds").value());
		assertEquals("Must be 0-3600 s", FieldValidator.wholeNumber("3601", "queueGoneSeconds").error());
		assertEquals("Must be 0-3600 s", FieldValidator.wholeNumber("-1", "queueGoneSeconds").error());
		assertFalse(FieldValidator.wholeNumber("", "queueGoneSeconds").ok());
		assertFalse(FieldValidator.wholeNumber("abc", "queueGoneSeconds").ok());
		assertFalse(FieldValidator.wholeNumber("1.5", "queueGoneSeconds").ok());
		assertFalse(FieldValidator.wholeNumber("99999999999999999999", "queueGoneSeconds").ok());
		assertFalse(FieldValidator.wholeNumber(null, "queueGoneSeconds").ok());
		assertEquals(1, FieldValidator.wholeNumber("1", "maxRestartWaitMinutes").value());
		assertFalse(FieldValidator.wholeNumber("0", "maxRestartWaitMinutes").ok());
	}

	@Test
	void decimalsRejectJunkNanInfinityAndOutOfRange() {
		assertEquals(0.5, FieldValidator.decimal("0.5", "recoveryTurnSeconds").value());
		assertEquals(10.0, FieldValidator.decimal("10", "recoveryTurnSeconds").value());
		assertEquals(0.25, FieldValidator.decimal(".25", "recoveryTurnSeconds").value());
		assertFalse(FieldValidator.decimal("10.01", "recoveryTurnSeconds").ok());
		assertFalse(FieldValidator.decimal("-0.1", "recoveryTurnSeconds").ok());
		for (String junk : new String[]{"NaN", "Infinity", "-Infinity", "1e3", "0x10", "5d", "5f", "1,5", "..", "-", ".", "", "  "}) {
			assertFalse(FieldValidator.decimal(junk, "recoveryTurnSeconds").ok(), "accepted: '" + junk + "'");
		}
		assertTrue(FieldValidator.decimal("0.1", "stuckDistanceBlocks").ok());
		assertFalse(FieldValidator.decimal("0.05", "stuckDistanceBlocks").ok());
	}

	@Test
	void optionalDecimalAllowsEmptyButNotJunk() {
		assertNull(FieldValidator.optionalDecimal("").value());
		assertTrue(FieldValidator.optionalDecimal("").ok());
		assertEquals(-170.5, FieldValidator.optionalDecimal("-170.5").value());
		assertFalse(FieldValidator.optionalDecimal("abc").ok());
		assertFalse(FieldValidator.optionalDecimal("NaN").ok());
	}

	@Test
	void keywordListsMustBeNonEmptyWhenRequired() {
		assertEquals(List.of("servers", "restart queue"), FieldValidator.words(" servers , restart queue ", true).value());
		assertEquals("Enter at least one word, separated by commas", FieldValidator.words("", true).error());
		assertFalse(FieldValidator.words(" , , ", true).ok());
		assertTrue(FieldValidator.words("", false).ok());
		assertTrue(FieldValidator.words("", false).value().isEmpty());
	}

	@Test
	void timePartsAcceptBlankAndRejectNegativeOrHuge() {
		assertEquals(0L, FieldValidator.timePart("", "Hours", 100).value());
		assertEquals(30L, FieldValidator.timePart("30", "Minutes", 59999).value());
		assertFalse(FieldValidator.timePart("-1", "Hours", 100).ok());
		assertFalse(FieldValidator.timePart("x", "Hours", 100).ok());
		assertFalse(FieldValidator.timePart("101", "Hours", 100).ok());
	}

	// ---- PanelLayout ----

	@Test
	void panelIsAboutSixtyPercentCappedAtFourTwentyAndNeverWiderThanTheScreen() {
		assertEquals(420, PanelLayout.panelWidth(1000));
		assertEquals(420, PanelLayout.panelWidth(2000));
		assertEquals(420, PanelLayout.panelWidth(700)); // 60% = 420
		assertEquals(360, PanelLayout.panelWidth(600)); // 60%
		assertEquals(300, PanelLayout.panelWidth(427)); // 60% would be 256: the floor keeps fields usable
		assertEquals(288, PanelLayout.panelWidth(300)); // narrower than the floor: screen minus the side margins
		assertEquals(188, PanelLayout.panelWidth(200));
		for (int sw = 60; sw <= 3000; sw += 7) {
			int w = PanelLayout.panelWidth(sw);
			assertTrue(w <= PanelLayout.MAX_WIDTH, "too wide at " + sw);
			assertTrue(w >= 60, "too narrow at " + sw);
			assertTrue(PanelLayout.panelX(sw, w) >= 0, "off screen at " + sw);
		}
	}

	// ---- RowScroll ----

	@Test
	void everythingFitsMeansNoScrolling() {
		int[] h = {20, 20, 20};
		assertEquals(0, RowScroll.maxFirst(h, 100));
		assertFalse(RowScroll.canScroll(h, 100));
		RowScroll.Layout l = RowScroll.layout(h, 100, 0);
		assertEquals(3, l.last());
		assertEquals(40, l.top()[2]);
	}

	@Test
	void onlyWholeRowsAreShownAndTheLastRowsCanBeReached() {
		int[] h = {20, 20, 20, 20, 20};
		assertTrue(RowScroll.canScroll(h, 50));
		RowScroll.Layout l = RowScroll.layout(h, 50, 0);
		assertEquals(2, l.last());
		assertEquals(-1, l.top()[2]);
		assertEquals(3, RowScroll.maxFirst(h, 50));
		RowScroll.Layout end = RowScroll.layout(h, 50, 3);
		assertEquals(5, end.last());
		assertEquals(3, RowScroll.clampFirst(99, h, 50));
		assertEquals(0, RowScroll.clampFirst(-4, h, 50));
	}

	@Test
	void aRowTallerThanTheViewportIsStillShown() {
		int[] h = {100, 20};
		RowScroll.Layout l = RowScroll.layout(h, 50, 0);
		assertEquals(0, l.top()[0]);
		assertEquals(1, l.last());
		assertEquals(0, RowScroll.layout(new int[0], 50, 0).last());
	}

	@Test
	void variableRowHeightsScrollByWholeRows() {
		int[] h = {10, 28, 10, 28, 10};
		assertEquals(2, RowScroll.maxFirst(h, 50)); // 10 + 28 + 10 = 48 fits from row 2
		RowScroll.Layout l = RowScroll.layout(h, 50, 1);
		assertEquals(0, l.top()[1]);
		assertEquals(28, l.top()[2]);
	}
}
