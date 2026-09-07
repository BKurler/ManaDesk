/*
 * Contributors:
 *     Rémi Dutil (2026) - created for the in-place "split" feature
 */
package com.reflexit.magiccards.core.model.storage;

import java.io.File;
import java.util.List;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.xml.CollectionSingleFileCardStore;
import com.reflexit.unittesting.CardGenerator;

import junit.framework.TestCase;

/**
 * {@link CollectionCardStore#addRightAfter} - the primitive behind an in-place
 * "Split Pile" / "Split &amp; move": the source pile keeps its exact list
 * position and the new sibling pile is inserted directly behind it, never at the
 * end and never re-sorted.
 */
public class CollectionCardStoreTest extends TestCase {
	private CollectionSingleFileCardStore store;
	private File tempFile;

	@Override
	protected void setUp() throws Exception {
		tempFile = File.createTempFile("coll", ".xml");
		tempFile.deleteOnExit();
		store = new CollectionSingleFileCardStore(tempFile, Location.valueOf("Collections/aaa.xml"));
		store.setType(IStorageInfo.COLLECTION_TYPE);
	}

	@Override
	protected void tearDown() throws Exception {
		tempFile.delete();
		super.tearDown();
	}

	/** Adds a distinct card named {@code name} with {@code count} copies and returns the stored pile. */
	private MagicCardPhysical add(String name, int count) {
		MagicCard mc = CardGenerator.generateRandomCard();
		mc.setName(name);
		MagicCardPhysical p = new MagicCardPhysical(mc, store.getLocation());
		p.setCount(count);
		store.add(p);
		for (IMagicCard c : store.getCards())
			if (name.equals(c.getName()))
				return (MagicCardPhysical) c;
		throw new IllegalStateException("not stored: " + name);
	}

	private List<String> names() {
		List<IMagicCard> cards = store.getCards();
		java.util.ArrayList<String> res = new java.util.ArrayList<>();
		for (IMagicCard c : cards)
			res.add(c.getName());
		return res;
	}

	public void testNewPileGoesRightAfterTheAnchorNotAtTheEnd() {
		add("A", 1);
		MagicCardPhysical b = add("B", 5);
		add("C", 1);
		assertEquals(java.util.Arrays.asList("A", "B", "C"), names());

		MagicCardPhysical newPile = store.addRightAfter(b, 2);

		// B still where it was, C pushed down by one, new pile between them
		assertEquals(java.util.Arrays.asList("A", "B", "B", "C"), names());
		assertSame(newPile, store.getCards().get(2));
		assertEquals(2, newPile.getCount());
		// addRightAfter does not touch the anchor's count (the caller lowers it)
		assertEquals(5, b.getCount());
		assertSame("anchor instance must not be replaced", b, store.getCards().get(1));
	}

	public void testAnchorAtTheEndStillGetsASiblingRightAfterIt() {
		add("A", 1);
		MagicCardPhysical last = add("Z", 3);

		MagicCardPhysical newPile = store.addRightAfter(last, 1);

		assertEquals(java.util.Arrays.asList("A", "Z", "Z"), names());
		assertSame(newPile, store.getCards().get(2));
		assertEquals(3, last.getCount());
	}

	public void testTheNewPileIsRegisteredForLookupById() {
		MagicCardPhysical b = add("B", 4);
		MagicCardPhysical newPile = store.addRightAfter(b, 1);
		// both piles are now retrievable under the same card id
		assertEquals(2, store.getCards(b.getCardId()).size());
		assertTrue(store.getCards(b.getCardId()).contains(newPile));
	}
}
