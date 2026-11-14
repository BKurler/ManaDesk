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
package com.reflexit.magiccards.ui.exportWizards;

import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.exports.DeckBoxImportDelegate;
import com.reflexit.magiccards.core.exports.ImportData;
import com.reflexit.magiccards.core.exports.ManaBoxCsvImportDelegate;

import junit.framework.TestCase;

/**
 * The first page of an import warns (in red) when the cards go to a For Trade collection: every card will have the
 * fortrade tag; and, only when the file has a Tradelist Count column (a Deckbox Inventory), that this count is
 * ignored. Pure logic, plain JUnit.
 */
public class ForTradeImportNoteTest extends TestCase {
	private static DeckBoxImportDelegate read(String header, String row) {
		DeckBoxImportDelegate d = new DeckBoxImportDelegate();
		ImportData data = new ImportData();
		data.setText(header + "\n" + row + "\n");
		d.init(data);
		d.run(ICoreProgressMonitor.NONE);
		return d;
	}

	private static DeckBoxImportDelegate inventory() {
		return read("Count,Tradelist Count,Name,Edition,Card Number", "2,1,Fog,Fourth Edition,244");
	}

	private static DeckBoxImportDelegate wishlist() {
		return read("Count,Name,Edition,Card Number", "2,Fog,Fourth Edition,244");
	}

	public void testHeaderTellsIfThereIsATradelistCount() {
		assertTrue(inventory().hasTradelistColumn());
		assertFalse(wishlist().hasTradelistColumn());
	}

	public void testTagWarningForEveryFormat() {
		for (Object delegate : new Object[] { new ManaBoxCsvImportDelegate(), wishlist(), null }) {
			String w = AbstractCardListImportPage.forTradeWarnings(true, delegate);
			assertTrue(w, w.startsWith("Warning:"));
			assertTrue(w, w.contains("fortrade tag"));
			assertFalse("no trade count in this format: " + w, w.contains("Tradelist Count"));
		}
	}

	public void testTradelistWarningOnlyWhenTheFileHasTheColumn() {
		String w = AbstractCardListImportPage.forTradeWarnings(true, inventory());
		assertTrue(w, w.contains("fortrade tag"));
		assertTrue(w, w.contains("Tradelist Count"));
		assertEquals("two warnings", 2, w.split("\n").length);
	}

	public void testNothingOutsideForTrade() {
		assertEquals("", AbstractCardListImportPage.forTradeWarnings(false, inventory()));
		assertEquals("", AbstractCardListImportPage.forTradeWarnings(false, null));
	}
}
