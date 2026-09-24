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
import com.reflexit.magiccards.ui.views.lib.DeckColumnCollection;

import junit.framework.TestCase;

/**
 * Within a single deck/collection's own tab every row already belongs to the
 * one pile you opened, so Sideboard/Extra/Location are always the same value
 * there and never useful as columns - unlike My Cards, which searches across
 * every pile of every deck at once. {@link DeckColumnCollection} must drop
 * exactly those three columns and keep everything else
 * {@code MagicColumnCollection} defines.
 */
public class DeckColumnCollectionTest extends TestCase {

	private DeckColumnCollection cc;

	@Override
	protected void setUp() {
		cc = new DeckColumnCollection("test.deck.page");
	}

	private List<ICardField> fields() {
		List<ICardField> res = new ArrayList<>();
		for (AbstractColumn c : cc.getColumns())
			res.add(c.getDataField());
		return res;
	}

	public void testDropsSideboardAndExtraColumns() {
		List<ICardField> f = fields();
		assertFalse("Sideboard is always the same value in a single deck/collection tab",
				f.contains(MagicCardField.SIDEBOARD));
		assertFalse("Extra is always the same value in a single deck/collection tab",
				f.contains(MagicCardField.EXTRA));
	}

	public void testKeepsTheOtherPerCopyColumns() {
		List<ICardField> f = fields();
		assertTrue("Condition", f.contains(MagicCardField.CONDITION));
		assertTrue("Finish", f.contains(MagicCardField.FINISH));
		assertTrue("Comment", f.contains(MagicCardField.COMMENT));
		assertTrue("Count", f.contains(MagicCardField.COUNT));
	}

	public void testDropsLocationColumnToo() {
		List<ICardField> f = fields();
		assertFalse("Location is always the same value in a single deck/collection tab",
				f.contains(MagicCardField.LOCATION));
	}

	public void testIdIsThePreferencePageId() {
		assertEquals("test.deck.page", cc.getId());
	}
}
