/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia. All rights reserved. This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - testFindElementPrefersLeafOverSameNamedFolder() /
 *                         testFindCardCollectionByIdPrefersLeafOverSameNamedFolder():
 *                         lock in the leaf-over-folder fix in
 *                         CardOrganizer#findElement()
 */
package com.reflexit.magiccards.core.model.nav;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.reflexit.magiccards.core.DataManager;
// import static org.junit.Assert.assertEquals;
// import static org.junit.Assert.assertTrue;
import com.reflexit.magiccards.core.model.events.CardEvent;
import com.reflexit.magiccards.core.model.events.ICardEventListener;
import com.reflexit.unittesting.TestFileUtils;

import junit.framework.TestCase;

@FixMethodOrder(MethodSorters.JVM)
public class CardOrganizerTest extends TestCase {
	private ModelRoot root;

	@BeforeClass
	public static void beforeClass() {
		TestFileUtils.resetDb();
	}

	@Override
	@Before
	public void setUp() throws Exception {
		root = DataManager.getInstance().getModelRoot();
	}

	/**
	 * Test method for
	 * {@link com.reflexit.magiccards.core.model.nav.CardOrganizer#findElement(org.eclipse.core.runtime.IPath)}
	 * .
	 */
	@Test
	public void testFindElement() {
		CardElement element = this.root.findElement(new LocationPath("/Decks"));
		assertEquals(this.root.getDeckContainer(), element);
	}

	@Test
	public void testFindElement2() {
		CollectionsContainer decks = this.root.getDeckContainer();
		CollectionsContainer con = decks.addCollectionsContainer("cox");
		CardElement element = this.root.findElement(new LocationPath("/Decks/cox"));
		assertEquals(con, element);
	}

	/**
	 * Test method for
	 * {@link com.reflexit.magiccards.core.model.nav.CardElement#fireEvent(com.reflexit.magiccards.core.model.events.CardEvent)}
	 * .
	 */
	/**
	 * Regression: {@code CardElement#getName()} (= {@code LocationPath#getBaseName()})
	 * strips the file extension, so a deck/collection "collide.xml" and a
	 * folder "collide" in the same parent both answer "collide" to
	 * {@code getName()} - the name-matching branch {@link CardOrganizer#findElement}
	 * actually uses for a nested lookup (not the id-based
	 * {@code LocationPath#equals()} one, which never matches past the first
	 * path segment) used to return whichever of the two it iterated first.
	 * The leaf file must always win. Uses the {@code CardCollection}
	 * constructor directly (not {@code addDeck()}, which also calls
	 * {@code getStorageInfo()} - needs the full card-DB runtime this
	 * standalone-model test doesn't have) so this test actually runs here.
	 */
	@Test
	public void testFindElementPrefersLeafOverSameNamedFolder() {
		CollectionsContainer decks = this.root.getDeckContainer();
		new CardCollection("collide.xml", decks, true, false, false);
		decks.addCollectionsContainer("collide");
		CardElement found = this.root.findElement(new LocationPath("/Decks/collide"));
		assertTrue("the leaf deck must win over the same-named folder, not resolve to the folder",
				found instanceof CardCollection);
	}

	@Test
	public void testFindCardCollectionByIdPrefersLeafOverSameNamedFolder() {
		CollectionsContainer decks = this.root.getDeckContainer();
		new CardCollection("collide2.xml", decks, true, false, false);
		decks.addCollectionsContainer("collide2");
		CardCollection found = this.root.findCardCollectionById("Decks/collide2");
		assertNotNull("must resolve to the deck, not fail because of the same-named folder", found);
	}

	@Test
	public void testFireEvent() {
		final boolean res[] = new boolean[1];
		CollectionsContainer deckContainer = this.root.getDeckContainer();
		deckContainer.addListener(new ICardEventListener() {
			@Override
			public void handleEvent(CardEvent event) {
				res[0] = true;
			}
		});
		deckContainer.addDeck("test", true, false);
		assertTrue("Event is not received", res[0]);
	}
}
