package dev.afkmod.logic;

import dev.afkmod.logic.KeywordMatcher.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeywordMatcherTest {
	private static final List<String> RESTART = List.of("servers");
	private static final List<String> IGNORE = List.of("whispered");
	private static final List<String> NO_END = List.of();

	private static Result classify(String message) {
		return KeywordMatcher.classify(message, RESTART, IGNORE, NO_END);
	}

	@Test
	void matchesRestartKeywordCaseInsensitively() {
		assertEquals(Result.RESTART, classify("Servers are restarting in 10 seconds"));
		assertEquals(Result.RESTART, classify("ALL SERVERS RESTARTING"));
		assertEquals(Result.RESTART, classify("restarting the servers now"));
	}

	@Test
	void substringMatch() {
		assertEquals(Result.RESTART, classify("gameservers rebooting"));
	}

	@Test
	void stripsFormattingCodes() {
		assertEquals(Result.RESTART, classify("§c§lSer§rvers §eare restarting"));
		assertEquals(Result.RESTART, classify("§x§f§f§0§0§0§0Servers restarting"));
		assertEquals("Servers restarting", KeywordMatcher.stripFormatting("§6Servers §Lrestarting§r"));
	}

	@Test
	void whisperedIsIgnoredEvenIfItContainsServers() {
		assertEquals(Result.IGNORED, classify("Steve whispered to you: the servers are restarting lol"));
		assertEquals(Result.IGNORED, classify("§7Steve §oWHISPERED§r: servers"));
	}

	@Test
	void ignoreRuleAppliesWithoutRestartKeywordToo() {
		assertEquals(Result.IGNORED, classify("Alex whispered to you: hi"));
	}

	@Test
	void unrelatedMessageIsNone() {
		assertEquals(Result.NONE, classify("You found a diamond!"));
		assertEquals(Result.NONE, classify(""));
		assertEquals(Result.NONE, classify(null));
	}

	@Test
	void endKeywordBeatsRestartKeywordButNotIgnore() {
		List<String> end = List.of("back online");
		assertEquals(Result.RESTART_END, KeywordMatcher.classify("Servers are back online!", RESTART, IGNORE, end));
		assertEquals(Result.IGNORED, KeywordMatcher.classify("x whispered: servers back online", RESTART, IGNORE, end));
		assertEquals(Result.RESTART, KeywordMatcher.classify("Servers restarting", RESTART, IGNORE, end));
	}

	@Test
	void blankKeywordsNeverMatch() {
		assertEquals(Result.NONE, KeywordMatcher.classify("anything", List.of("", "  "), List.of(" "), List.of()));
	}

	@Test
	void multipleKeywords() {
		List<String> restart = List.of("servers", "Restart");
		assertEquals(Result.RESTART, KeywordMatcher.classify("Network restart soon", restart, IGNORE, NO_END));
	}

	@Test
	void parseAndJoinList() {
		assertEquals(List.of("servers", "restart now"), KeywordMatcher.parseList(" servers, ,restart now ,"));
		assertEquals(List.of(), KeywordMatcher.parseList(""));
		assertEquals(List.of(), KeywordMatcher.parseList(null));
		assertEquals("servers, whispered", KeywordMatcher.joinList(List.of("servers", "whispered")));
	}
}
