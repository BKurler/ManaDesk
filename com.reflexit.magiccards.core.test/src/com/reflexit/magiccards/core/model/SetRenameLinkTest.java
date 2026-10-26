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

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.unittesting.TestFileUtils;

import junit.framework.TestCase;

/**
 * A card of a set not released yet keeps its Scryfall id when the set is released, so a copy of it in a deck
 * or collection follows it even if Scryfall renames the set between the preview and the release. Plain JUnit,
 * with the core test database fixtures.
 */
public class SetRenameLinkTest extends TestCase {
	private static boolean reset = true;
	private static int n = 0;
	private DataManager dm;
	private CardCollection list;
	private MagicCard preview;

	@Override
	protected void setUp() throws Exception {
		if (reset) {
			TestFileUtils.resetDb();
			reset = false;
		}
		dm = DataManager.getInstance();
		dm.waitForInit(10);
		dm.getLibraryCardStore();
		n++;
		list = new CardCollection("setrename" + n + ".xml", dm.getModelRoot().getCollectionsContainer(), false, false,
				false);
		list.persistInitialSettings(CollectionType.STANDARD, false);
	}

	@Override
	protected void tearDown() throws Exception {
		list.remove();
		if (preview != null)
			dm.getMagicDBStore().remove(preview);
		super.tearDown();
	}

	private static MagicCard printing(String id, String name, String set, String num) {
		MagicCard c = new MagicCard();
		c.setCardId(id);
		c.setName(name);
		c.setSet(set);
		c.setCollNumber(num);
		return c;
	}

	public void testACopyFollowsTheCardWhenItsSetIsRenamed() {
		preview = printing("rn-" + n, "Rename Test Card", "Preview Set Name", "9");
		dm.getMagicDBStore().add(preview);
		MagicCardPhysical copy = new MagicCardPhysical(preview, list.getLocation());
		copy.setCount(1);
		copy.setOwn(true);
		dm.add(copy);
		MagicCardPhysical stored = (MagicCardPhysical) dm.getLibraryCardStore().getCards("rn-" + n).iterator().next();
		assertEquals("Preview Set Name", stored.getSet());

		// the card database update: the same id, now with the released set's name
		dm.getMagicDBStore().add(printing("rn-" + n, "Rename Test Card", "Released Set Name", "9"));
		dm.reconcile();

		MagicCard dbCard = (MagicCard) dm.getMagicDBStore().getCard("rn-" + n);
		assertNotNull(dbCard);
		stored = (MagicCardPhysical) dm.getLibraryCardStore().getCards("rn-" + n).iterator().next();
		assertSame("the copy is still linked to the database card of that id", dbCard, stored.getBase());
		assertEquals("and shows the new set name", dbCard.getSet(), stored.getSet());
	}
}
