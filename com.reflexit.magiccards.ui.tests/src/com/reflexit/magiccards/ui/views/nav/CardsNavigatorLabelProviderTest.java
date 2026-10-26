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
package com.reflexit.magiccards.ui.views.nav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.reflexit.magiccards.core.model.Location;

/**
 * The deck trees show a sideboard with a leading "#" and an extra list with "~", like their tabs.
 * Plain JUnit (no display needed).
 */
public class CardsNavigatorLabelProviderTest {

	@Test
	public void testSideboardStartsWithHash() {
		Location deck = Location.createLocation("MyDeck");
		assertEquals("#MyDeck-sideboard", CardsNavigatorLabelProvider.prefixed("MyDeck-sideboard", deck.toSideboard()));
	}

	@Test
	public void testExtraStartsWithTilde() {
		Location deck = Location.createLocation("MyDeck");
		assertEquals("~MyDeck-extra", CardsNavigatorLabelProvider.prefixed("MyDeck-extra", deck.toExtra()));
	}

	@Test
	public void testADeckOrCollectionIsLeftAlone() {
		Location deck = Location.createLocation("MyDeck");
		assertEquals("MyDeck", CardsNavigatorLabelProvider.prefixed("MyDeck", deck));
	}

	@Test
	public void testNothingToPrefix() {
		assertNull(CardsNavigatorLabelProvider.prefixed(null, Location.createLocation("x").toSideboard()));
		assertEquals("name", CardsNavigatorLabelProvider.prefixed("name", null));
	}
}
