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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * The card rulings shown in the Rulings view: which addresses count as a rulings page, and how
 * Scryfall's rulings answer becomes HTML (escaping, symbols kept, source label, empty list, bad
 * answer). The answer is read from a {@code file:} address, so nothing goes on the web. Works as
 * plain JUnit.
 */
public class ScryfallRulingsTest extends TestCase {
	private File file;

	@Override
	protected void tearDown() throws Exception {
		if (file != null)
			file.delete();
	}

	private String render(String json) throws IOException {
		file = File.createTempFile("rulings-test", ".json");
		Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
		return ScryfallRulings.fetchAsHtml(file.toURI().toURL().toExternalForm());
	}

	// ---- which addresses

	public void testRulingsAddresses() {
		assertTrue(ScryfallRulings.isRulingsUrl("https://api.scryfall.com/cards/abc-123/rulings"));
		assertTrue(ScryfallRulings.isRulingsUrl("http://scryfall.com/card/x/rulings"));
		assertFalse("an image host is not a rulings page", ScryfallRulings.isRulingsUrl("https://cards.scryfall.io/x/rulings"));
		assertFalse("not a rulings endpoint", ScryfallRulings.isRulingsUrl("https://api.scryfall.com/cards/abc-123"));
		assertFalse(ScryfallRulings.isRulingsUrl("https://example.com/cards/abc/rulings"));
		assertFalse(ScryfallRulings.isRulingsUrl(null));
		assertFalse(ScryfallRulings.isRulingsUrl(""));
	}

	// ---- the HTML

	public void testNoRulings() throws Exception {
		assertEquals("<p>No rulings for this card.</p>", render("{\"object\":\"list\",\"data\":[]}"));
		assertEquals("a missing list is the same as an empty one", "<p>No rulings for this card.</p>", render("{\"object\":\"list\"}"));
	}

	public void testOneRulingWithDateAndSource() throws Exception {
		String html = render("{\"data\":[{\"source\":\"wotc\",\"published_at\":\"2021-03-19\",\"comment\":\"It does a thing.\"}]}");
		assertEquals("<ul class=\"rulings\"><li><b>2021-03-19</b> &ndash; It does a thing.</li></ul>", html);
	}

	public void testSourceOtherThanWizardsIsShown() throws Exception {
		String html = render("{\"data\":[{\"source\":\"scryfall\",\"published_at\":\"2020-01-01\",\"comment\":\"Note.\"}]}");
		assertTrue(html, html.contains("Note. <i>(scryfall)</i>"));
		String wotc = render("{\"data\":[{\"source\":\"WOTC\",\"published_at\":\"2020-01-01\",\"comment\":\"Note.\"}]}");
		assertFalse("the Wizards source is not repeated", wotc.contains("<i>"));
	}

	public void testSeveralRulingsKeepTheirOrder() throws Exception {
		String html = render("{\"data\":[{\"published_at\":\"2019-01-01\",\"comment\":\"First.\"},"
				+ "{\"published_at\":\"2020-01-01\",\"comment\":\"Second.\"}]}");
		assertTrue(html.indexOf("First.") < html.indexOf("Second."));
		assertEquals(2, html.split("<li>").length - 1);
	}

	public void testCommentsAreEscapedButSymbolsAreKept() throws Exception {
		String html = render("{\"data\":[{\"published_at\":\"2020-01-01\","
				+ "\"comment\":\"Pay {T}, then a <b>bold</b> & \\\"quoted\\\" move.\"}]}");
		assertTrue(html, html.contains("{T}"));
		assertTrue(html, html.contains("&lt;b&gt;bold&lt;/b&gt;"));
		assertTrue(html, html.contains("&amp;"));
		assertFalse(html, html.contains("<b>bold</b>"));
	}

	public void testMissingFieldsAreLeftOut() throws Exception {
		String html = render("{\"data\":[{\"comment\":\"Only text.\"}]}");
		assertEquals("<ul class=\"rulings\"><li>Only text.</li></ul>", html);
	}

	// ---- bad answers

	public void testAnAnswerThatIsNotJsonIsAnError() throws Exception {
		try {
			render("<html>Service unavailable</html>");
			fail();
		} catch (IOException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("Unexpected rulings response"));
		}
	}

	public void testAnUnreachableAddressIsAnError() throws Exception {
		File missing = new File(System.getProperty("java.io.tmpdir"), "rulings-test-missing-" + System.nanoTime() + ".json");
		try {
			ScryfallRulings.fetchAsHtml(missing.toURI().toURL().toExternalForm());
			fail();
		} catch (IOException e) {
			// expected
		}
	}
}
