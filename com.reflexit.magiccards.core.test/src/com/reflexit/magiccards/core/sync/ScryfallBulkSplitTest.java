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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.test.assist.AbstractMagicTest;
import com.reflexit.unittesting.TestFileUtils;

/**
 * Offline tests for the Scryfall "Default Cards" bulk-file split
 * ({@link ParseScryFallChecklist#groupSetsFromBulk},
 * {@link ParseScryFallChecklist#splitAllFromBulk},
 * {@link ParseScryFallChecklist#writeSetFlatGz}) that feeds
 * {@link ScryfallBulkCache}. No network: a tiny fake {@code .jsonl.gz} is built
 * on disk.
 */
public class ScryfallBulkSplitTest extends AbstractMagicTest {

	@BeforeClass
	public static void setUpBeforeClass() {
		TestFileUtils.resetDb();
		DataManager.getInstance().waitForInit(10);
	}

	// ---------------------------------------------------------------- helpers

	private static MagicCard card(String set, String name, String collNum) {
		MagicCard c = new MagicCard();
		c.setCardId(name.replaceAll("\\s", "") + "-" + set);
		c.setSet(set);
		c.setName(name);
		c.setCollNumber(collNum);
		return c;
	}

	/** A minimal but parseRecord-safe single-face "normal" Scryfall card object. */
	private static String cardJson(String set, String name, String collNum) {
		String id = name.replaceAll("\\s", "") + "-" + set;
		return "{\"id\":\"" + id + "\",\"lang\":\"en\",\"games\":[\"paper\"],\"layout\":\"normal\","
				+ "\"set\":\"" + set + "\",\"set_name\":\"" + set.toUpperCase() + " Set\",\"name\":\"" + name + "\","
				+ "\"mana_cost\":\"{G}\",\"type_line\":\"Creature\",\"rarity\":\"common\",\"artist\":\"Tester\","
				+ "\"collector_number\":\"" + collNum + "\",\"oracle_text\":\"Text\",\"full_art\":false,"
				+ "\"textless\":false,\"story_spotlight\":false,\"booster\":true,"
				+ "\"image_uris\":{\"normal\":\"https://example.com/" + collNum + ".jpg\"},"
				+ "\"scryfall_uri\":\"https://scryfall.com/x\"}";
	}

	private static File gzWithLines(String... lines) throws IOException {
		File f = File.createTempFile("bulk-test-", ".jsonl.gz");
		f.deleteOnExit();
		try (PrintStream out = new PrintStream(new GZIPOutputStream(new FileOutputStream(f)), false, "UTF-8")) {
			for (String l : lines)
				out.println(l);
		}
		return f;
	}

	private static List<String> gzLines(File gz) throws IOException {
		List<String> lines = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(
				new InputStreamReader(new GZIPInputStream(new FileInputStream(gz)), StandardCharsets.UTF_8))) {
			String l;
			while ((l = r.readLine()) != null)
				lines.add(l);
		}
		return lines;
	}

	private static int tmpLeftovers(File dir) {
		File[] f = dir.listFiles((d, n) -> n.endsWith(".tmp"));
		return f == null ? 0 : f.length;
	}

	// ------------------------------------------------------------------ tests

	@Test
	public void testWriteSetFlatGzRoundTripAndSorted() throws IOException {
		File dir = Files.createTempDirectory("bulk-split-write").toFile();
		File gz = new File(dir, "tst.txt.gz");
		List<MagicCard> cards = new ArrayList<>(
				Arrays.asList(card("tst", "Gamma", "30"), card("tst", "Alpha", "10"), card("tst", "Beta", "20")));

		new ParseScryFallChecklist().writeSetFlatGz(cards, gz);

		Assert.assertTrue("gz file written", gz.isFile());
		Assert.assertEquals("no .tmp left behind", 0, tmpLeftovers(dir));

		List<String> lines = gzLines(gz);
		Assert.assertEquals("header + 3 cards", 4, lines.size());
		// SortedOutputHanlder orders by collector number: Alpha(10) < Beta(20) < Gamma(30)
		Assert.assertTrue("Alpha first", lines.get(1).contains("Alpha"));
		Assert.assertTrue("Beta second", lines.get(2).contains("Beta"));
		Assert.assertTrue("Gamma third", lines.get(3).contains("Gamma"));
	}

	@Test
	public void testGroupSetsFromBulkFiltersToRequestedSet() throws IOException {
		File bulk = gzWithLines(cardJson("tst", "Card One", "1"), cardJson("tst", "Card Two", "2"),
				cardJson("oth", "Other Card", "1"));

		Map<String, List<MagicCard>> g = new ParseScryFallChecklist().groupSetsFromBulk(bulk,
				Collections.singleton("tst"));

		Assert.assertNotNull(g.get("tst"));
		Assert.assertEquals("two 'tst' cards", 2, g.get("tst").size());
		Assert.assertTrue("'oth' not returned", g.get("oth") == null || g.get("oth").isEmpty());
	}

	@Test
	public void testGroupSetsFromBulkSkipsNonPaper() throws IOException {
		String digital = cardJson("tst", "Arena Card", "5").replace("[\"paper\"]", "[\"mtgo\",\"arena\"]");
		File bulk = gzWithLines(cardJson("tst", "Paper Card", "1"), digital);

		Map<String, List<MagicCard>> g = new ParseScryFallChecklist().groupSetsFromBulk(bulk,
				Collections.singleton("tst"));

		Assert.assertEquals("only the paper card kept", 1, g.get("tst").size());
	}

	@Test
	public void testGroupSetsFromBulkToleratesArrayWrappingAndBlankLines() throws IOException {
		File bulk = gzWithLines("[", "  " + cardJson("tst", "A", "1") + ",", "", "  " + cardJson("tst", "B", "2"), "]");

		Map<String, List<MagicCard>> g = new ParseScryFallChecklist().groupSetsFromBulk(bulk,
				Collections.singleton("tst"));

		Assert.assertEquals(2, g.get("tst").size());
	}

	@Test
	public void testSplitAllFromBulkWritesOnePerSetAndSkipsEmpty() throws IOException {
		String digitalOnly = cardJson("dig", "Digital Only", "1").replace("[\"paper\"]", "[\"arena\"]");
		File bulk = gzWithLines(cardJson("aaa", "A1", "1"), cardJson("aaa", "A2", "2"), cardJson("bbb", "B1", "1"),
				digitalOnly);
		File dir = Files.createTempDirectory("bulk-split-all").toFile();

		Set<String> written = new ParseScryFallChecklist().splitAllFromBulk(bulk, dir);

		Assert.assertTrue("aaa written", written.contains("aaa"));
		Assert.assertTrue("bbb written", written.contains("bbb"));
		Assert.assertTrue("digital-only set skipped", !written.contains("dig"));

		Assert.assertEquals("aaa: header + 2 cards", 3, gzLines(new File(dir, "aaa.txt.gz")).size());
		Assert.assertEquals("bbb: header + 1 card", 2, gzLines(new File(dir, "bbb.txt.gz")).size());
		Assert.assertTrue("no dig.txt.gz", !new File(dir, "dig.txt.gz").exists());
		Assert.assertEquals("no .tmp left behind", 0, tmpLeftovers(dir));
	}
}
