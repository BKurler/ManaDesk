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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.imageio.ImageIO;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;

/**
 * The real Scryfall, a few small requests only (the set list, one card and its rulings, the
 * symbology and catalogs, one set icon, one small image) - never the bulk card file. When Scryfall
 * cannot be reached or does not answer properly the class FAILS once, with the reason (an online
 * suite that silently skips hides an outage). What is downloaded goes to the workspace, so the
 * tests are skipped unless that is a temp folder (the launch sets
 * {@code -Dosgi.instance.area}), and they never touch the real ManaDesk folder. Asserts the
 * shape of the answers, not their exact data. Not part of the Maven build: see
 * {@code AllOnlineTests}.
 */
public class ScryfallLiveTest {
	private static final String API = "https://api.scryfall.com/";
	private Edition tempEdition;

	@BeforeClass
	public static void onlyWithTheWebAndATempWorkspace() throws Exception {
		String tmp = new File(System.getProperty("java.io.tmpdir")).getCanonicalPath();
		String workspace = FileUtils.getWorkspace().getCanonicalPath();
		skipUnless(workspace.startsWith(tmp), "the workspace is " + workspace + ", not under " + tmp
				+ ": start the tests from AllCoreTests.launch (it sets -Dosgi.instance.area=<temp folder>)");
		if (!WebUtils.isReachable(new URL(API)))
			throw new AssertionError("Scryfall cannot be reached (is the web down?)");
		// one request to see that the service answers: when it is down or refusing us (HTTP 503...), fail
		// once, with the reason, instead of sending a dozen more requests that would all fail
		try {
			WebUtils.openUrlText(new URL(API + "sets/lea"), 1);
		} catch (IOException e) {
			throw new AssertionError("Scryfall does not answer properly (offline for maintenance?): " + e.getMessage(), e);
		}
	}

	/** Skips the whole class when {@code ok} is false, and says why on the console (the JUnit view does not show it). */
	private static void skipUnless(boolean ok, String reason) {
		if (!ok)
			System.err.println("ScryfallLiveTest SKIPPED - " + reason);
		assumeTrue(reason, ok);
	}

	@Before
	public void forgetHostsMarkedDown() {
		WebUtils.setSimulateWebDown(false);
	}

	@After
	public void pauseAndClean() throws Exception {
		if (tempEdition != null)
			Editions.getInstance().remove(tempEdition);
		Thread.sleep(150); // Scryfall asks for 50-100 ms between requests
	}

	// ---- the set list

	@Test
	public void theSetListHoldsTheOldSetsWithTheirAlternativeCodes() throws Exception {
		ParseScryFallSets parser = new ParseScryFallSets();
		try (BufferedReader reader = WebUtils.openUrlReader(parser.getSearchQuery(null), 2)) {
			assertEquals("the whole list comes in one page", null, parser.processFromReader(reader, null));
		}
		assertTrue("expected hundreds of sets, got " + parser.getAll().size(), parser.getAll().size() > 500);
		Edition alpha = null;
		for (Edition ed : parser.getAll())
			if ("Limited Edition Alpha".equals(ed.getName()))
				alpha = ed;
		assertNotNull("Limited Edition Alpha is in the list", alpha);
		assertEquals("LEA", alpha.getMainAbbreviation());
		assertTrue("the old two-letter code is added by us", alpha.abbreviationOf("1E"));
		assertNotNull(alpha.getReleaseDate());
		assertEquals("Core", alpha.getType());
	}

	// ---- one card and its rulings

	@Test
	public void aRealCardHasARulingsPageThatRenders() throws Exception {
		String card = WebUtils.openUrlText(new URL(API + "cards/named?exact=Sol+Ring"), 2);
		JSONObject json = (JSONObject) new JSONParser().parse(card);
		assertEquals("Sol Ring", json.get("name"));
		String rulingsUri = (String) json.get("rulings_uri");
		assertNotNull(rulingsUri);
		assertTrue(rulingsUri, ScryfallRulings.isRulingsUrl(rulingsUri));
		Thread.sleep(150);
		String html = ScryfallRulings.fetchAsHtml(rulingsUri);
		assertTrue(html, html.startsWith("<ul class=\"rulings\">") || html.startsWith("<p>No rulings"));
	}

	// ---- the web helper

	@Test
	public void aKnownAddressIsReadAndAMissingOneIsARealErrorNotAnOutage() throws Exception {
		assertTrue(WebUtils.isReachable(new URL(API)));
		String text = WebUtils.openUrlText(new URL(API + "sets/lea"), 2);
		assertTrue(text, text.contains("Limited Edition Alpha"));
		Thread.sleep(150);
		try {
			WebUtils.openUrlText(new URL(API + "cards/named?exact=No+Such+Card+Zzzxq"), 1);
			throw new AssertionError("a card that does not exist must be an error");
		} catch (IOException e) {
			assertFalse("a 404 is not 'the web is unavailable': " + e, WebUtils.isWebUnavailable(e));
		}
	}

	// ---- symbology and catalogs

	@Test
	public void theReferenceListsAreRefreshedFromScryfall() throws Exception {
		ScryfallReference.refresh(null);
		assertTrue("the symbology was saved", new File(ScryfallReference.refreshedDir(), "symbology.json").length() > 0
				|| ScryfallReference.refreshedDir().list().length > 0);
		assertTrue(ScryfallReference.catalog(ScryfallReference.CARD_TYPES).contains("Creature"));
		assertTrue(ScryfallReference.catalog(ScryfallReference.KEYWORD_ABILITIES).contains("Flying"));
		assertTrue(ScryfallReference.symbols().containsKey("{R}"));
		assertEquals("R.svg", ScryfallReference.symbolFile("{R}"));
		assertEquals("RG.svg", ScryfallReference.symbolFile("{R/G}"));
	}

	// ---- an icon and an image

	@Test
	public void aSetIconIsDownloadedRasterizedAndSaved() throws Exception {
		tempEdition = Editions.getInstance().addEdition("Online Icon Test Set", "OIT");
		tempEdition.setIconAbbr("m21");
		File target = new File(tempEdition.getImageFiles().createSetImageLocalURL("Common").toURI());
		target.delete();
		File png = tempEdition.getImageFiles().ensureSetSymbolExists("Common");
		assertNotNull("the icon was saved", png);
		assertTrue(png.getPath(), png.isFile() && png.length() > 0);
		BufferedImage image = ImageIO.read(png);
		assertNotNull("a PNG image", image);
		assertEquals(19, image.getWidth());
		assertEquals(19, image.getHeight());
		png.delete();
	}

	@Test
	public void aRemoteImageIsSavedToTheCache() throws Exception {
		File dir = Files.createTempDirectory("live-image").toFile();
		try {
			File target = new File(dir, "R.svg");
			CardCache.saveCachedFile(target, new URL("https://svgs.scryfall.io/card-symbols/R.svg"));
			String text = new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
			assertTrue(text, text.contains("<svg"));
			assertEquals("no temporary file left behind", 1, dir.list().length);
		} finally {
			FileUtils.deleteTree(dir);
		}
	}
}
