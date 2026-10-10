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
package com.reflexit.magiccards.core.model.nav;

import java.util.Arrays;
import java.util.Collections;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.CollectionType;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.OwnershipRules;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;
import com.reflexit.unittesting.TestFileUtils;

import junit.framework.TestCase;

public class CollectionTypeTest extends TestCase {
	private static boolean reset = true;
	private static int n = 0;
	private DataManager dm;
	private CardCollection a;
	private CardCollection b;

	@Override
	protected void setUp() throws Exception {
		if (reset) {
			TestFileUtils.resetDb();
			reset = false;
		}
		dm = DataManager.getInstance();
		dm.waitForInit(10);
		dm.getLibraryCardStore();
	}

	@Override
	protected void tearDown() throws Exception {
		if (a != null)
			a.remove();
		if (b != null)
			b.remove();
		a = b = null;
		super.tearDown();
	}

	private CardCollection create(CollectionType type) {
		n++;
		CardCollection cc = new CardCollection("ctype" + n + ".xml", dm.getModelRoot().getCollectionsContainer(), false,
				type.isVirtual(), false);
		cc.persistInitialSettings(type, false);
		return cc;
	}

	/** Any base card - the rules under test do not depend on which one. */
	private IMagicCard anyCard() {
		for (Object o : dm.getMagicDBStore())
			return (IMagicCard) o;
		MagicCard mc = new MagicCard();
		mc.setName("Test Card");
		mc.setCardId("collection-type-test");
		return mc;
	}

	private MagicCardPhysical add(CardCollection cc, boolean own) {
		IMagicCard base = anyCard();
		MagicCardPhysical c = new MagicCardPhysical(base, cc.getLocation());
		c.setOwn(own);
		c.setCount(1);
		dm.add(c);
		return c;
	}

	public void testResolve() {
		assertEquals(CollectionType.FOR_TRADE, CollectionType.resolve("FOR_TRADE", false));
		assertEquals(CollectionType.WISH, CollectionType.resolve("WISH", true));
		// legacy (no key) follows the virtual flag
		assertEquals(CollectionType.STANDARD, CollectionType.resolve(null, false));
		assertEquals(CollectionType.WISH, CollectionType.resolve(null, true));
		// inconsistent key loses to the flag
		assertEquals(CollectionType.WISH, CollectionType.resolve("FOR_TRADE", true));
		assertEquals(CollectionType.STANDARD, CollectionType.resolve("WISH", false));
		assertNull(CollectionType.fromKey("bogus"));
	}

	public void testTypeDrivesVirtualAndPersists() {
		a = create(CollectionType.FOR_TRADE);
		assertFalse(a.isVirtual());
		assertEquals(CollectionType.FOR_TRADE, a.getCollectionType());
		a.close();
		assertEquals("type not persisted", CollectionType.FOR_TRADE, a.getCollectionType());

		b = create(CollectionType.WISH);
		assertTrue(b.isVirtual());
		assertEquals(CollectionType.WISH, b.getCollectionType());

		IStorageInfo info = a.getStorageInfo();
		info.setCollectionType(CollectionType.WISH);
		assertTrue(a.isVirtual());
		info.setCollectionType(CollectionType.STANDARD);
		assertFalse(a.isVirtual());
	}

	public void testDeckHasNoCollectionType() {
		CardCollection deck = dm.getModelRoot().getDeckContainer().addDeck("ctype-deck" + (++n), true, true);
		try {
			assertNull(deck.getCollectionType());
			assertNull(deck.getStorageInfo().getCollectionType());
		} finally {
			deck.remove();
		}
	}

	public void testCannotSetOwnedCardVirtualInStandard() {
		a = create(CollectionType.STANDARD);
		MagicCardPhysical owned = add(a, true);
		assertFalse(OwnershipRules.canSetOwn(owned, false));
		assertTrue(OwnershipRules.canSetOwn(owned, true));
		// a legacy virtual card stays allowed, and can be set to Own
		MagicCardPhysical legacy = new MagicCardPhysical(owned, a.getLocation());
		legacy.setOwn(false);
		assertTrue(OwnershipRules.canSetOwn(legacy, false));
		assertTrue(OwnershipRules.canSetOwn(legacy, true));
	}

	public void testWishCardCannotBeSetOwn() {
		// ownership follows the list: a wished card becomes Own by being moved
		// to a Standard collection, not through the Own column
		b = create(CollectionType.WISH);
		MagicCardPhysical wished = add(b, false);
		assertFalse(OwnershipRules.canSetOwn(wished, true));
		assertTrue(OwnershipRules.canSetOwn(wished, false));
		// a legacy owned card in a virtual list keeps its value, but can only
		// be set to virtual
		MagicCardPhysical legacy = new MagicCardPhysical(wished, b.getLocation());
		legacy.setOwn(true);
		assertTrue(OwnershipRules.canSetOwn(legacy, true));
		assertTrue(OwnershipRules.canSetOwn(legacy, false));
	}

	public void testMoveVirtualIntoStandardBecomesOwn() {
		// the "bought / printed" path: moving a wished card into a Standard
		// collection makes it Own (the UI confirms first)
		a = create(CollectionType.STANDARD);
		b = create(CollectionType.WISH);
		MagicCardPhysical wished = add(b, false);
		assertTrue(OwnershipRules.becomesOwnOnMove(wished, a.getStore()));
		assertNull(OwnershipRules.moveVeto(wished, a.getStore()));
		assertTrue(dm.moveCards(Collections.<IMagicCard> singletonList(wished), a.getStore()));
		assertEquals(1, countOwned(a, true));
		assertEquals(0, countOwned(a, false));
		assertEquals(0, b.getStore().size());
	}

	public void testEnforceMainCollection() {
		b = create(CollectionType.WISH);
		IStorageInfo info = b.getStorageInfo();
		info.setUnsorted(true);
		info.setReadOnly(true);
		OwnershipRules.enforceMainCollection(info);
		assertEquals(CollectionType.STANDARD, info.getCollectionType());
		assertFalse(info.isVirtual());
		assertFalse(info.isUnsorted());
		assertFalse(info.isReadOnly());
	}

	public void testMoveOwnedIntoWishRefused() {
		a = create(CollectionType.STANDARD);
		b = create(CollectionType.WISH);
		MagicCardPhysical owned = add(a, true);
		try {
			dm.moveCards(Collections.<IMagicCard> singletonList(owned), b.getStore());
			fail("owned card moved into a Wish collection");
		} catch (MagicException expected) {
			assertEquals(OwnershipRules.NO_MOVE_OWNED_TO_VIRTUAL, expected.getMessage());
		}
	}

	public void testCopyRules() {
		a = create(CollectionType.STANDARD);
		b = create(CollectionType.WISH);
		MagicCardPhysical owned = add(a, true);
		MagicCardPhysical wished = add(b, false);
		// owned -> Standard: refused (counted twice)
		try {
			dm.copyCards(Collections.<IMagicCard> singletonList(owned), a.getStore());
			fail("owned card copied into a Standard collection");
		} catch (MagicException expected) {
			assertEquals(OwnershipRules.NO_COPY_OWNED_TO_OWNED, expected.getMessage());
		}
		// owned -> Wishlist/To Print: allowed, the copy is virtual
		assertTrue(dm.copyCards(Collections.<IMagicCard> singletonList(owned), b.getStore()));
		assertEquals(2, countOwned(b, false)); // merged with the wished copy
		assertEquals(0, countOwned(b, true));
		// virtual -> Standard: allowed (bought / printed), the copy is owned
		assertTrue(dm.copyCards(Collections.<IMagicCard> singletonList(wished), a.getStore()));
		assertEquals(0, countOwned(a, false));
	}

	/** Number of copies in {@code cc} whose ownership is {@code own}. */
	private int countOwned(CardCollection cc, boolean own) {
		int n = 0;
		for (Object o : cc.getStore())
			if (o instanceof MagicCardPhysical && ((MagicCardPhysical) o).isOwn() == own)
				n += ((MagicCardPhysical) o).getCount();
		return n;
	}

	public void testMainCollectionFileRenamed() throws Exception {
		// an existing workspace has Collections/main.xml: it becomes Main.xml
		java.io.File dir = java.nio.file.Files.createTempDirectory("ctype-main").toFile();
		java.io.File coll = new java.io.File(dir, "Collections");
		assertTrue(coll.mkdirs());
		assertTrue(new java.io.File(coll, "main.xml").createNewFile());
		ModelRoot root = ModelRoot.getInstance(dir);
		assertEquals("Main", root.getDefaultLib().getName());
		java.util.List<String> names = Arrays.asList(coll.list());
		assertTrue(names.toString(), names.contains("Main.xml"));
		assertFalse(names.toString(), names.contains("main.xml"));
		// a fresh workspace gets Main.xml directly
		java.io.File dir2 = java.nio.file.Files.createTempDirectory("ctype-main2").toFile();
		assertEquals("Main", ModelRoot.getInstance(dir2).getDefaultLib().getName());
	}

	public void testImportOwnershipFollowsDestination() {
		// non-virtual destination: a file-virtual card becomes Own + Proxy
		com.reflexit.magiccards.core.exports.ImportData data = new com.reflexit.magiccards.core.exports.ImportData(
				false, null, "");
		data.setOwnershipFixed(true);
		MagicCardPhysical virt = new MagicCardPhysical(anyCard(), null);
		virt.setOwn(false);
		virt.setCount(3);
		MagicCardPhysical own = new MagicCardPhysical(anyCard(), null);
		own.setOwn(true);
		data.applyFixedOwnership(virt);
		data.applyFixedOwnership(own);
		assertTrue(virt.isOwn());
		assertTrue(virt.isProxy());
		assertFalse(own.isProxy());
		assertEquals(3, data.getForcedOwnProxy());
		assertEquals(0, data.getForcedVirtual());
		// virtual destination: a file-Own card becomes virtual, no Proxy
		data.clear();
		data.setVirtual(true);
		MagicCardPhysical own2 = new MagicCardPhysical(anyCard(), null);
		own2.setOwn(true);
		data.applyFixedOwnership(own2);
		assertFalse(own2.isOwn());
		assertFalse(own2.isProxy());
		assertEquals(1, data.getForcedVirtual());
		assertEquals(0, data.getForcedOwnProxy());
	}

	public void testCollectionTypeVeto() {
		MagicCardPhysical own = new MagicCardPhysical(anyCard(), null);
		own.setOwn(true);
		MagicCardPhysical virt = new MagicCardPhysical(own, null);
		virt.setOwn(false);
		assertNull(OwnershipRules.collectionTypeVeto(Arrays.asList(own), CollectionType.FOR_TRADE));
		assertNotNull(OwnershipRules.collectionTypeVeto(Arrays.asList(own), CollectionType.WISH));
		assertNotNull(OwnershipRules.collectionTypeVeto(Arrays.asList(virt), CollectionType.STANDARD));
		assertNull(OwnershipRules.collectionTypeVeto(Arrays.asList(virt), CollectionType.WISH));
		assertNull(OwnershipRules.collectionTypeVeto(Collections.emptyList(), CollectionType.WISH));
	}
}
