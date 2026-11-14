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
package com.reflexit.magiccards.ui.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.eclipse.swt.graphics.Image;
import org.junit.Test;

/**
 * Card symbols in text and pictures: {R} turned into a picture address in the card HTML, a symbol
 * picture loaded once, scaled to the standard height and joined into a mana cost image. Uses the
 * symbol pictures that ship with the plug-in. JUnit Plug-in Test (pictures need a display).
 */
public class SymbolRendererTest {
	// ---- in HTML

	@Test
	public void testNothingToReplaceInEmptyOrPlainText() {
		assertEquals("", SymbolRenderer.replaceSymbolsWithHtmlImages(null));
		assertEquals("", SymbolRenderer.replaceSymbolsWithHtmlImages(""));
		assertEquals("Add one mana.", SymbolRenderer.replaceSymbolsWithHtmlImages("Add one mana."));
	}

	@Test
	public void testASymbolBecomesAPicture() {
		String html = SymbolRenderer.replaceSymbolsWithHtmlImages("Pay {R} now");
		assertTrue(html, html.startsWith("Pay <img src=\""));
		assertTrue(html, html.contains("R.png\" style=\"vertical-align:middle;\"/> now"));
		assertFalse(html.contains("{R}"));
	}

	@Test
	public void testSeveralSymbolsAndHybrids() {
		String html = SymbolRenderer.replaceSymbolsWithHtmlImages("{2}{R/G}{T}");
		assertEquals(3, html.split("<img ", -1).length - 1);
		assertFalse(html.contains("{"));
	}

	@Test
	public void testAnUnknownOrUnclosedSymbolIsKeptAsText() {
		assertEquals("{not a symbol}", SymbolRenderer.replaceSymbolsWithHtmlImages("{not a symbol}"));
		assertEquals("cost {R", SymbolRenderer.replaceSymbolsWithHtmlImages("cost {R"));
	}

	@Test
	public void testNoHtmlForNothing() {
		assertEquals("", SymbolRenderer.wrapHtml(null, null));
	}

	@Test
	public void testBundledSymbolPictures() {
		assertTrue(SymbolRenderer.isBundledSymbol("R.png"));
		assertFalse(SymbolRenderer.isBundledSymbol("no-such-symbol.png"));
	}

	// ---- pictures

	@Test
	public void testASymbolPictureIsLoadedOnceAndKept() {
		Image first = SymbolRenderer.getManaSymbolImage("{R}", "icons/symbols/R.png");
		assertNotNull(first);
		assertSame(first, SymbolRenderer.getManaSymbolImage("{R}", "icons/symbols/R.png"));
	}

	@Test
	public void testAMissingSymbolPictureIsNull() {
		assertNull(SymbolRenderer.getManaSymbolImage("{Q}", "icons/symbols/no-such-symbol-zz.png"));
	}

	@Test
	public void testAPictureIsScaledToTheSymbolHeightAndKept() {
		Image base = SymbolRenderer.getManaSymbolImage("{G}", "icons/symbols/G.png");
		Image scaled = SymbolRenderer.scaleToSymbolHeight(base, "scaled:test-G");
		assertEquals(SymbolRenderer.SYMBOL_SIZE, scaled.getBounds().height);
		assertSame(scaled, SymbolRenderer.scaleToSymbolHeight(base, "scaled:test-G"));
		assertNull(SymbolRenderer.scaleToSymbolHeight(null, "scaled:test-none"));
	}

	@Test
	public void testAManaCostIsOnePictureAsWideAsItsSymbols() {
		Image one = SymbolRenderer.buildCostImage("{R}");
		Image two = SymbolRenderer.buildCostImage("{R}{R}");
		assertNotNull(one);
		assertNotNull(two);
		assertEquals(SymbolRenderer.SYMBOL_SIZE, two.getBounds().height);
		assertEquals(2 * one.getBounds().width, two.getBounds().width);
		assertSame("kept", one, SymbolRenderer.buildCostImage("{R}"));
	}

	@Test
	public void testNoCostPictureForNothingOrWhatIsNotACost() {
		assertNull(SymbolRenderer.buildCostImage(null));
		assertNull(SymbolRenderer.buildCostImage(""));
		assertNull(SymbolRenderer.buildCostImage("*"));
		assertNull(SymbolRenderer.buildCostImageInternal("N/A"));
	}
}
