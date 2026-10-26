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
package com.reflexit.magiccards.core.model.nav;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.unittesting.TestFileUtils;

import junit.framework.TestCase;

/**
 * A deck's default format is edited on the main deck only: its sideboard and extra list use the same one.
 * Plain JUnit, with the core test database fixtures.
 */
public class DeckFormatInheritTest extends TestCase {
	private static boolean reset = true;
	private static int n = 0;
	private DataManager dm;
	private CardCollection main;
	private CardCollection side;
	private CardCollection extra;

	@Override
	protected void setUp() throws Exception {
		if (reset) {
			TestFileUtils.resetDb();
			reset = false;
		}
		dm = DataManager.getInstance();
		dm.waitForInit(10);
		dm.getLibraryCardStore();
		CollectionsContainer decks = dm.getModelRoot().getDeckContainer();
		main = decks.addDeck("fmtdeck" + (++n), true, true);
		Location base = main.getLocation();
		side = decks.addDeck(base.toSideboard().getBaseFileName(), true, true);
		extra = decks.addDeck(base.toExtra().getBaseFileName(), true, true);
	}

	@Override
	protected void tearDown() throws Exception {
		extra.remove();
		side.remove();
		main.remove();
		super.tearDown();
	}

	public void testSideboardAndExtraUseTheMainDecksFormat() {
		main.getStorageInfo().setDefaultFormat("Modern");
		assertEquals("Modern", main.getDefaultFormat());
		assertEquals("Modern", side.getDefaultFormat());
		assertEquals("Modern", extra.getDefaultFormat());
		main.getStorageInfo().setDefaultFormat("Pauper");
		assertEquals("Pauper", side.getDefaultFormat());
	}

	public void testAFormatStoredOnTheSideboardIsIgnored() {
		side.getStorageInfo().setDefaultFormat("Legacy");
		main.getStorageInfo().setDefaultFormat("Modern");
		assertEquals("Modern", side.getDefaultFormat());
		assertEquals("Modern", CardCollection.effectiveDefaultFormat(side.getLocation(), side.getStorageInfo()));
	}

	public void testNoFormatOnTheMainDeckMeansNoneForTheSideboard() {
		side.getStorageInfo().setDefaultFormat("Legacy");
		main.getStorageInfo().setDefaultFormat(null);
		assertNull(side.getDefaultFormat());
	}

	public void testAMainDeckOrOtherListUsesItsOwn() {
		main.getStorageInfo().setDefaultFormat("Standard");
		assertEquals("Standard", main.getDefaultFormat());
		assertEquals("Own", CardCollection.effectiveDefaultFormat(null, new FormatInfo("Own")));
	}

	/** Just enough of a storage info for the lookup. */
	private static class FormatInfo extends com.reflexit.magiccards.core.model.xml.SingleFileCardStorage {
		private final String format;

		FormatInfo(String format) {
			this.format = format;
		}

		@Override
		public String getDefaultFormat() {
			return format;
		}
	}
}
