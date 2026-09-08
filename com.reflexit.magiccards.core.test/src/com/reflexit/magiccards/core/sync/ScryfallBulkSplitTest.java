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
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.test.assist.AbstractMagicTest;
import com.reflexit.unittesting.TestFileUtils;

/**
 * Offline tests for the Scryfall "Default Cards" bulk-file parse
 * ({@link ParseScryFallChecklist#parseBulkGrouped}) that feeds the single
 * "Update Card Database" action. No network: a tiny fake {@code .jsonl.gz} is
 * built on disk.
 */
public class ScryfallBulkSplitTest extends AbstractMagicTest {

	@BeforeClass
	public static void setUpBeforeClass() {
		TestFileUtils.resetDb();
		DataManager.getInstance().waitForInit(10);
	}

	// ---------------------------------------------------------------- helpers

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

	private static Map<String, List<MagicCard>> parse(File bulk, java.util.Set<String> only) throws Exception {
		return new ParseScryFallChecklist().parseBulkGrouped(bulk, only, ICoreProgressMonitor.NONE);
	}

	// ------------------------------------------------------------------ tests

	@Test
	public void testFullPassGroupsEverySet() throws Exception {
		File bulk = gzWithLines(cardJson("aaa", "A1", "1"), cardJson("aaa", "A2", "2"), cardJson("bbb", "B1", "1"));

		Map<String, List<MagicCard>> g = parse(bulk, null);

		Assert.assertEquals("two sets", 2, g.size());
		Assert.assertEquals("aaa: 2 cards", 2, g.get("aaa").size());
		Assert.assertEquals("bbb: 1 card", 1, g.get("bbb").size());
	}

	@Test
	public void testFiltersToRequestedSet() throws Exception {
		File bulk = gzWithLines(cardJson("tst", "Card One", "1"), cardJson("tst", "Card Two", "2"),
				cardJson("oth", "Other Card", "1"));

		Map<String, List<MagicCard>> g = parse(bulk, Collections.singleton("tst"));

		Assert.assertNotNull(g.get("tst"));
		Assert.assertEquals("two 'tst' cards", 2, g.get("tst").size());
		Assert.assertTrue("'oth' not returned", g.get("oth") == null || g.get("oth").isEmpty());
	}

	@Test
	public void testSkipsNonPaper() throws Exception {
		String digital = cardJson("tst", "Arena Card", "5").replace("[\"paper\"]", "[\"mtgo\",\"arena\"]");
		File bulk = gzWithLines(cardJson("tst", "Paper Card", "1"), digital);

		Map<String, List<MagicCard>> g = parse(bulk, Collections.singleton("tst"));

		Assert.assertEquals("only the paper card kept", 1, g.get("tst").size());
	}

	@Test
	public void testToleratesArrayWrappingAndBlankLines() throws Exception {
		File bulk = gzWithLines("[", "  " + cardJson("tst", "A", "1") + ",", "", "  " + cardJson("tst", "B", "2"), "]");

		Map<String, List<MagicCard>> g = parse(bulk, Collections.singleton("tst"));

		Assert.assertEquals(2, g.get("tst").size());
	}

	@Test
	public void testCancelledParseThrows() throws Exception {
		String[] many = new String[9000];
		for (int i = 0; i < many.length; i++)
			many[i] = cardJson("tst", "C" + i, String.valueOf(i));
		File bulk = gzWithLines(many);

		ICoreProgressMonitor cancelled = new ICoreProgressMonitor() {
			@Override public void beginTask(String name, int totalWork) {}
			@Override public void subTask(String name) {}
			@Override public void setTaskName(String name) {}
			@Override public void worked(int work) {}
			@Override public void internalWorked(double work) {}
			@Override public void done() {}
			@Override public void setCanceled(boolean value) {}
			@Override public boolean isCanceled() { return true; }
		};
		try {
			new ParseScryFallChecklist().parseBulkGrouped(bulk, null, cancelled);
			Assert.fail("expected InterruptedException");
		} catch (InterruptedException expected) {
			// good
		}
	}
}
