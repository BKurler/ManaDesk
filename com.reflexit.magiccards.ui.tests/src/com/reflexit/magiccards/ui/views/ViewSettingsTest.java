/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - merging a smaller viewer's layout keeps the other columns in place
 *******************************************************************************/

package com.reflexit.magiccards.ui.views;

import junit.framework.TestCase;

/**
 * How a card list saves its column layout and reads back its sort:
 * <ul>
 * <li>a viewer that knows fewer columns (a Split view) must not drop the other
 * columns' entries when it saves the shared layout
 * ({@link AbstractMagicCardsListControl#mergeColumnLayout});</li>
 * <li>a table leaves out only the sort levels of its grouping and keeps every
 * level of the user's own multi-column sort
 * ({@link AbstractMagicCardsListControl#withoutSortFields}).</li>
 * </ul>
 */
public class ViewSettingsTest extends TestCase {
	private static final String FULL = "Name:280,-Card Id:260,Set:165,-Count:45,-Comment:100,-Date:100";

	public void testMergeKeepsColumnsTheViewerDoesNotHave() {
		String split = "Name:280,-Card Id:260,Set:165";
		assertEquals(FULL, AbstractMagicCardsListControl.mergeColumnLayout(split, FULL));
	}

	public void testMergeViewerWinsForItsOwnColumns() {
		// the viewer made Card Id visible and resized Set: that is what is saved
		String split = "Name:280,Card Id:200,Set:90";
		assertEquals("Name:280,Card Id:200,Set:90,-Count:45,-Comment:100,-Date:100",
				AbstractMagicCardsListControl.mergeColumnLayout(split, FULL));
	}

	public void testMergeHiddenInStoreVisibleInViewer() {
		// "-Count" (hidden) in the store and "Count" in the viewer are the same column
		String table = "Count:45,Name:280";
		String merged = AbstractMagicCardsListControl.mergeColumnLayout(table, "-Count:45,Name:280,Set:165");
		assertEquals("Count:45,Name:280,Set:165", merged);
	}

	public void testMergeKeepsTheViewerOrderAmongItsColumns() {
		// the viewer swapped Name and Set: they swap in their slots, the others stay put
		String table = "Set:165,Name:280";
		assertEquals("Set:165,-Card Id:260,Name:280,-Count:45,-Comment:100,-Date:100",
				AbstractMagicCardsListControl.mergeColumnLayout(table, FULL));
	}

	public void testMergeNeverMovesTheColumnsTheViewerDoesNotHave() {
		// the Split view has no Count / Ownership: saving from it used to put its own
		// columns first and push Count into the middle of the shared layout
		String stored = "Count:45,Name:280,Set:165,Ownership:25,Type:200,-Card Id:260,Comment:100";
		String split = "Name:280,Set:165,Type:200,-Card Id:260";
		assertEquals(stored, AbstractMagicCardsListControl.mergeColumnLayout(split, stored));
	}

	public void testMergeSplitViewKeepsCountFirstAfterRestoreDefaults() {
		// restore defaults, then a viewer that does not know Count saves: Count stays first
		String defaults = "Count,Name,-Card Id,Set,Collector's Number,Ownership,Finish,Condition,Proxy,Type,Cost";
		String split = "Name:280,-Card Id:260,Set:165,Collector's Number:60,Finish:63,Type:200,Cost:100";
		String merged = AbstractMagicCardsListControl.mergeColumnLayout(split, defaults);
		assertTrue(merged, merged.startsWith("Count,Name:280,-Card Id:260,Set:165,Collector's Number:60,Ownership,"));
		assertEquals("Count,Name:280,-Card Id:260,Set:165,Collector's Number:60,Ownership,Finish:63,Condition,Proxy,"
				+ "Type:200,Cost:100", merged);
	}

	public void testTreeKeepsNameWhereTheSharedLayoutHasIt() {
		// the tree shows Name first on screen: its layout is "Name,Count,Set,..." but the
		// shared one has Count first and that must not change
		String treeLayout = "Name:280,Count:45,Set:165,-Card Id:260";
		String stored = "Count:45,Name:250,Set:165,-Card Id:260";
		assertEquals("Count:45,Name:280,Set:165,-Card Id:260",
				AbstractMagicCardsListControl.keepColumnSlot(treeLayout, stored, "Name"));
		// the other columns still follow the tree's order (the user dragged Set before Count)
		assertEquals("Set:165,Count:45,Name:280,-Card Id:260",
				AbstractMagicCardsListControl.keepColumnSlot("Name:280,Set:165,Count:45,-Card Id:260",
						"Count:45,Name:250,Set:165,-Card Id:260", "Name"));
	}

	public void testKeepColumnSlotWhenNameIsFirstInTheSharedLayout() {
		assertEquals("Name:280,Count:45,Set:165", AbstractMagicCardsListControl
				.keepColumnSlot("Name:280,Count:45,Set:165", "Name:250,Count:45,Set:165", "Name"));
	}

	public void testKeepColumnSlotWithoutStoredOrColumn() {
		assertEquals("Name:280,Set:165", AbstractMagicCardsListControl.keepColumnSlot("Name:280,Set:165", null, "Name"));
		assertEquals("Count:45,Set:165",
				AbstractMagicCardsListControl.keepColumnSlot("Count:45,Set:165", "Name:250,Set:165", "Name"));
	}

	public void testTreeSaveThenTableLoadKeepsCountFirst() {
		String stored = "Count:45,Name:250,Set:165,Type:200";
		String tree = AbstractMagicCardsListControl.keepColumnSlot("Name:280,Count:45,Set:165,Type:200", stored, "Name");
		assertEquals("Count:45,Name:280,Set:165,Type:200", AbstractMagicCardsListControl.mergeColumnLayout(tree, stored));
	}

	public void testMergeAppendsColumnsOnlyTheViewerKnows() {
		assertEquals("Name:280,Set:165,Extra:50",
				AbstractMagicCardsListControl.mergeColumnLayout("Extra:50,Name:280", "Name:280,Set:165"));
	}

	public void testMergeToleratesDuplicatesAndBlanks() {
		assertEquals("Name:280,Set:165", AbstractMagicCardsListControl.mergeColumnLayout("Name:280, ,Name:100",
				"Name:50,,Set:165,Name:60"));
	}

	public void testMergeWithNothingStored() {
		assertEquals("Name:280", AbstractMagicCardsListControl.mergeColumnLayout("Name:280", null));
		assertEquals("Name:280", AbstractMagicCardsListControl.mergeColumnLayout("Name:280", ""));
	}

	public void testMergeSameColumnsIsUnchanged() {
		assertEquals(FULL, AbstractMagicCardsListControl.mergeColumnLayout(FULL, FULL));
	}

	public void testTableKeepsAMultiColumnSort() {
		// three header clicks: Name, then Collector's Number, then Set - all kept
		assertEquals("NAME^/COLLNUM^/SET^/",
				AbstractMagicCardsListControl.withoutSortFields("NAME^/COLLNUM^/SET^/", ""));
		assertEquals("SET^/NAMEv/FINISH^/COLLNUMv/",
				AbstractMagicCardsListControl.withoutSortFields("SET^/NAMEv/FINISH^/COLLNUMv/", null));
	}

	public void testTableLeavesOutTheGroupingLevels() {
		assertEquals("COLLNUMv/SETv/",
				AbstractMagicCardsListControl.withoutSortFields("COLLNUMv/SETv/CMCv/", "CMC"));
		assertEquals("NAME^/", AbstractMagicCardsListControl.withoutSortFields("TYPEv/NAME^/CMC^/", "TYPE/CMC"));
	}

	public void testTableKeepsTheDirections() {
		assertEquals("NAMEv/SET^/", AbstractMagicCardsListControl.withoutSortFields("NAMEv/COST^/SET^/", "COST"));
	}

	public void testTableGroupNotInTheSort() {
		assertEquals("SET^/NAME^/", AbstractMagicCardsListControl.withoutSortFields("SET^/NAME^/", "COST"));
	}

	public void testTableOnlyGroupingLevels() {
		assertEquals("", AbstractMagicCardsListControl.withoutSortFields("CMCv/", "CMC"));
	}

	public void testTableNoSort() {
		assertEquals("", AbstractMagicCardsListControl.withoutSortFields("", "CMC"));
		assertNull(AbstractMagicCardsListControl.withoutSortFields(null, "CMC"));
	}

	public void testTableFieldNameIsNotAPrefixMatch() {
		// grouping by SET must not remove SET_CORE (a different field)
		assertEquals("SET_COREv/", AbstractMagicCardsListControl.withoutSortFields("SET_COREv/SET^/", "SET"));
	}
}
