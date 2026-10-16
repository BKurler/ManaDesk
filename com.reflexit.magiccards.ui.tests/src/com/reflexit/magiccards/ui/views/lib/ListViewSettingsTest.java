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

package com.reflexit.magiccards.ui.views.lib;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import com.reflexit.magiccards.ui.preferences.CollectionViewPreferencePage;
import com.reflexit.magiccards.ui.preferences.DeckViewPreferencePage;
import com.reflexit.magiccards.ui.preferences.PreferenceConstants;
import com.reflexit.magiccards.ui.preferences.PreferenceInitializer;

import junit.framework.TestCase;

/**
 * Each deck and collection keeps its own View As, grouping and sort
 * ({@link PreferenceInitializer#getListViewStore}), next to the shared columns
 * of its view page, and a tab knows whether it shows a deck or a collection
 * from its id alone - even before the library is loaded
 * ({@link DeckView#preferencePageIdForPath}).
 */
public class ListViewSettingsTest extends TestCase {
	/** A page id of its own, so the test never touches real settings. */
	private String page;

	@Override
	protected void setUp() {
		page = "test.listview." + System.nanoTime();
	}

	@Override
	protected void tearDown() throws BackingStoreException {
		Preferences root = InstanceScope.INSTANCE.getNode(page).parent();
		for (String name : root.childrenNames())
			if (name.startsWith(page))
				root.node(name).removeNode();
		root.flush();
	}

	public void testCollectionFromItsPath() {
		assertEquals(CollectionViewPreferencePage.class.getName(),
				DeckView.preferencePageIdForPath("Collections/StandardCollection"));
		assertEquals(CollectionViewPreferencePage.class.getName(),
				DeckView.preferencePageIdForPath("Collections/Binders/Rares"));
	}

	public void testDeckFromItsPath() {
		assertEquals(DeckViewPreferencePage.class.getName(), DeckView.preferencePageIdForPath("Decks/StandardDeck"));
		assertEquals(DeckViewPreferencePage.class.getName(),
				DeckView.preferencePageIdForPath("Decks/BoxedDeck-sideboard"));
	}

	public void testUnknownPath() {
		assertNull(DeckView.preferencePageIdForPath(null));
		assertNull(DeckView.preferencePageIdForPath("StandardDeck"));
		// a list named like a root is not under it
		assertNull(DeckView.preferencePageIdForPath("DecksAndMore/x"));
		assertNull(DeckView.preferencePageIdForPath("CollectionsOld/x"));
	}

	public void testEachListHasItsOwnSettings() {
		IPersistentPreferenceStore a = PreferenceInitializer.getListViewStore(page, "Collections/A");
		IPersistentPreferenceStore b = PreferenceInitializer.getListViewStore(page, "Collections/B");
		a.setValue(PreferenceConstants.SORT_ORDER, "NAME^/SET^/");
		a.setValue(PreferenceConstants.PRESENTATION_VIEW, "TREE");
		assertEquals("NAME^/SET^/", a.getString(PreferenceConstants.SORT_ORDER));
		assertEquals("", b.getString(PreferenceConstants.SORT_ORDER));
		assertEquals("TREE", PreferenceInitializer.getListViewStore(page, "Collections/A")
				.getString(PreferenceConstants.PRESENTATION_VIEW));
	}

	public void testNodeIsFlatNotNestedUnderTheFolder() throws BackingStoreException {
		IPersistentPreferenceStore a = PreferenceInitializer.getListViewStore(page, "Collections/A");
		a.setValue(PreferenceConstants.GROUP_FIELD, "SET");
		Preferences root = InstanceScope.INSTANCE.getNode(page).parent();
		assertTrue(root.nodeExists(page + ".deck.Collections_A.view"));
		assertEquals("SET", root.node(page + ".deck.Collections_A.view").get(PreferenceConstants.GROUP_FIELD, null));
		assertFalse("no node named after the folder", root.nodeExists(page + ".deck.Collections"));
	}

	public void testDefaultsComeFromTheSharedPage() {
		PreferenceInitializer.getLocalStore(page).setDefault(PreferenceConstants.PRESENTATION_VIEW, "TABLE");
		IPersistentPreferenceStore a = PreferenceInitializer.getListViewStore(page, "Decks/D");
		assertEquals("TABLE", a.getString(PreferenceConstants.PRESENTATION_VIEW));
	}

	public void testRestoreDefaultsResetsEveryListGroupingAndSortOnly() {
		IPersistentPreferenceStore a = PreferenceInitializer.getListViewStore(page, "Collections/A");
		IPersistentPreferenceStore b = PreferenceInitializer.getListViewStore(page, "Collections/B");
		a.setValue(PreferenceConstants.GROUP_FIELD, "SET");
		a.setValue(PreferenceConstants.SORT_ORDER, "NAME^/");
		a.setValue(PreferenceConstants.PRESENTATION_VIEW, "TREE");
		b.setValue(PreferenceConstants.SORT_ORDER, "CMCv/");
		PreferenceInitializer.resetListViewSettings(page);
		assertEquals("", a.getString(PreferenceConstants.GROUP_FIELD));
		assertEquals("", a.getString(PreferenceConstants.SORT_ORDER));
		assertEquals("", b.getString(PreferenceConstants.SORT_ORDER));
		// View As is not part of Restore Defaults
		assertEquals("TREE", a.getString(PreferenceConstants.PRESENTATION_VIEW));
	}

	public void testRestoreDefaultsLeavesOtherPagesAlone() {
		IPersistentPreferenceStore other = PreferenceInitializer.getListViewStore(page + "x", "Decks/D");
		other.setValue(PreferenceConstants.SORT_ORDER, "NAME^/");
		PreferenceInitializer.resetListViewSettings(page);
		assertEquals("NAME^/", other.getString(PreferenceConstants.SORT_ORDER));
	}
}
