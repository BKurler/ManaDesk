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
package com.reflexit.magiccards.core.model;

import org.junit.Assert;
import org.junit.Test;

/** The deck's third list: {@code -extra} locations, next to {@code -sideboard}. */
public class LocationExtraTest {

	@Test
	public void extraSuffixRoundTrips() {
		Location main = Location.valueOf("Decks/mydeck");
		Location acc = main.toExtra();
		Assert.assertEquals("Decks/mydeck-extra", acc.toString());
		Assert.assertTrue(acc.isExtra());
		Assert.assertFalse(acc.isSideboard());
		Assert.assertEquals(main, acc.toMainDeck());
	}

	@Test
	public void sideboardAndExtraAreDistinct() {
		Location main = Location.valueOf("Decks/mydeck");
		Assert.assertNotEquals(main.toSideboard(), main.toExtra());
		Assert.assertFalse(main.toSideboard().isExtra());
		Assert.assertFalse(main.toExtra().isSideboard());
	}

	@Test
	public void conversionsAreIdempotentAndCrossNavigable() {
		Location main = Location.valueOf("Decks/x");
		Assert.assertEquals(main.toExtra(), main.toExtra().toExtra());
		// from the sideboard you can still reach the extra list of the same deck
		Assert.assertEquals("Decks/x-extra", main.toSideboard().toExtra().toString());
		Assert.assertEquals("Decks/x-sideboard", main.toExtra().toSideboard().toString());
		Assert.assertEquals(main, main.toExtra().toMainDeck().toMainDeck());
	}

	@Test
	public void plainDeckIsNeitherSideboardNorExtra() {
		Location main = Location.valueOf("Decks/x");
		Assert.assertFalse(main.isSideboard());
		Assert.assertFalse(main.isExtra());
		Assert.assertEquals(main, main.toMainDeck());
	}
}
