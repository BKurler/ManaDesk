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

package com.reflexit.magiccards.ui.tests;

import java.util.ArrayList;
import java.util.List;

import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.ui.views.columns.AbstractColumn;
import com.reflexit.magiccards.ui.views.columns.GroupColumn;
import com.reflexit.magiccards.ui.views.columns.PrintingsColumnCollection;

import junit.framework.TestCase;

/**
 * The Printings view column set must contain only fields that vary between
 * printings of the same card (set, collector number, rarity, artist, ...), never
 * the oracle-level fields that are identical on every printing, and never
 * anything tied to a collection or a deck. The collector number is mandatory.
 */
public class PrintingsColumnCollectionTest extends TestCase {

	private PrintingsColumnCollection cc;

	@Override
	protected void setUp() {
		cc = new PrintingsColumnCollection("test.printings.page");
	}

	private List<ICardField> fields() {
		List<ICardField> res = new ArrayList<>();
		for (AbstractColumn c : cc.getColumns())
			res.add(c.getDataField());
		return res;
	}

	public void testKeepsPrintingSpecificColumns() {
		List<ICardField> f = fields();
		assertTrue("Name", f.contains(MagicCardField.NAME));
		assertTrue("Set", f.contains(MagicCardField.SET));
		assertTrue("Collector's Number", f.contains(MagicCardField.COLLNUM));
		assertTrue("Rarity", f.contains(MagicCardField.RARITY));
		assertTrue("Artist", f.contains(MagicCardField.ARTIST));
		assertTrue("Language", f.contains(MagicCardField.LANG));
		assertTrue("Release Date", f.contains(MagicCardField.SET_RELEASE));
		assertTrue("Multiverse ID", f.contains(MagicCardField.GATHERERID));
	}

	public void testDropsColumnsThatAreTheSameOnEveryPrinting() {
		List<ICardField> f = fields();
		assertFalse("Cost is identical on every printing", f.contains(MagicCardField.COST));
		assertFalse("Type is identical on every printing", f.contains(MagicCardField.TYPE));
		assertFalse("Power is identical on every printing", f.contains(MagicCardField.POWER));
		assertFalse("Toughness is identical on every printing", f.contains(MagicCardField.TOUGHNESS));
		assertFalse("Oracle text is identical on every printing", f.contains(MagicCardField.ORACLE));
		assertFalse("Color is identical on every printing", f.contains(MagicCardField.COLOR));
		assertFalse("Legality is identical on every printing", f.contains(MagicCardField.LEGALITY));
	}

	public void testDropsRating() {
		assertFalse("community rating is not useful in the Printings view",
				fields().contains(MagicCardField.RATING));
	}

	public void testDropsCollectionAndDeckColumns() {
		List<ICardField> f = fields();
		assertFalse("Count", f.contains(MagicCardField.COUNT));
		assertFalse("Location", f.contains(MagicCardField.LOCATION));
		assertFalse("Ownership", f.contains(MagicCardField.OWNERSHIP));
		assertFalse("Comment", f.contains(MagicCardField.COMMENT));
		assertFalse("User Price", f.contains(MagicCardField.PRICE));
		assertFalse("Special", f.contains(MagicCardField.SPECIAL));
		assertFalse("Sideboard", f.contains(MagicCardField.SIDEBOARD));
	}

	public void testHasNameGroupColumn() {
		assertNotNull("column set needs a Name/Group column", cc.getColumn(GroupColumn.COL_NAME));
	}

	public void testCollectorNumberStaysVisibleEvenWhenPreferenceHidesIt() {
		cc.updateColumnsFromPropery("Name,Set,-Collector's Number");
		AbstractColumn collNum = cc.getColumn(MagicCardField.COLLNUM);
		assertNotNull(collNum);
		assertTrue("collector number is mandatory in the Printings view", collNum.isVisible());
	}

	public void testIdIsThePreferencePageId() {
		assertEquals("test.printings.page", cc.getId());
	}
}
