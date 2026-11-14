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
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.test.assist.AbstractMagicTest;
import com.reflexit.unittesting.TestFileUtils;

/**
 * Characterization tests of how {@link ParseScryFallChecklist} turns a Scryfall card object
 * into one or two {@link MagicCard}s (the "face processing": single card, one face = two
 * cards, two faces = two cards, per-face vs whole-card fields). They pin down today's
 * behaviour for every layout so the face logic can be reworked without changing its output.
 * Offline: a tiny fake bulk file is built on disk. Plain JUnit, but it needs the database
 * fixtures of the core test plug-in, like {@code ScryfallCardFactsTest}.
 */
public class ScryfallFacesTest extends AbstractMagicTest {
	private static final String IMG_CARD = "https://example.com/card.jpg";
	private static final String IMG_FRONT = "https://example.com/front.jpg";
	private static final String IMG_BACK = "https://example.com/back.jpg";

	/** Layouts Scryfall gives to a card made of one face. */
	private static final String[] SINGLE_LAYOUTS = { "normal", "meld", "leveler", "class", "case", "saga", "mutate",
			"prototype", "planar", "vanguard", "augment", "host", "token", "emblem", "scheme", "art_series" };
	/** Two faces printed on one card face: the images are the card's. */
	private static final String[] SHARED_IMAGE_LAYOUTS = { "split", "flip", "adventure", "prepare" };
	/** Two physical faces: each face has its own images. */
	private static final String[] FACE_IMAGE_LAYOUTS = { "transform", "modal_dfc", "battle", "double_faced_token",
			"reversible_card" };
	/** Layouts that can be one card or two, depending on card_faces. */
	private static final String[] DEPENDS_LAYOUTS = { "token", "emblem", "scheme", "art_series" };

	private static int counter;

	@BeforeClass
	public static void setUpBeforeClass() {
		TestFileUtils.resetDb();
		DataManager.getInstance().waitForInit(10);
	}

	// ------------------------------------------------------------ builders

	@SuppressWarnings("unchecked")
	private static JSONObject obj(Object... kv) {
		JSONObject o = new JSONObject();
		for (int i = 0; i < kv.length; i += 2)
			o.put(kv[i], kv[i + 1]);
		return o;
	}

	@SuppressWarnings("unchecked")
	private static JSONArray arr(Object... items) {
		JSONArray a = new JSONArray();
		a.addAll(Arrays.asList(items));
		return a;
	}

	private static JSONObject images(String url) {
		return obj("normal", url);
	}

	/** A whole-card object; every field {@code parseRecord} needs is there, faces are added by the caller. */
	@SuppressWarnings("unchecked")
	private static JSONObject card(String layout, String name, String set) {
		JSONObject c = obj("id", name.replaceAll("\\W", "") + "-" + set, "lang", "en", "games", arr("paper"),
				"layout", layout, "set", set, "set_name", set.toUpperCase() + " Set", "name", name, "rarity", "rare",
				"collector_number", "7", "scryfall_uri", "https://scryfall.com/x", "finishes", arr("nonfoil", "foil"),
				"legalities", obj("modern", "legal", "standard", "banned"));
		c.put("mana_cost", "{1}{G}");
		c.put("type_line", "Creature — Elf");
		c.put("oracle_text", "Whole text");
		c.put("artist", "Tester");
		return c;
	}

	private static JSONObject face(String name, String cost, String type) {
		JSONObject f = obj("name", name, "type_line", type, "oracle_text", name + " text", "artist", name + " artist");
		if (cost != null)
			f.put("mana_cost", cost);
		return f;
	}

	/** A two-faced card whose images are on the card (split, adventure...). */
	@SuppressWarnings("unchecked")
	private static JSONObject sharedImageCard(String layout, String set) {
		JSONObject c = card(layout, "Front // Back", set);
		c.put("image_uris", images(IMG_CARD));
		c.put("card_faces", arr(face("Front", "{R}", "Instant"), face("Back", "{1}{U}", "Sorcery")));
		return c;
	}

	/** A two-faced card whose images are on each face (transform, modal DFC...). */
	@SuppressWarnings("unchecked")
	private static JSONObject faceImageCard(String layout, String set) {
		JSONObject c = card(layout, "Front // Back", set);
		c.remove("mana_cost");
		c.remove("oracle_text");
		JSONObject f = face("Front", "{R}", "Creature — Human");
		f.put("image_uris", images(IMG_FRONT));
		JSONObject b = face("Back", "", "Creature — Werewolf");
		b.put("image_uris", images(IMG_BACK));
		c.put("card_faces", arr(f, b));
		return c;
	}

	private static List<MagicCard> parse(String set, JSONObject... records) throws Exception {
		File f = File.createTempFile("faces-test-", ".jsonl.gz");
		f.deleteOnExit();
		try (PrintStream out = new PrintStream(new GZIPOutputStream(new FileOutputStream(f)), false, "UTF-8")) {
			for (JSONObject r : records)
				out.println(r.toJSONString());
		}
		return new ParseScryFallChecklist().parseBulkGrouped(f, Collections.singleton(set), ICoreProgressMonitor.NONE)
				.get(set);
	}

	private static String nextSet() {
		return "fx" + (++counter);
	}

	// ------------------------------------------------------------ one card

	@Test
	public void testSingleFacedLayoutsMakeOneCard() throws Exception {
		for (String layout : SINGLE_LAYOUTS) {
			String set = nextSet();
			JSONObject c = card(layout, "Solo", set);
			c.put("image_uris", images(IMG_CARD));
			List<MagicCard> r = parse(set, c);
			Assert.assertEquals(layout, 1, r.size());
			Assert.assertEquals(layout, "Solo", r.get(0).getName());
		}
	}

	@Test
	public void testUnknownLayoutWithoutFacesIsASingleCard() throws Exception {
		String set = nextSet();
		JSONObject c = card("some_future_layout", "Solo", set);
		c.put("image_uris", images(IMG_CARD));
		Assert.assertEquals(1, parse(set, c).size());
	}

	@Test
	public void testSingleCardFields() throws Exception {
		String set = nextSet();
		JSONObject c = card("normal", "Solo", set);
		c.put("image_uris", images(IMG_CARD));
		c.put("oracle_text", "Flying | Haste");
		c.put("power", "2");
		c.put("toughness", "3");
		MagicCard m = parse(set, c).get(0);
		Assert.assertEquals("Solo", m.getName());
		Assert.assertEquals(set.toUpperCase() + " Set", m.getSet());
		Assert.assertEquals("{1}{G}", m.getCost());
		Assert.assertEquals("the em dash of the type line is replaced", "Creature - Elf", m.getType());
		Assert.assertEquals("2", m.getPower());
		Assert.assertEquals("3", m.getToughness());
		Assert.assertEquals("the bar is a field separator of the storage", "Flying &vert; Haste", m.getOracleText());
		Assert.assertEquals("Tester", m.getArtist());
		Assert.assertEquals("7", m.getCollNumber());
		Assert.assertEquals(IMG_CARD, m.get(MagicCardField.IMAGE_URL));
		Assert.assertNull("a single card has nothing to flip to", m.get(MagicCardField.FLIPID));
	}

	@Test
	public void testSingleCardWithoutPowerOrToughnessHasEmptyOnes() throws Exception {
		String set = nextSet();
		JSONObject c = card("normal", "Solo", set);
		c.put("image_uris", images(IMG_CARD));
		MagicCard m = parse(set, c).get(0);
		Assert.assertEquals("", m.getPower());
		Assert.assertEquals("", m.getToughness());
	}

	@Test
	public void testSingleCardWithOneFaceTakesItsImageFromTheFace() throws Exception {
		// a token: no card level image_uris, a single entry in card_faces that carries the image
		String set = nextSet();
		JSONObject c = card("token", "Solo", set);
		c.put("image_uris", obj());
		JSONObject f = face("Solo", "{G}", "Creature");
		f.put("image_uris", images(IMG_FRONT));
		c.put("card_faces", arr(f));
		List<MagicCard> r = parse(set, c);
		Assert.assertEquals("one face is one card", 1, r.size());
		Assert.assertEquals(IMG_FRONT, r.get(0).get(MagicCardField.IMAGE_URL));
		Assert.assertEquals("{G}", r.get(0).getCost());
	}

	@Test
	public void testErrorImageIsIgnored() throws Exception {
		String set = nextSet();
		JSONObject c = card("normal", "Solo", set);
		c.put("image_uris", images("https://errors.scryfall.com/soon.jpg"));
		Assert.assertNull(parse(set, c).get(0).get(MagicCardField.IMAGE_URL));
	}

	// ------------------------------------------------------------ two cards, images on the card

	@Test
	public void testSharedImageLayoutsMakeTwoCardsWithTheSameImage() throws Exception {
		for (String layout : SHARED_IMAGE_LAYOUTS) {
			String set = nextSet();
			List<MagicCard> r = parse(set, sharedImageCard(layout, set));
			Assert.assertEquals(layout, 2, r.size());
			Assert.assertEquals(layout, IMG_CARD, r.get(0).get(MagicCardField.IMAGE_URL));
			Assert.assertEquals(layout, IMG_CARD, r.get(1).get(MagicCardField.IMAGE_URL));
		}
	}

	@Test
	public void testSharedImageFacesKeepTheirOwnStats() throws Exception {
		String set = nextSet();
		List<MagicCard> r = parse(set, sharedImageCard("adventure", set));
		MagicCard front = r.get(0);
		MagicCard back = r.get(1);
		Assert.assertEquals("{R}", front.getCost());
		Assert.assertEquals("{1}{U}", back.getCost());
		Assert.assertEquals("Instant", front.getType());
		Assert.assertEquals("Sorcery", back.getType());
		Assert.assertEquals("Front text", front.getOracleText());
		Assert.assertEquals("Back text", back.getOracleText());
		Assert.assertEquals("Front artist", front.getArtist());
		Assert.assertEquals("Back artist", back.getArtist());
	}

	// ------------------------------------------------------------ two cards, images on the faces

	@Test
	public void testFaceImageLayoutsMakeTwoCardsWithTheirOwnImage() throws Exception {
		for (String layout : FACE_IMAGE_LAYOUTS) {
			String set = nextSet();
			List<MagicCard> r = parse(set, faceImageCard(layout, set));
			Assert.assertEquals(layout, 2, r.size());
			Assert.assertEquals(layout, IMG_FRONT, r.get(0).get(MagicCardField.IMAGE_URL));
			Assert.assertEquals(layout, IMG_BACK, r.get(1).get(MagicCardField.IMAGE_URL));
		}
	}

	@Test
	public void testTokenLikeLayoutsWithTwoFacesMakeTwoCards() throws Exception {
		for (String layout : DEPENDS_LAYOUTS) {
			String set = nextSet();
			List<MagicCard> r = parse(set, faceImageCard(layout, set));
			Assert.assertEquals(layout, 2, r.size());
			Assert.assertEquals(layout, IMG_FRONT, r.get(0).get(MagicCardField.IMAGE_URL));
			Assert.assertEquals(layout, IMG_BACK, r.get(1).get(MagicCardField.IMAGE_URL));
		}
	}

	@Test
	public void testFaceImageBackFaceWithoutAnyOptionalFields() throws Exception {
		String set = nextSet();
		JSONObject c = faceImageCard("transform", set);
		JSONObject back = (JSONObject) ((JSONArray) c.get("card_faces")).get(1);
		back.remove("type_line");
		back.remove("oracle_text");
		back.remove("artist");
		List<MagicCard> r = parse(set, c);
		Assert.assertEquals("", r.get(1).getType());
		Assert.assertEquals("", r.get(1).getOracleText());
		Assert.assertEquals("", r.get(1).getArtist());
		Assert.assertEquals("", r.get(1).getPower());
		Assert.assertEquals("", r.get(1).getToughness());
	}

	@Test
	public void testFacePowerAndToughnessAreEachFaces() throws Exception {
		String set = nextSet();
		JSONObject c = faceImageCard("transform", set);
		JSONArray faces = (JSONArray) c.get("card_faces");
		((JSONObject) faces.get(0)).put("power", "1");
		((JSONObject) faces.get(0)).put("toughness", "2");
		((JSONObject) faces.get(1)).put("power", "4");
		((JSONObject) faces.get(1)).put("toughness", "5");
		List<MagicCard> r = parse(set, c);
		Assert.assertEquals("1", r.get(0).getPower());
		Assert.assertEquals("2", r.get(0).getToughness());
		Assert.assertEquals("4", r.get(1).getPower());
		Assert.assertEquals("5", r.get(1).getToughness());
	}

	// ------------------------------------------------------------ the pair of cards

	@Test
	public void testBackCardIdentity() throws Exception {
		String set = nextSet();
		JSONObject c = faceImageCard("transform", set);
		List<MagicCard> r = parse(set, c);
		MagicCard front = r.get(0);
		MagicCard back = r.get(1);
		Assert.assertEquals("the whole card's name", "Front // Back", front.getName());
		Assert.assertEquals("name and layout", "Front // Back (transform)", back.getName());
		Assert.assertEquals(String.valueOf(c.get("id")), String.valueOf(front.getCardId()));
		Assert.assertEquals("-" + c.get("id"), String.valueOf(back.getCardId()));
		Assert.assertEquals("7", front.getCollNumber());
		Assert.assertEquals("7b", back.getCollNumber());
		Assert.assertEquals(front.getSet(), back.getSet());
		Assert.assertEquals(front.getRarity(), back.getRarity());
	}

	@Test
	public void testFacesPointToEachOther() throws Exception {
		String set = nextSet();
		List<MagicCard> r = parse(set, sharedImageCard("split", set));
		Assert.assertEquals(String.valueOf(r.get(1).getCardId()), String.valueOf(r.get(0).get(MagicCardField.FLIPID)));
		Assert.assertEquals(String.valueOf(r.get(0).getCardId()), String.valueOf(r.get(1).get(MagicCardField.FLIPID)));
	}

	@Test
	public void testCardLevelFactsAreSharedByBothFaces() throws Exception {
		String set = nextSet();
		JSONObject c = faceImageCard("modal_dfc", set);
		c.put("keywords", arr("Flying", "Haste"));
		c.put("reserved", Boolean.TRUE);
		c.put("color_identity", arr("R", "U"));
		c.put("oracle_id", "oid-1");
		List<MagicCard> r = parse(set, c);
		for (MagicCard m : r) {
			Assert.assertEquals("Flying|Haste", m.get(MagicCardField.KEYWORDS));
			Assert.assertEquals("true", m.get(MagicCardField.RESERVED));
			Assert.assertEquals("canonical W-U-B-R-G order", "{U}{R}", m.get(MagicCardField.COLOR_IDENTITY));
			Assert.assertEquals("oid-1", m.get(MagicCardField.ORACLE_ID));
			Assert.assertEquals("nonfoil,foil", m.get(MagicCardField.FINISHES));
		}
		Assert.assertEquals(r.get(0).get(MagicCardField.LEGALITY), r.get(1).get(MagicCardField.LEGALITY));
		Assert.assertNotNull(r.get(0).get(MagicCardField.LEGALITY));
	}

	@Test
	public void testOracleIdFallsBackToEachFace() throws Exception {
		String set = nextSet();
		JSONObject c = faceImageCard("transform", set);
		JSONArray faces = (JSONArray) c.get("card_faces");
		((JSONObject) faces.get(0)).put("oracle_id", "face-a");
		((JSONObject) faces.get(1)).put("oracle_id", "face-b");
		List<MagicCard> r = parse(set, c);
		Assert.assertEquals("face-a", r.get(0).get(MagicCardField.ORACLE_ID));
		Assert.assertEquals("face-b", r.get(1).get(MagicCardField.ORACLE_ID));
	}

	@Test
	public void testColorsAreEachFacesOwnElseTheCards() throws Exception {
		String set = nextSet();
		JSONObject c = faceImageCard("transform", set);
		JSONArray faces = (JSONArray) c.get("card_faces");
		((JSONObject) faces.get(0)).put("colors", arr("R"));
		// the back face has no colors of its own: the card's
		c.put("colors", arr("G"));
		List<MagicCard> r = parse(set, c);
		Assert.assertEquals("{R}", r.get(0).get(MagicCardField.COLOR));
		Assert.assertEquals("{G}", r.get(1).get(MagicCardField.COLOR));
	}

	@Test
	public void testWatermarkIsPerFace() throws Exception {
		String set = nextSet();
		JSONObject c = faceImageCard("transform", set);
		((JSONObject) ((JSONArray) c.get("card_faces")).get(0)).put("watermark", "orzhov");
		List<MagicCard> r = parse(set, c);
		Assert.assertTrue(String.valueOf(r.get(0).get(MagicCardField.TRAITS)),
				String.valueOf(r.get(0).get(MagicCardField.TRAITS)).contains("Orzhov"));
		Assert.assertNull(r.get(1).get(MagicCardField.TRAITS));
	}

	// ------------------------------------------------------------ text and ids

	@Test
	public void testMultiverseIdsLineOfTwoFaces() throws Exception {
		String set = nextSet();
		JSONObject c = faceImageCard("transform", set);
		c.put("multiverse_ids", arr(Long.valueOf(111), Long.valueOf(222)));
		List<MagicCard> r = parse(set, c);
		Assert.assertTrue(r.get(0).getText(), r.get(0).getText().contains("MID: 111 // 222<br>"));
		Assert.assertTrue(r.get(1).getText(), r.get(1).getText().contains("MID: 222 // 111<br>"));
		Assert.assertEquals("the Gatherer id is the first one", "111", r.get(0).getGathererCardId());
	}

	@Test
	public void testMultiverseIdLineOfOneId() throws Exception {
		String set = nextSet();
		JSONObject c = sharedImageCard("split", set);
		c.put("multiverse_ids", arr(Long.valueOf(333)));
		List<MagicCard> r = parse(set, c);
		Assert.assertTrue(r.get(0).getText().contains("MID: 333<br>"));
		Assert.assertTrue(r.get(1).getText().contains("MID: 333<br>"));
	}

	@Test
	public void testTwoFacedTextsDifferOnlyByTheMultiverseLine() throws Exception {
		String set = nextSet();
		JSONObject c = faceImageCard("transform", set);
		List<MagicCard> r = parse(set, c);
		Assert.assertEquals("no multiverse ids: the same text on both faces", r.get(0).getText(), r.get(1).getText());
		Assert.assertTrue(r.get(0).getText().contains("Finishes: Nonfoil, Foil<br>"));
		Assert.assertTrue(r.get(0).getText().contains("href=\"https://scryfall.com/x\">Scryfall</a>"));
	}

	@Test
	public void testTraitsInTheTextAreEachFacesOwn() throws Exception {
		String set = nextSet();
		JSONObject c = faceImageCard("transform", set);
		((JSONObject) ((JSONArray) c.get("card_faces")).get(0)).put("watermark", "orzhov");
		List<MagicCard> r = parse(set, c);
		Assert.assertTrue(r.get(0).getText().contains("Watermark: Orzhov"));
		Assert.assertFalse(r.get(1).getText().contains("Watermark: Orzhov"));
	}

	// ------------------------------------------------------------ several records

	@Test
	public void testRecordsComeOutInOrderFrontThenBack() throws Exception {
		String set = nextSet();
		JSONObject single = card("normal", "Solo", set);
		single.put("image_uris", images(IMG_CARD));
		JSONObject dfc = faceImageCard("transform", set);
		JSONObject adventure = sharedImageCard("adventure", set);
		adventure.put("id", "adv-" + set);
		List<MagicCard> r = parse(set, single, dfc, adventure);
		Assert.assertEquals(5, r.size());
		Assert.assertEquals("Solo", r.get(0).getName());
		Assert.assertEquals("Front // Back", r.get(1).getName());
		Assert.assertEquals("Front // Back (transform)", r.get(2).getName());
		Assert.assertEquals("Front // Back", r.get(3).getName());
		Assert.assertEquals("Front // Back (adventure)", r.get(4).getName());
	}

	@Test
	public void testNoPaperNorOnlineRecordIsSkippedBeforeFaces() throws Exception {
		String set = nextSet();
		JSONObject c = faceImageCard("transform", set);
		c.put("games", arr("astral"));
		Assert.assertTrue(parse(set, c).isEmpty());
	}

	// ------------------------------------------------------------ data driven faces

	/** The faces decide, not the layout name: a layout Scryfall adds tomorrow needs no code. */
	@Test
	public void testUnknownLayoutWithTwoFacesIsTwoCards() throws Exception {
		String set = nextSet();
		JSONObject c = faceImageCard("some_future_layout", set);
		List<MagicCard> r = parse(set, c);
		Assert.assertEquals(2, r.size());
		Assert.assertEquals("Front // Back", r.get(0).getName());
		Assert.assertEquals("Front // Back (some_future_layout)", r.get(1).getName());
		Assert.assertEquals(IMG_FRONT, r.get(0).get(MagicCardField.IMAGE_URL));
		Assert.assertEquals(IMG_BACK, r.get(1).get(MagicCardField.IMAGE_URL));
	}

	@Test
	public void testLayoutNameDoesNotChangeTheResult() throws Exception {
		// the same two faces under every layout name make the same two cards
		for (String layout : new String[] { "normal", "saga", "transform", "zzz_new" }) {
			String set = nextSet();
			List<MagicCard> r = parse(set, faceImageCard(layout, set));
			Assert.assertEquals(layout, 2, r.size());
			Assert.assertEquals(layout, "Front // Back (" + layout + ")", r.get(1).getName());
		}
	}

	@Test
	public void testMoreThanTwoFacesKeepTheFirstTwo() throws Exception {
		String set = nextSet();
		JSONObject c = faceImageCard("transform", set);
		((JSONArray) c.get("card_faces")).add(face("Third", "{W}", "Land"));
		List<MagicCard> r = parse(set, c);
		Assert.assertEquals(2, r.size());
		Assert.assertEquals("Creature - Werewolf", r.get(1).getType());
	}

	@Test
	public void testMissingFieldsDoNotThrow() throws Exception {
		// no layout, no mana cost, no artist, no image: a card of nothing but the essentials
		String set = nextSet();
		JSONObject c = card("normal", "Bare", set);
		c.remove("layout");
		c.remove("mana_cost");
		c.remove("artist");
		List<MagicCard> r = parse(set, c);
		Assert.assertEquals(1, r.size());
		Assert.assertEquals("", r.get(0).getCost());
		Assert.assertEquals("", r.get(0).getArtist());
		Assert.assertNull(r.get(0).get(MagicCardField.IMAGE_URL));
	}

	@Test
	public void testMissingImageStatusGivesNoImage() throws Exception {
		String set = nextSet();
		JSONObject c = card("normal", "Gone", set);
		c.put("image_uris", images(IMG_CARD));
		c.put("image_status", "missing");
		Assert.assertNull(parse(set, c).get(0).get(MagicCardField.IMAGE_URL));
	}

	@Test
	public void testImageWithoutNormalSizeUsesAnother() throws Exception {
		String set = nextSet();
		JSONObject c = card("normal", "Sized", set);
		c.put("image_uris", obj("large", IMG_CARD));
		Assert.assertEquals(IMG_CARD, parse(set, c).get(0).get(MagicCardField.IMAGE_URL));
	}

	@Test
	public void testBackFaceWithoutImageUsesTheCardsThenTheFronts() throws Exception {
		String set = nextSet();
		JSONObject c = faceImageCard("transform", set);
		((JSONObject) ((JSONArray) c.get("card_faces")).get(1)).remove("image_uris");
		List<MagicCard> r = parse(set, c);
		Assert.assertEquals(IMG_FRONT, r.get(1).get(MagicCardField.IMAGE_URL));
	}
}
