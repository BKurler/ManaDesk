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
package com.reflexit.magiccards.ui.gallery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;

/**
 * The HTML of the card gallery: the page around the cards, a card with its count and "Proxy"
 * badges, and the page for the virtual (paged) gallery. The picture addresses are {@code file:}
 * addresses, so nothing goes on the web. JUnit Plug-in Test (the "image not available" picture is
 * found through the UI plug-in).
 */
public class GalleryHtmlBuilderTest {
	private static MagicCard card(int id) {
		MagicCard c = new MagicCard();
		c.setCardId(id);
		c.set(MagicCardField.IMAGE_URL, "file:///pics/card" + id + ".jpg");
		return c;
	}

	private static MagicCardPhysical copy(MagicCard c, int count, boolean proxy) {
		MagicCardPhysical p = new MagicCardPhysical(c, null);
		p.setCount(count);
		p.setProxy(proxy);
		return p;
	}

	private static int occurrences(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length()))
			n++;
		return n;
	}

	@Test
	public void testThePageHasTheGalleryAndItsScript() {
		String html = GalleryHtmlBuilder.buildHtml(new ArrayList<Object>());
		assertTrue(html.startsWith("<!DOCTYPE html>"));
		assertTrue(html.contains("<div class='gallery'></div>"));
		assertTrue(html.contains("<style>"));
		assertTrue(html.contains("<script>"));
		assertTrue(html.endsWith("</body></html>"));
	}

	@Test
	public void testSomethingThatIsNotAListGivesAnEmptyGallery() {
		assertTrue(GalleryHtmlBuilder.buildHtml(null).contains("<div class='gallery'></div>"));
		assertTrue(GalleryHtmlBuilder.buildHtml("text").contains("<div class='gallery'></div>"));
	}

	@Test
	public void testOnlyCardsAreShown() {
		List<Object> input = Arrays.<Object> asList(card(11), "not a card", card(12), Integer.valueOf(3));
		String html = GalleryHtmlBuilder.buildHtml(input);
		assertEquals(2, occurrences(html, "data-id='"));
		assertTrue(html.contains("data-id='11'"));
		assertTrue(html.contains("data-id='12'"));
	}

	@Test
	public void testACardIsAnImageInsideADiv() {
		String html = GalleryHtmlBuilder.buildHtml(Arrays.asList(card(21)));
		assertTrue(html.contains("<div class='card-inner'><img src='"));
		assertTrue(html.contains("loading='lazy'"));
		assertTrue(html.contains("<div class='badges'>"));
	}

	@Test
	public void testACardWithoutCountOrProxyHasNoBadge() {
		String html = GalleryHtmlBuilder.buildHtml(Arrays.asList(card(31)));
		assertFalse(html.contains("<span class='badge"));
		assertFalse(html.contains("class='card proxy'"));
	}

	@Test
	public void testSeveralCopiesShowTheCount() {
		String html = GalleryHtmlBuilder.buildHtml(Arrays.asList(copy(card(41), 3, false)));
		assertTrue(html.contains("<span class='badge count-badge'>x3</span>"));
		assertFalse(html.contains("<span class='badge proxy-badge'>"));
	}

	@Test
	public void testASingleCopyShowsNoCount() {
		String html = GalleryHtmlBuilder.buildHtml(Arrays.asList(copy(card(51), 1, false)));
		assertFalse(html.contains("count-badge'>x1"));
		assertFalse(html.contains("<span class='badge count-badge"));
	}

	@Test
	public void testAProxyGetsItsFrameAndBadge() {
		String html = GalleryHtmlBuilder.buildHtml(Arrays.asList(copy(card(61), 2, true)));
		assertTrue(html.contains("<div class='card proxy' data-id='61'>"));
		assertTrue(html.contains("<span class='badge proxy-badge'>Proxy</span>"));
		assertTrue(html.contains("x2"));
	}

	// ---- the paged gallery

	@Test
	public void testTheVirtualGalleryStartsEmptyAndKnowsTheTotal() {
		String html = GalleryHtmlBuilder.buildVirtualGalleryHtml(1234);
		assertTrue(html.startsWith("<!DOCTYPE html>"));
		assertTrue(html.contains("<div class='gallery' id='gallery'></div>"));
		assertTrue(html.contains("window.totalCards=1234;"));
		assertTrue(html.contains("var pageSize=80;"));
		assertTrue("no card selected", html.contains("window.__wantSelect=null;"));
		assertTrue(html.contains("window.__wantIndex=-1;"));
	}

	@Test
	public void testTheVirtualGalleryCanSelectACard() {
		String html = GalleryHtmlBuilder.buildVirtualGalleryHtml(2000, "77", 1900);
		assertTrue(html.contains("window.__wantIndex=1900;"));
		assertTrue(html.contains("window.__wantSelect='77';"));
	}

	@Test
	public void testTheSelectedIdIsMadeSafeForTheScript() {
		String html = GalleryHtmlBuilder.buildVirtualGalleryHtml(10, "a'b<c>", 3);
		assertFalse("raw markup", html.contains("<c>"));
		assertTrue("quote dropped, markup escaped", html.contains("window.__wantSelect='ab&lt;c&gt;';"));
	}

	@Test
	public void testAnEmptySelectionIsIgnored() {
		assertTrue(GalleryHtmlBuilder.buildVirtualGalleryHtml(10, "", 3).contains("window.__wantSelect=null;"));
		assertTrue(GalleryHtmlBuilder.buildVirtualGalleryHtml(10, null, 3).contains("window.__wantSelect=null;"));
	}
}
