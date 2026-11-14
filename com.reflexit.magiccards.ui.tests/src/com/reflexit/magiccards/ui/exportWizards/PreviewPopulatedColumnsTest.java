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

import java.util.ArrayList;
import java.util.List;

import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;

import junit.framework.TestCase;

/**
 * The import preview shows every per-copy column an imported card has a value in - in particular Proxy, which a
 * Deckbox file gives through its Condition cell, so no column of the file maps to it. Pure logic, plain JUnit.
 */
public class PreviewPopulatedColumnsTest extends TestCase {
	private static MagicCardPhysical card() {
		MagicCard base = new MagicCard();
		base.setName("Plains");
		MagicCardPhysical c = new MagicCardPhysical(base, null);
		c.setCount(1);
		return c;
	}

	public void testNothingPopulated() {
		List<Object> cards = new ArrayList<>();
		cards.add(card());
		assertTrue(DeckImportPreviewPage.populatedFields(cards).isEmpty());
		assertTrue(DeckImportPreviewPage.populatedFields(null).isEmpty());
	}

	public void testProxyColumnWhenOneCardIsAProxy() {
		MagicCardPhysical proxy = card();
		proxy.setProxy(true);
		List<Object> cards = new ArrayList<>();
		cards.add(card());
		cards.add(proxy);
		List<ICardField> fields = DeckImportPreviewPage.populatedFields(cards);
		assertEquals(1, fields.size());
		assertEquals(MagicCardField.PROXY, fields.get(0));
	}

	public void testConditionFinishPriceAndForTrade() {
		MagicCardPhysical c = card();
		c.setCondition(CardCondition.NEAR_MINT);
		c.setFinish(CardFinish.FOIL);
		c.setPrice(1.5f);
		c.setSpecialTag("fortrade");
		List<Object> cards = new ArrayList<>();
		cards.add(c);
		List<ICardField> fields = DeckImportPreviewPage.populatedFields(cards);
		assertTrue(fields.contains(MagicCardField.CONDITION));
		assertTrue(fields.contains(MagicCardField.FINISH));
		assertTrue(fields.contains(MagicCardField.PRICE));
		assertTrue(fields.contains(MagicCardField.FORTRADECOUNT));
		assertFalse(fields.contains(MagicCardField.PROXY));
	}

	public void testAProxyIsNeverForTrade() {
		MagicCardPhysical c = card();
		c.setSpecialTag("fortrade");
		c.setProxy(true);
		List<Object> cards = new ArrayList<>();
		cards.add(c);
		List<ICardField> fields = DeckImportPreviewPage.populatedFields(cards);
		assertTrue(fields.contains(MagicCardField.PROXY));
		assertFalse("For Trade is 0 for a proxy", fields.contains(MagicCardField.FORTRADECOUNT));
	}
}
