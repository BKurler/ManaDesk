/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.model.nav;

import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;
import com.reflexit.unittesting.TestFileUtils;

import junit.framework.TestCase;

/**
 * A brand-new deck/collection must keep the type / virtual / unsorted flags
 * chosen at creation time. Regression: the "New" wizard only stored them as
 * transient fields on {@link CardCollection} and left the XML file empty, so
 * {@link CardCollection#associate} reset them to the empty file's defaults and
 * the user had to reopen the element and set everything again.
 */
@FixMethodOrder(MethodSorters.JVM)
public class NewCardCollectionSettingsTest extends TestCase {
	private static boolean reset = true;
	private DataManager dm;
	private CardCollection created;

	private static void init() {
		if (reset) {
			TestFileUtils.resetDb();
			reset = false;
		}
	}

	@Override
	protected void setUp() throws Exception {
		init();
		dm = DataManager.getInstance();
		dm.waitForInit(10);
		dm.getLibraryCardStore();
	}

	@Override
	protected void tearDown() throws Exception {
		if (created != null)
			created.remove();
		created = null;
		super.tearDown();
	}

	/** Mimics {@code NewDeckWizard} / {@code NewCardCollectionWizard.doCreateCardElement}. */
	private CardCollection wizardCreate(CollectionsContainer parent, String name, boolean deck, boolean virtual,
			boolean unsorted) {
		CardCollection c = new CardCollection(name + ".xml", parent, deck, virtual, unsorted);
		c.persistInitialSettings(deck, virtual, unsorted);
		return c;
	}

	public void testDeckVirtualPersisted() {
		created = wizardCreate(dm.getModelRoot().getDeckContainer(), "wiz-deck", true, true, false);

		// visible right away
		assertTrue(created.isDeck());
		assertTrue(created.isVirtual());

		// and survives a close/reopen - i.e. it really went into the file, not
		// just the transient fields
		created.close();
		assertTrue("type not persisted", created.isDeck());
		assertTrue("virtual not persisted", created.isVirtual());
		assertEquals(IStorageInfo.DECK_TYPE, created.getStorageInfo().getType());
	}

	/** "unsorted" (a manual card order) is a collection-only notion - a deck must
	 * never come out unsorted no matter what the caller asks for. */
	public void testDeckCannotBeUnsorted() {
		created = wizardCreate(dm.getModelRoot().getDeckContainer(), "wiz-deck-us", true, true, true);
		assertFalse("a deck must not be unsorted", created.isUnsorted());
		created.close();
		assertFalse("a deck must not be unsorted after reopen", created.isUnsorted());
	}

	public void testCollectionSortedNonVirtual() {
		created = wizardCreate(dm.getModelRoot().getCollectionsContainer(), "wiz-coll", false, false, false);

		created.close();
		assertFalse(created.isDeck());
		assertFalse(created.isVirtual());
		assertFalse(created.isUnsorted());
		assertEquals(IStorageInfo.COLLECTION_TYPE, created.getStorageInfo().getType());
	}

	/** A collection, unlike a deck, keeps the "unsorted" flag. */
	public void testCollectionCanBeUnsorted() {
		created = wizardCreate(dm.getModelRoot().getCollectionsContainer(), "wiz-coll-us", false, false, true);
		assertTrue(created.isUnsorted());
		created.close();
		assertTrue("unsorted not persisted for collection", created.isUnsorted());
	}

	/** {@link ModelRoot#sideOf} classifies elements by which half of the tree
	 *  they sit in - the basis for the side-locked New Deck / New Collection UI. */
	public void testSideOf() {
		ModelRoot root = dm.getModelRoot();
		assertNull(root.sideOf(null));
		assertNull(root.sideOf(root));
		assertNull(root.sideOf(root.getMyCardsContainer()));
		assertNull(root.sideOf(root.getMagicDBContainer()));
		assertEquals(ModelRoot.Side.DECK, root.sideOf(root.getDeckContainer()));
		assertEquals(ModelRoot.Side.COLLECTION, root.sideOf(root.getCollectionsContainer()));

		created = wizardCreate(root.getDeckContainer(), "side-deck", true, true, false);
		assertEquals(ModelRoot.Side.DECK, root.sideOf(created));
		assertSame(root.getDeckContainer(), root.containerFor(ModelRoot.Side.DECK));
		assertSame(root.getCollectionsContainer(), root.containerFor(ModelRoot.Side.COLLECTION));

		CollectionsContainer sub = root.getCollectionsContainer().addCollectionsContainer("side-sub");
		assertEquals(ModelRoot.Side.COLLECTION, root.sideOf(sub));
	}

	/** Renaming a deck carries its sideboard sibling along. */
	public void testRenameWithRelated() {
		CollectionsContainer decks = dm.getModelRoot().getDeckContainer();
		created = wizardCreate(decks, "ren-deck", true, true, false);
		decks.addDeck(created.getLocation().toSideboard().getBaseFileName(), true, true);
		assertNotNull(decks.findChieldByName("ren-deck-sideboard.xml"));

		created.renameWithRelated("ren-deck-2");

		assertEquals("ren-deck-2", created.getName());
		assertNotNull("sideboard followed the rename", decks.findChieldByName("ren-deck-2-sideboard.xml"));
		assertNull(decks.findChieldByName("ren-deck-sideboard.xml"));

		// tearDown removes `created`; clean the renamed sideboard too
		CardElement sb = decks.findChieldByName("ren-deck-2-sideboard.xml");
		if (sb != null)
			sb.remove();
	}

	/** Moving a deck must always carry its sideboard and extra along - no
	 *  matter which of the three the user actually selected. */
	public void testMoveCarriesSideboardAndExtra() {
		CollectionsContainer decks = dm.getModelRoot().getDeckContainer();
		CollectionsContainer target = decks.addCollectionsContainer("move-target");
		try {
			assertMoveCarriesFamily(decks, target, "mv-main", 0); // select the main deck
			assertMoveCarriesFamily(decks, target, "mv-sb", 1); // select the sideboard
			assertMoveCarriesFamily(decks, target, "mv-ex", 2); // select the extra list
		} finally {
			target.remove();
		}
	}

	/** Creates {@code name} + its sideboard + its extra under {@code decks}, moves
	 *  the family into {@code target} by selecting only the member at
	 *  {@code selectIndex} (0=main, 1=sideboard, 2=extra), and asserts all three
	 *  land under {@code target} together. */
	private void assertMoveCarriesFamily(CollectionsContainer decks, CollectionsContainer target, String name,
			int selectIndex) {
		created = wizardCreate(decks, name, true, true, false);
		CardCollection sideboard = decks.addDeck(created.getLocation().toSideboard().getBaseFileName(), true, true);
		CardCollection extra = decks.addDeck(created.getLocation().toExtra().getBaseFileName(), true, true);
		CardElement[] family = { created, sideboard, extra };

		dm.getModelRoot().move(new CardElement[] { family[selectIndex] }, target);

		assertNotNull(name + ": main followed the move", target.findChieldByName(name + ".xml"));
		assertNotNull(name + ": sideboard followed the move", target.findChieldByName(name + "-sideboard.xml"));
		assertNotNull(name + ": extra followed the move", target.findChieldByName(name + "-extra.xml"));
		assertNull(name + ": main left the source", decks.findChieldByName(name + ".xml"));
		assertNull(name + ": sideboard left the source", decks.findChieldByName(name + "-sideboard.xml"));
		assertNull(name + ": extra left the source", decks.findChieldByName(name + "-extra.xml"));

		// tearDown only removes `created` (now under target, still fine to remove
		// from there) - the sideboard/extra moved alongside it need their own cleanup
		CardElement sb = target.findChieldByName(name + "-sideboard.xml");
		if (sb != null)
			sb.remove();
		CardElement ex = target.findChieldByName(name + "-extra.xml");
		if (ex != null)
			ex.remove();
	}
}
