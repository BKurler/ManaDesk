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

	public void testDeckVirtualUnsortedPersisted() {
		created = wizardCreate(dm.getModelRoot().getDeckContainer(), "wiz-deck", true, true, true);

		// visible right away
		assertTrue(created.isDeck());
		assertTrue(created.isVirtual());
		assertTrue(created.isUnsorted());

		// and survives a close/reopen - i.e. it really went into the file, not
		// just the transient fields
		created.close();
		assertTrue("type not persisted", created.isDeck());
		assertTrue("virtual not persisted", created.isVirtual());
		assertTrue("unsorted not persisted", created.isUnsorted());
		assertEquals(IStorageInfo.DECK_TYPE, created.getStorageInfo().getType());
	}

	public void testCollectionSortedNonVirtual() {
		created = wizardCreate(dm.getModelRoot().getCollectionsContainer(), "wiz-coll", false, false, false);

		created.close();
		assertFalse(created.isDeck());
		assertFalse(created.isVirtual());
		assertFalse(created.isUnsorted());
		assertEquals(IStorageInfo.COLLECTION_TYPE, created.getStorageInfo().getType());
	}
}
