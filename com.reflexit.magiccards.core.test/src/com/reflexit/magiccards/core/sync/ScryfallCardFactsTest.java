/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *******************************************************************************/
package com.reflexit.magiccards.core.sync;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.utils.CardStoreUtils;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.test.assist.AbstractMagicTest;
import com.reflexit.unittesting.TestFileUtils;

/**
 * The card facts taken from Scryfall (reserved, reprint, game changer, EDHREC rank,
 * produced mana, official colors, booster) and the Scryfall ids merged into others
 * (migrations). Offline: a tiny fake bulk file is built on disk. Plain JUnit - but
 * it needs the database fixtures of the core test plug-in, like
 * {@code ScryfallBulkSplitTest}.
 */
public class ScryfallCardFactsTest extends AbstractMagicTest {

	@BeforeClass
	public static void setUpBeforeClass() {
		TestFileUtils.resetDb();
		DataManager.getInstance().waitForInit(10);
	}

	/** A parseRecord-safe single-face card; {@code extra} is spliced in before {@code scryfall_uri}. */
	private static String cardJson(String set, String name, String extra) {
		String id = name.replaceAll("\\s", "") + "-" + set;
		return "{\"id\":\"" + id + "\",\"lang\":\"en\",\"games\":[\"paper\"],\"layout\":\"normal\"," + "\"set\":\""
				+ set + "\",\"set_name\":\"" + set.toUpperCase() + " Set\",\"name\":\"" + name + "\","
				+ "\"mana_cost\":\"{G}\",\"type_line\":\"Creature\",\"rarity\":\"common\",\"artist\":\"Tester\","
				+ "\"collector_number\":\"1\",\"oracle_text\":\"Text\",\"full_art\":false,"
				+ "\"textless\":false,\"story_spotlight\":false,\"booster\":true,"
				+ "\"image_uris\":{\"normal\":\"https://example.com/1.jpg\"}," + extra
				+ "\"scryfall_uri\":\"https://scryfall.com/x\"}";
	}

	private static MagicCard parseOne(String set, String name, String extra) throws Exception {
		File f = File.createTempFile("facts-test-", ".jsonl.gz");
		f.deleteOnExit();
		try (PrintStream out = new PrintStream(new GZIPOutputStream(new FileOutputStream(f)), false, "UTF-8")) {
			out.println(cardJson(set, name, extra));
		}
		Map<String, List<MagicCard>> g = new ParseScryFallChecklist().parseBulkGrouped(f,
				Collections.singleton(set), ICoreProgressMonitor.NONE);
		Assert.assertEquals(1, g.get(set).size());
		return g.get(set).get(0);
	}

	// ------------------------------------------------------------ parsed facts

	@Test
	public void testFactsAreStored() throws Exception {
		MagicCard c = parseOne("fa1", "Fact Card", "\"reserved\":true,\"reprint\":true,\"game_changer\":true,"
				+ "\"edhrec_rank\":1234,\"produced_mana\":[\"G\",\"C\"],\"colors\":[\"G\"],");
		Assert.assertEquals("true", c.get(MagicCardField.RESERVED));
		Assert.assertEquals("true", c.get(MagicCardField.REPRINT));
		Assert.assertEquals("true", c.get(MagicCardField.GAME_CHANGER));
		Assert.assertEquals(Integer.valueOf(1234), c.get(MagicCardField.EDHREC_RANK));
		String produced = (String) c.get(MagicCardField.PRODUCED_MANA);
		Assert.assertTrue(produced, produced.contains("{G}") && produced.contains("{C}"));
	}

	@Test
	public void testNothingIsStoredWhenThereIsNothingToSay() throws Exception {
		MagicCard c = parseOne("fa2", "Plain Card",
				"\"reserved\":false,\"reprint\":false,\"game_changer\":false,\"produced_mana\":[],\"colors\":[\"G\"],");
		Assert.assertNull(c.get(MagicCardField.RESERVED));
		Assert.assertNull(c.get(MagicCardField.REPRINT));
		Assert.assertNull(c.get(MagicCardField.GAME_CHANGER));
		Assert.assertNull(c.get(MagicCardField.EDHREC_RANK));
		Assert.assertNull(c.get(MagicCardField.PRODUCED_MANA));
	}

	@Test
	public void testOfficialColorsWinOverTheCost() throws Exception {
		// mana cost {G}, but Scryfall says it is red (a color indicator) / colorless (devoid)
		Assert.assertEquals("{R}", parseOne("fa3", "Indicator", "\"colors\":[\"R\"],").get(MagicCardField.COLOR));
		Assert.assertEquals("{C}", parseOne("fa4", "Devoid", "\"colors\":[],").get(MagicCardField.COLOR));
	}

	@Test
	public void testNoOfficialColorsFallsBackToTheCost() throws Exception {
		Assert.assertEquals("{G}", parseOne("fa5", "No Colors", "").get(MagicCardField.COLOR));
	}

	@Test
	public void testBoosterLineOnlyForCardsNotInBoosters() throws Exception {
		String inBoosters = parseOne("fa6", "In Boosters", "").getText();
		Assert.assertFalse("a card found in boosters says nothing about it", inBoosters.contains("Booster:"));
		File f = File.createTempFile("facts-test-", ".jsonl.gz");
		f.deleteOnExit();
		try (PrintStream out = new PrintStream(new GZIPOutputStream(new FileOutputStream(f)), false, "UTF-8")) {
			out.println(cardJson("fa8", "No Boosters", "").replace("\"booster\":true", "\"booster\":false"));
		}
		MagicCard c = new ParseScryFallChecklist().parseBulkGrouped(f, Collections.singleton("fa8"),
				ICoreProgressMonitor.NONE).get("fa8").get(0);
		Assert.assertTrue(c.getText(), c.getText().contains("Booster: false<br>"));
	}

	@Test
	public void testTextlessIsNotTakenForBooster() throws Exception {
		File f = File.createTempFile("facts-test-", ".jsonl.gz");
		f.deleteOnExit();
		try (PrintStream out = new PrintStream(new GZIPOutputStream(new FileOutputStream(f)), false, "UTF-8")) {
			out.println(cardJson("fa9", "Textless", "").replace("\"textless\":false", "\"textless\":true")
					.replace("\"booster\":true", "\"booster\":false"));
		}
		MagicCard c = new ParseScryFallChecklist().parseBulkGrouped(f, Collections.singleton("fa9"),
				ICoreProgressMonitor.NONE).get("fa9").get(0);
		Assert.assertTrue(c.getText(), c.getText().contains("Traits: Textless<br>")); // textless is a trait now
		Assert.assertTrue("textless is not booster, and a card outside boosters says so", c.getText().contains("Booster: false<br>"));
	}

	// ------------------------------------------------- traits, text, oracle id

	@Test
	public void testTraitsAreOneListInFixedOrder() throws Exception {
		MagicCard c = parseOne("fc1", "Trait Card", "\"reserved\":true,\"reprint\":true,\"game_changer\":true,");
		Assert.assertEquals("Reserved List, Game Changer, Reprint", c.get(MagicCardField.TRAITS));
		MagicCard one = parseOne("fc2", "One Trait", "\"reprint\":true,");
		Assert.assertEquals("Reprint", one.get(MagicCardField.TRAITS));
		Assert.assertNull(parseOne("fc3", "No Trait", "").get(MagicCardField.TRAITS));
	}

	@Test
	public void testFactsAreLinesOfTheTextUnderTheFinishes() throws Exception {
		MagicCard c = parseOne("fc4", "Text Card", "\"finishes\":[\"nonfoil\",\"foil\"],\"reserved\":true,"
				+ "\"edhrec_rank\":77,\"produced_mana\":[\"G\"],");
		String text = c.getText();
		int finishes = text.indexOf("Finishes: Nonfoil, Foil<br>");
		int traits = text.indexOf("Traits: Reserved List<br>");
		int produces = text.indexOf("Produces: {G}<br>");
		Assert.assertTrue(text, finishes >= 0 && traits > finishes && produces > traits);
		Assert.assertFalse("the rank is a column, not a line of the text", text.contains("EDHREC"));
		// a card with nothing to say gets no such lines
		String plain = parseOne("fc5", "Plain Text Card", "\"finishes\":[\"nonfoil\"],").getText();
		Assert.assertFalse(plain, plain.contains("Traits:") || plain.contains("EDHREC") || plain.contains("Produces:"));
	}

	@Test
	public void testEdhrecRankSortsAsANumberWithUnrankedLast() throws Exception {
		MagicCard r9 = parseOne("fe1", "Rank Nine", "\"edhrec_rank\":9,");
		MagicCard r100 = parseOne("fe2", "Rank Hundred", "\"edhrec_rank\":100,");
		MagicCard none = parseOne("fe3", "Unranked", "");
		java.util.List<MagicCard> l = new java.util.ArrayList<MagicCard>(java.util.Arrays.asList(none, r100, r9));
		java.util.Collections.sort(l, new com.reflexit.magiccards.core.model.MagicCardComparator(MagicCardField.EDHREC_RANK, true));
		Assert.assertEquals("9 before 100 (a number, not text), unranked last",
				java.util.Arrays.asList(r9, r100, none), l);
	}

	@Test
	public void testExtendedIdentityIsOfficialPlusFetchedColors() throws Exception {
		// a fetch land: official identity is colorless, but it fetches a Plains or an Island
		MagicCard fetch = parseOne("ff1", "Fake Strand", "\"color_identity\":[],\"mana_cost\":\"\","
				+ "\"oracle_text\":\"{T}, Pay 1 life, Sacrifice Fake Strand: Search your library for a Plains or Island card.\",");
		Assert.assertEquals("{C}", fetch.get(MagicCardField.COLOR_IDENTITY));
		String ext = (String) fetch.get(MagicCardField.COLOR_IDENTITY_EXTENDED);
		Assert.assertTrue(ext, ext.contains("{W}") && ext.contains("{U}") && !ext.contains("{C}"));
		// Crimson Kobolds: cost {0}, no text, red by Scryfall's own word
		MagicCard kobolds = parseOne("ff2", "Fake Kobolds", "\"color_identity\":[\"R\"],\"colors\":[\"R\"],"
				+ "\"mana_cost\":\"{0}\",\"oracle_text\":\"\",");
		Assert.assertEquals("{R}", kobolds.get(MagicCardField.COLOR_IDENTITY_EXTENDED));
		// Extort: the {W/B} in the reminder text counts for the extended identity (not the official one)
		MagicCard extort = parseOne("ff3", "Fake Syndic", "\"color_identity\":[\"W\"],\"colors\":[\"W\"],"
				+ "\"mana_cost\":\"{1}{W}\",\"oracle_text\":\"Extort ({W/B}, pay: ...)\",");
		Assert.assertEquals("{W}", extort.get(MagicCardField.COLOR_IDENTITY));
		String ee = (String) extort.get(MagicCardField.COLOR_IDENTITY_EXTENDED);
		Assert.assertTrue(ee, ee.contains("{W}") && ee.contains("{B}"));
		// no identity from Scryfall: the old oracle-text guess is only the fallback
		MagicCard unknown = parseOne("ff4", "Fake Unknown", "\"mana_cost\":\"{G}\",\"oracle_text\":\"Text\",");
		Assert.assertNotNull(unknown.get(MagicCardField.COLOR_IDENTITY_EXTENDED));
	}

	@Test
	public void testBuildFactsText() {
		Assert.assertEquals("", ParseScryFallChecklist.buildFactsText(false, false, false, null, null));
		Assert.assertEquals("Traits: Game Changer<br>",
				ParseScryFallChecklist.buildFactsText(false, true, false, null, null));
		Assert.assertEquals("Traits: Reserved List, Reprint<br>Produces: {C}<br>",
				ParseScryFallChecklist.buildFactsText(true, false, true, null, "{C}"));
		// the treatments come after the three own-field facts, in the same list
		Assert.assertEquals("Traits: Reserved List, Showcase, Borderless<br>", ParseScryFallChecklist
				.buildFactsText(true, false, false, java.util.Arrays.asList("Showcase", "Borderless"), null));
	}

	private static JSONObject json(String s) throws Exception {
		return (JSONObject) new JSONParser().parse(s);
	}

	@Test
	public void testExtraTraitLabels() throws Exception {
		Assert.assertTrue(ParseScryFallChecklist.extraTraitLabels(json("{\"frame\":\"2015\",\"border_color\":\"black\"}"), null).isEmpty());
		JSONObject all = json("{\"frame\":\"1993\",\"border_color\":\"white\",\"frame_effects\":[\"legendary\",\"showcase\",\"extendedart\"],"
				+ "\"full_art\":true,\"textless\":true,\"story_spotlight\":true,\"security_stamp\":\"acorn\",\"variation\":true,"
				+ "\"oversized\":true,\"watermark\":\"selesnya\",\"content_warning\":true}");
		Assert.assertEquals(java.util.Arrays.asList("1993 Frame", "White Border", "Showcase", "Extended Art", "Full Art",
				"Textless", "Story Spotlight", "Acorn Stamp", "Variation", "Oversized", "Watermark: Selesnya",
				"Content Warning"), ParseScryFallChecklist.extraTraitLabels(all, null));
		Assert.assertEquals(java.util.Arrays.asList("Future Frame", "Borderless"), ParseScryFallChecklist
				.extraTraitLabels(json("{\"frame\":\"future\",\"border_color\":\"borderless\"}"), null));
		// only the looks that say something: "legendary", "devoid", "snow"... frame effects are not traits
		Assert.assertTrue(ParseScryFallChecklist
				.extraTraitLabels(json("{\"frame_effects\":[\"legendary\",\"devoid\",\"snow\"]}"), null).isEmpty());
	}

	@Test
	public void testPromoTypesAreTraitsNotAPromosLine() throws Exception {
		Assert.assertEquals(java.util.Arrays.asList("Prerelease", "Surge Foil"), ParseScryFallChecklist
				.extraTraitLabels(json("{\"promo_types\":[\"boosterfun\",\"prerelease\",\"surgefoil\"]}"), null));
		MagicCard c = parseOne("fh1", "Promo Card", "\"finishes\":[\"nonfoil\"],\"promo_types\":[\"playtest\"],");
		Assert.assertEquals("Playtest", c.get(MagicCardField.TRAITS));
		Assert.assertTrue(c.getText(), c.getText().contains("Traits: Playtest<br>"));
		Assert.assertFalse("the old raw Promos line is gone", c.getText().contains("Promos:"));
	}

	@Test
	public void testAFaceCanHaveItsOwnWatermark() throws Exception {
		JSONObject elem = json("{\"frame\":\"2015\"}");
		JSONObject front = json("{\"watermark\":\"orzhov\"}");
		JSONObject back = json("{}");
		Assert.assertEquals(java.util.Arrays.asList("Watermark: Orzhov"), ParseScryFallChecklist.extraTraitLabels(elem, front));
		Assert.assertTrue(ParseScryFallChecklist.extraTraitLabels(elem, back).isEmpty());
	}

	@Test
	public void testTreatmentsEndUpInTheTraitsAndTheTextNotInOldLines() throws Exception {
		MagicCard c = parseOne("fg1", "Pretty Card", "\"finishes\":[\"nonfoil\"],\"border_color\":\"borderless\","
				+ "\"frame_effects\":[\"showcase\"],\"full_art\":true,\"watermark\":\"izzet\",\"reserved\":true,"
				+ "\"flavor_text\":\"A line.\",\"flavor_name\":\"Other Name\",");
		Assert.assertEquals("Reserved List, Borderless, Showcase, Full Art, Watermark: Izzet",
				c.get(MagicCardField.TRAITS));
		String text = c.getText();
		Assert.assertTrue(text, text.contains("Traits: Reserved List, Borderless, Showcase, Full Art, Watermark: Izzet<br>"));
		Assert.assertFalse("no flavor lines", text.contains("Flavor"));
		Assert.assertFalse("full art moved into the traits", text.contains("FullArt:"));
		Assert.assertFalse(text, text.contains("TextLess:") || text.contains("StorySpotlight:"));
	}

	@Test
	public void testOracleId() throws Exception {
		Assert.assertEquals("abc", parseOne("fc6", "Oracle Card", "\"oracle_id\":\"abc\",").get(MagicCardField.ORACLE_ID));
		Assert.assertNull(parseOne("fc7", "No Oracle", "").get(MagicCardField.ORACLE_ID));
		JSONObject face = (JSONObject) new JSONParser().parse("{\"oracle_id\":\"face-id\"}");
		JSONObject withTop = (JSONObject) new JSONParser().parse("{\"oracle_id\":\"top-id\"}");
		JSONObject none = (JSONObject) new JSONParser().parse("{}");
		Assert.assertEquals("top-id", ParseScryFallChecklist.oracleIdOf(face, withTop));
		Assert.assertEquals("face-id", ParseScryFallChecklist.oracleIdOf(face, none)); // multi-faced: the face's
		Assert.assertNull(ParseScryFallChecklist.oracleIdOf(null, none));
	}

	@Test
	public void testPrintingsAreFoundByOracleIdNotByName() throws Exception {
		MagicCard a = parseOne("fd1", "Old Name", "\"oracle_id\":\"same\",");
		MagicCard b = parseOne("fd2", "Renamed", "\"oracle_id\":\"same\",");
		MagicCard other = parseOne("fd3", "Old Name", "\"oracle_id\":\"other\","); // same name, a different card
		MagicCard unknown = parseOne("fd4", "Old Name", "");
		java.util.List<MagicCard> all = java.util.Arrays.asList(a, b, other, unknown);
		java.util.List<com.reflexit.magiccards.core.model.IMagicCard> p = CardStoreUtils.printingsByOracleId(all, "same");
		Assert.assertEquals(2, p.size());
		Assert.assertTrue(p.contains(a) && p.contains(b));
		Assert.assertFalse(p.contains(other));
		Assert.assertTrue(CardStoreUtils.printingsByOracleId(all, null).isEmpty());
		Assert.assertTrue(CardStoreUtils.printingsByOracleId(all, "nobody").isEmpty());
	}

	// ----------------------------------------------------------------- helpers

	@Test
	public void testHelpers() throws Exception {
		Assert.assertEquals("true", ParseScryFallChecklist.flagString(Boolean.TRUE));
		Assert.assertNull(ParseScryFallChecklist.flagString(Boolean.FALSE));
		Assert.assertNull(ParseScryFallChecklist.flagString(null));
		Assert.assertNull(ParseScryFallChecklist.manaCostString(null));
		Assert.assertNull(ParseScryFallChecklist.manaCostString(new JSONArray()));
		JSONObject face = (JSONObject) new JSONParser().parse("{\"colors\":[\"U\"]}");
		JSONObject elem = (JSONObject) new JSONParser().parse("{\"colors\":[\"R\"]}");
		JSONObject none = (JSONObject) new JSONParser().parse("{}");
		Assert.assertEquals("{U}", ParseScryFallChecklist.officialColorCost(face, elem)); // the face's own
		Assert.assertEquals("{R}", ParseScryFallChecklist.officialColorCost(none, elem)); // else the card's
		Assert.assertEquals("{R}", ParseScryFallChecklist.officialColorCost(null, elem));
		Assert.assertNull(ParseScryFallChecklist.officialColorCost(null, none)); // nothing: fall back
	}

	// ------------------------------------------------------------- deck facts

	@Test
	public void testGameChangersAndManaSources() throws Exception {
		MagicCard a = parseOne("fb1", "Cyclonic Rift", "\"game_changer\":true,");
		MagicCard b = parseOne("fb2", "Sol Ring", "\"produced_mana\":[\"C\"],");
		MagicCard c = parseOne("fb3", "Command Tower", "\"produced_mana\":[\"W\",\"U\",\"G\"],");
		MagicCard d = parseOne("fb4", "Vanilla", "");
		java.util.List<MagicCard> cards = java.util.Arrays.asList(a, b, c, d);
		Map<String, Integer> changers = CardStoreUtils.buildGameChangers(cards);
		Assert.assertEquals(1, changers.size());
		Assert.assertEquals(Integer.valueOf(1), changers.get("Cyclonic Rift"));
		Map<String, Integer> sources = new HashMap<String, Integer>(CardStoreUtils.buildManaSources(cards));
		Assert.assertEquals(Integer.valueOf(1), sources.get("W"));
		Assert.assertEquals(Integer.valueOf(1), sources.get("U"));
		Assert.assertEquals(Integer.valueOf(1), sources.get("G"));
		Assert.assertEquals(Integer.valueOf(1), sources.get("C"));
		Assert.assertEquals(Integer.valueOf(0), sources.get("B"));
		Assert.assertEquals(Integer.valueOf(0), sources.get("R"));
	}

	// -------------------------------------------------------------- migrations

	@Test
	public void testMigrationsKeepOnlyMerges() {
		Map<String, String> into = new HashMap<String, String>();
		String next = ScryfallMigrations.parsePage("{\"has_more\":true,\"next_page\":\"https://x/p2\",\"data\":["
				+ "{\"migration_strategy\":\"merge\",\"old_scryfall_id\":\"old1\",\"new_scryfall_id\":\"new1\"},"
				+ "{\"migration_strategy\":\"delete\",\"old_scryfall_id\":\"gone\",\"new_scryfall_id\":null},"
				+ "{\"migration_strategy\":\"merge\",\"old_scryfall_id\":\"same\",\"new_scryfall_id\":\"same\"}]}", into);
		Assert.assertEquals("https://x/p2", next);
		Assert.assertEquals(1, into.size());
		Assert.assertEquals("new1", into.get("old1"));
		Assert.assertNull(ScryfallMigrations.parsePage("{\"has_more\":false,\"data\":[]}", into));
		Assert.assertNull(ScryfallMigrations.parsePage("not json", into));
	}

	@Test
	public void testMigrationsFollowAChainAndStopOnACycle() {
		Map<String, String> m = new HashMap<String, String>();
		m.put("a", "b");
		m.put("b", "c");
		Assert.assertEquals("c", ScryfallMigrations.resolve(m, "a"));
		Assert.assertEquals("c", ScryfallMigrations.resolve(m, "b"));
		Assert.assertNull(ScryfallMigrations.resolve(m, "c"));
		m.put("c", "a"); // a cycle: do not guess
		Assert.assertNull(ScryfallMigrations.resolve(m, "a"));
	}

	@Test
	public void testMigrationsFileRoundTrip() throws IOException {
		File f = File.createTempFile("migrations-test-", ".tsv");
		f.deleteOnExit();
		Map<String, String> m = new HashMap<String, String>();
		m.put("old-1", "new-1");
		m.put("old-2", "new-2");
		Assert.assertTrue(ScryfallMigrations.write(f, m));
		Assert.assertFalse("same content: nothing rewritten", ScryfallMigrations.write(f, m));
		Assert.assertEquals(m, ScryfallMigrations.read(f));
		Assert.assertTrue(ScryfallMigrations.read(new File(f.getParentFile(), "no-such-file.tsv")).isEmpty());
	}
}
