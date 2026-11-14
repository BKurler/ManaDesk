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

import java.util.List;

import org.junit.Test;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;

/**
 * The "Print Proxies" HTML export and its "Mobile" variant: one picture per copy on the printable
 * sheet, escaped names, the picture address (or none), and what the export wizard is told about
 * the format. The picture addresses are set on the cards (or fixed by a small subclass), so
 * nothing goes on the web. Works as plain JUnit.
 */
public class PrintProxyHtmlExportDelegateTest extends AbstarctExportTest {
	/** Gives every card the same fixed picture address instead of looking it up. */
	private static class Fixed extends PrintProxyHtmlExportDelegate {
		@Override
		protected String getCardImageUrl(IMagicCard card) {
			return "file:///pics/" + card.getName() + ".jpg";
		}
	}

	private static class NoPicture extends PrintProxyHtmlExportDelegate {
		@Override
		protected String getCardImageUrl(IMagicCard card) {
			return null;
		}
	}

	private List<String> exportSlots(PrintProxyHtmlExportDelegate exporter) {
		run(exporter);
		String html = out.toString();
		assertTrue("not a printable sheet", PrintSheet.isPrintSheet(html));
		return PrintSheet.slots(html);
	}

	@Test
	public void testOneSlotPerCopy() {
		card1.setCount(3);
		card2.setCount(1);
		((com.reflexit.magiccards.core.model.MagicCardPhysical) card3).setCount(2);
		assertEquals(6, exportSlots(new Fixed()).size());
	}

	@Test
	public void testEachSlotIsAProxyPictureWithTheNameAndAddress() {
		card1.setCount(1);
		card2.setCount(0);
		((com.reflexit.magiccards.core.model.MagicCardPhysical) card3).setCount(0);
		List<String> slots = exportSlots(new Fixed());
		assertEquals(1, slots.size());
		String slot = slots.get(0);
		assertTrue(slot, slot.startsWith("<img class=\"proxy\" src=\"file:///pics/"));
		assertTrue(slot, slot.contains("alt=\"" + PrintSheet.esc(card1.getName()) + "\""));
	}

	@Test
	public void testANameIsEscaped() {
		card1.set(MagicCardField.NAME, "Ping & <Zap>");
		card1.setCount(1);
		card2.setCount(0);
		((com.reflexit.magiccards.core.model.MagicCardPhysical) card3).setCount(0);
		run(new NoPicture());
		String html = out.toString();
		assertTrue(html.contains("alt=\"Ping &amp; &lt;Zap&gt;\""));
		assertTrue("raw markup not escaped", !html.contains("<Zap>"));
	}

	@Test
	public void testAMissingAddressGivesAnEmptySource() {
		card1.setCount(1);
		card2.setCount(0);
		((com.reflexit.magiccards.core.model.MagicCardPhysical) card3).setCount(0);
		List<String> slots = exportSlots(new NoPicture());
		assertEquals(1, slots.size());
		assertTrue(slots.get(0), slots.get(0).contains("src=\"\""));
	}

	@Test
	public void testTheLocationIsTheMainDeckOfTheFirstCard() {
		PrintProxyHtmlExportDelegate exporter = new Fixed();
		run(exporter);
		assertTrue(exporter.location != null);
		assertEquals(card1.getLocation().toMainDeck(), exporter.location);
	}

	@Test
	public void testAnEmptyListGivesASheetWithoutCards() {
		PrintProxyHtmlExportDelegate exporter = new Fixed();
		deck.getCardStore().removeAll();
		deck.update();
		exporter.init(out, true, deck);
		try {
			exporter.run(null);
		} catch (Exception e) {
			fail(e.toString());
		}
		String html = out.toString();
		assertTrue(PrintSheet.isPrintSheet(html));
		assertEquals(0, PrintSheet.slots(html).size());
	}

	@Test
	public void testTheMobileVariantUsesTheRemoteAddressOfTheCard() {
		card1.set(MagicCardField.IMAGE_URL, "https://example.invalid/a.jpg");
		PrintProxyHtmlExporMobileDelegate mobile = new PrintProxyHtmlExporMobileDelegate();
		assertEquals("https://example.invalid/a.jpg", mobile.getCardImageUrl(card1));
	}

	@Test
	public void testTheMobileVariantHasNoAddressWithoutOne() {
		card1.set(MagicCardField.IMAGE_URL, null);
		assertTrue(new PrintProxyHtmlExporMobileDelegate().getCardImageUrl(card1) == null);
	}

	@Test
	public void testTheMobileVariantSurvivesABrokenAddress() {
		card1.set(MagicCardField.IMAGE_URL, "not an address");
		assertTrue(new PrintProxyHtmlExporMobileDelegate().getCardImageUrl(card1) == null);
	}

	@Test
	public void testWhatTheWizardIsTold() {
		PrintProxyHtmlExportDelegate exporter = new PrintProxyHtmlExportDelegate();
		assertTrue(!exporter.isColumnChoiceSupported());
		assertTrue(exporter.isMultipleLocationSupported());
		assertTrue(exporter.isSideboardSupported());
		assertTrue(exporter.isCombineByDefault());
		assertTrue(exporter.isPrintSheet());
	}
}
