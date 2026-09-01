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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.ui.views.AbstractMagicCardsListControl;
import com.reflexit.unittesting.CardGenerator;

/**
 * {@link AbstractMagicCardsListControl#unsortedCopyPosition(List, IMagicCard)} -
 * the "Position N" shown in the status line for an unsorted collection. It is
 * the physical box position: the target's 1-based place in the store's entry
 * order, counted in individual card copies.
 */
public class UnsortedCopyPositionTest {

	private static MagicCardPhysical card(String name, int count) {
		MagicCardPhysical c = CardGenerator.generatePhysicalCardWithValues();
		c.set(MagicCardField.NAME, name);
		c.setCount(count);
		return c;
	}

	@Test
	public void firstCardIsPositionOne() {
		MagicCardPhysical a = card("Alpha", 1);
		List<IMagicCard> box = new ArrayList<>(Arrays.asList(a, card("Beta", 1)));
		assertEquals(1, AbstractMagicCardsListControl.unsortedCopyPosition(box, a));
	}

	@Test
	public void countsAdvanceThePosition() {
		MagicCardPhysical a = card("Alpha", 1);
		MagicCardPhysical b = card("Beta", 3); // stack of 3 -> +3
		MagicCardPhysical g = card("Gamma", 1);
		List<IMagicCard> box = new ArrayList<>(Arrays.asList(a, b, g));
		assertEquals(1, AbstractMagicCardsListControl.unsortedCopyPosition(box, a));
		assertEquals(2, AbstractMagicCardsListControl.unsortedCopyPosition(box, b));
		assertEquals(5, AbstractMagicCardsListControl.unsortedCopyPosition(box, g)); // 1 + 3 + 1
	}

	@Test
	public void identityWinsOverValueEqualDuplicates() {
		// four value-equal count-1 "Clue" rows - clicking the 3rd must report 3,
		// not 1 (which a plain equals() scan would give).
		MagicCardPhysical proto = card("Clue", 1);
		MagicCardPhysical c0 = (MagicCardPhysical) proto.cloneCard();
		MagicCardPhysical c1 = (MagicCardPhysical) proto.cloneCard();
		MagicCardPhysical c2 = (MagicCardPhysical) proto.cloneCard();
		MagicCardPhysical c3 = (MagicCardPhysical) proto.cloneCard();
		List<IMagicCard> box = new ArrayList<>(Arrays.asList(c0, c1, c2, c3));
		assertEquals(1, AbstractMagicCardsListControl.unsortedCopyPosition(box, c0));
		assertEquals(3, AbstractMagicCardsListControl.unsortedCopyPosition(box, c2));
		assertEquals(4, AbstractMagicCardsListControl.unsortedCopyPosition(box, c3));
	}

	@Test
	public void valueEqualFallbackWhenInstanceIsDetached() {
		// no identity match (a viewer handed back a clone) -> first value-equal
		MagicCardPhysical c0 = card("Clue", 1);
		MagicCardPhysical c1 = (MagicCardPhysical) c0.cloneCard();
		List<IMagicCard> box = new ArrayList<>(Arrays.asList(c0, c1));
		MagicCardPhysical detached = (MagicCardPhysical) c1.cloneCard();
		assertEquals(1, AbstractMagicCardsListControl.unsortedCopyPosition(box, detached));
	}

	@Test
	public void notFoundIsMinusOne() {
		List<IMagicCard> box = new ArrayList<>(Arrays.asList(card("Alpha", 1)));
		assertEquals(-1, AbstractMagicCardsListControl.unsortedCopyPosition(box, card("Missing", 1)));
	}

	@Test
	public void nullArgsAreMinusOne() {
		assertEquals(-1, AbstractMagicCardsListControl.unsortedCopyPosition(null, card("Alpha", 1)));
		assertEquals(-1,
				AbstractMagicCardsListControl.unsortedCopyPosition(new ArrayList<>(), (IMagicCard) null));
	}
}
