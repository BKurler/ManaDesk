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
package com.reflexit.magiccards.core.model.xml;

import java.util.ArrayList;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.xml.CardCollectionStoreObject;

/**
 * The digital-only cards kept out of a set's card list while the digital support
 * is off must still be written back with the set file, or a later save of a mixed
 * set (paper + digital-only cards) would silently delete them. Plain JUnit.
 */
public class HiddenDigitalCardsTest extends TestCase {

	private static MagicCard card(String id, String digital) {
		MagicCard c = new MagicCard();
		c.setCardId(id);
		c.set(MagicCardField.DIGITAL, digital);
		return c;
	}

	private java.io.File dir;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		dir = java.nio.file.Files.createTempDirectory("hidden-digital-").toFile();
	}

	@Override
	protected void tearDown() throws Exception {
		java.io.File[] files = dir.listFiles();
		if (files != null)
			for (java.io.File f : files)
				f.delete();
		dir.delete();
		super.tearDown();
	}

	/** A storage on a file in this test's own temp folder (saved there when it changes, removed in tearDown). */
	private SingleFileCardStorage storage() {
		return new SingleFileCardStorage(new java.io.File(dir, "set.xml"), Location.createLocationFromSet("Test Set"));
	}

	private static CardCollectionStoreObject saved(SingleFileCardStorage storage) {
		CardCollectionStoreObject obj = new CardCollectionStoreObject();
		storage.storeFields(obj);
		return obj;
	}

	public void testHiddenCardsAreWrittenBackButNotListed() {
		SingleFileCardStorage storage = storage();
		storage.add(card("1", null));
		storage.stashHidden(card("2", "true"));

		assertEquals("not in the list", 1, storage.size());
		assertEquals(1, storage.getHiddenCount());
		assertEquals("but in the file", 2, saved(storage).list.size());
	}

	public void testStashingTheSameCardAgainReplacesIt() {
		SingleFileCardStorage storage = storage();
		MagicCard first = card("2", "true");
		MagicCard second = card("2", "true");
		storage.stashHidden(first);
		storage.stashHidden(second);

		assertEquals(1, storage.getHiddenCount());
		ArrayList<IMagicCard> list = new ArrayList<>(saved(storage).list);
		assertSame("an update replaces the old record", second, list.get(0));
	}

	public void testNothingHiddenSavesJustTheList() {
		SingleFileCardStorage storage = storage();
		storage.add(card("1", null));
		assertEquals(0, storage.getHiddenCount());
		assertEquals(1, saved(storage).list.size());
	}
}
