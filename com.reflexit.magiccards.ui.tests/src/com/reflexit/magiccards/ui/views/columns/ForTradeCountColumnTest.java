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
package com.reflexit.magiccards.ui.views.columns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;

/**
 * The For Trade column only shows the count of the piles tagged "fortrade": it has no editor, and it shows the whole
 * count of a tagged pile, 0 for an untagged one or a proxy. Plain JUnit (no display needed).
 */
public class ForTradeCountColumnTest {
	private static MagicCardPhysical pile(int count) {
		MagicCard base = new MagicCard();
		base.setName("Plains");
		MagicCardPhysical c = new MagicCardPhysical(base, null);
		c.setCount(count);
		return c;
	}

	@Test
	public void columnIsReadOnly() {
		ForTradeCountColumn column = new ForTradeCountColumn();
		assertNull("no editor", column.getEditingSupport(null));
		assertFalse(column.canEditElement(pile(3)));
	}

	@Test
	public void showsTheWholePileWhenTagged() {
		ForTradeCountColumn column = new ForTradeCountColumn();
		MagicCardPhysical c = pile(3);
		assertEquals("0", column.getText(c));
		c.setSpecialTag("fortrade");
		assertEquals("3", column.getText(c));
		c.setProxy(true);
		assertEquals("a proxy is never for trade", "0", column.getText(c));
	}
}
