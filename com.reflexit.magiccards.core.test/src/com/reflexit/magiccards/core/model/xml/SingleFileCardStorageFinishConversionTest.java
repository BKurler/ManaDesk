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
import java.util.List;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.xml.CardCollectionStoreObject;

/**
 * The one-shot "legacy foil tag -&gt; Finish" conversion in
 * {@link SingleFileCardStorage#loadFields}: it must run exactly once, when a
 * deck/collection's cards are loaded - never on every
 * {@link MagicCardPhysical#getFinish()} call (that live-derivation behavior
 * was deliberately removed - see {@code MagicCardPhysical}'s header).
 */
public class SingleFileCardStorageFinishConversionTest extends TestCase {

	private static MagicCardPhysical physicalWithSpecial(String special) {
		MagicCardPhysical mcp = new MagicCardPhysical(new MagicCard(), null);
		if (special != null)
			mcp.setSpecialTag(special);
		return mcp;
	}

	private static CardCollectionStoreObject storeObjectOf(IMagicCard... cards) {
		CardCollectionStoreObject obj = new CardCollectionStoreObject();
		obj.list = new ArrayList<IMagicCard>(java.util.Arrays.asList(cards));
		return obj;
	}

	public void testFoilTagIsConvertedOnceAtLoad() {
		MagicCardPhysical foilTagged = physicalWithSpecial("foil");
		assertNull("not converted yet", foilTagged.getRawFinish());

		SingleFileCardStorage storage = new SingleFileCardStorage();
		storage.loadFields(storeObjectOf(foilTagged));

		assertEquals("converted to a real, explicit Finish", CardFinish.FOIL, foilTagged.getRawFinish());
		assertEquals(CardFinish.FOIL, foilTagged.getFinish());
		// the legacy tag itself is left untouched
		assertEquals("foil", foilTagged.getSpecial());
	}

	public void testFoilTagMatchesCaseInsensitively() {
		MagicCardPhysical shouty = physicalWithSpecial("FOIL");
		SingleFileCardStorage storage = new SingleFileCardStorage();
		storage.loadFields(storeObjectOf(shouty));
		assertEquals(CardFinish.FOIL, shouty.getRawFinish());
	}

	public void testNoSpecialTagIsNotConverted() {
		MagicCardPhysical plain = physicalWithSpecial(null);
		SingleFileCardStorage storage = new SingleFileCardStorage();
		storage.loadFields(storeObjectOf(plain));
		assertNull("nothing to convert - stays Auto", plain.getRawFinish());
	}

	public void testUnrelatedSpecialTagIsNotConverted() {
		MagicCardPhysical signed = physicalWithSpecial("signed");
		SingleFileCardStorage storage = new SingleFileCardStorage();
		storage.loadFields(storeObjectOf(signed));
		assertNull(signed.getRawFinish());
	}

	public void testAlreadyExplicitFinishIsNeverOverwritten() {
		MagicCardPhysical mcp = physicalWithSpecial("foil");
		mcp.setFinish(CardFinish.ETCHED); // e.g. the user picked it by hand already
		SingleFileCardStorage storage = new SingleFileCardStorage();
		storage.loadFields(storeObjectOf(mcp));
		assertEquals("an explicit Finish is never clobbered by the conversion", CardFinish.ETCHED,
				mcp.getRawFinish());
	}

	/** The conversion runs once, at load - a load that touches nothing else
	 *  afterwards must not re-derive anything from the tag on every read. */
	public void testConversionRunsOnceNotOnEveryGetFinishCall() {
		MagicCardPhysical mcp = physicalWithSpecial("foil");
		SingleFileCardStorage storage = new SingleFileCardStorage();
		storage.loadFields(storeObjectOf(mcp));
		assertEquals(CardFinish.FOIL, mcp.getRawFinish());

		// simulate the user clearing it back to Auto after load - if getFinish()
		// still looked at the tag live, this would silently re-derive FOIL;
		// it must not
		mcp.setFinish(null);
		assertEquals("the tag is not consulted on read - Auto now derives the plain default",
				CardFinish.NONFOIL, mcp.getFinish());
	}

	public void testLoadFieldsHandlesMultipleCardsInOnePass() {
		MagicCardPhysical a = physicalWithSpecial("foil");
		MagicCardPhysical b = physicalWithSpecial(null);
		MagicCardPhysical c = physicalWithSpecial("foil,signed");
		SingleFileCardStorage storage = new SingleFileCardStorage();
		storage.loadFields(storeObjectOf(a, b, c));
		assertEquals(CardFinish.FOIL, a.getRawFinish());
		assertNull(b.getRawFinish());
		assertEquals(CardFinish.FOIL, c.getRawFinish());
	}
}
