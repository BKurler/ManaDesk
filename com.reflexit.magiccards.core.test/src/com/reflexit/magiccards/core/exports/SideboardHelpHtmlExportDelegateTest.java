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
package com.reflexit.magiccards.core.exports;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardField;

@FixMethodOrder(MethodSorters.JVM)
public class SideboardHelpHtmlExportDelegateTest extends AbstarctExportTest {
	private final SideboardHelpHtmlExportDelegate exporter = new SideboardHelpHtmlExportDelegate();

	@Override
	public void setUp() throws Exception {
		super.setUp();
		exporter.setReportType(ImportExportFactory.createReportType("sideboardHelpTest"));
		// only card1 + card2 are in the sideboard; card3 stays in the main deck
		Location sb = deck.getLocation().toSideboard();
		card1.setLocation(sb);
		card2.setLocation(sb);
		card1.setCount(3);
		card2.setCount(2);
	}

	@Test
	public void testCardSizedPage() {
		run(exporter);
		String html = out.toString();
		assertTrue("no @page rule", html.contains("@page"));
		assertTrue("page not sized to a card on its side", html.contains("88mm 63mm"));
	}

	@Test
	public void testListsSideboardCardsOnly() {
		run(exporter);
		String html = out.toString();
		assertTrue(html.contains("Sideboard"));
		assertTrue("missing sideboard card name", html.contains(card1.getName()));
		assertTrue("missing sideboard card name", html.contains(card2.getName()));
		assertTrue("main-deck card leaked into the sideboard help", !html.contains(card3.getName()));
		assertTrue("missing quantity", html.contains(">3</td>"));
	}

	@Test
	public void testExtraCardsAndTitle() {
		// card2 moves to the extra list; card1 stays in the sideboard
		card2.setLocation(deck.getLocation().toExtra());
		run(exporter);
		String html = out.toString();
		assertTrue("extra card missing", html.contains(card2.getName()));
		assertTrue("title should mention Extra", html.contains("Sideboard and Extra"));
	}

	@Test
	public void testCombineTwoDecks() {
		// card1 -> deckA sideboard, card2 -> deckB sideboard
		card1.setLocation(Location.valueOf("deckA-sideboard"));
		card2.setLocation(Location.valueOf("deckB-sideboard"));
		run(exporter);
		String html = out.toString();
		int cards = html.split("class=\"card\"", -1).length - 1;
		assertTrue("expected one printable card per deck, got " + cards, cards == 2);
		assertTrue("deckA title missing", html.contains(">deckA <"));
		assertTrue("deckB title missing", html.contains(">deckB <"));
	}

	@Test
	public void testEscapesName() {
		card1.set(MagicCardField.NAME, "Ping & <Zap>");
		run(exporter);
		String html = out.toString();
		assertTrue(html.contains("Ping &amp; &lt;Zap&gt;"));
		assertTrue("raw markup not escaped", !html.contains("<Zap>"));
	}
}
