/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - registered DeckExportPageSelectionTest
 *     Rémi Dutil (2026) - registered EditCardsPropertiesDialogFinishTest
 *     Rémi Dutil (2026) - registered DeckColumnCollectionTest
 *     Rémi Dutil (2026) - registered BrowseWebsiteLiveTest, by explicit
 *                         request - unlike every other test here, it needs a
 *                         live network connection and a real display, and
 *                         re-checks DeckTextExtractor's own real-site parsing
 *                         against today's actual pages rather than a frozen
 *                         fixture (see that class' own header) - a normal run
 *                         of this suite now takes noticeably longer and can
 *                         fail for reasons outside this codebase (a site's
 *                         own layout changing, no network access).
 *     Rémi Dutil (2026) - moved BrowseWebsiteLiveTest from its own
 *                         com.reflexit.magiccards.ui.web package (mirroring
 *                         BrowseWebsiteDialog's own main-plugin package) into
 *                         this one, alongside AllTests itself - it doesn't
 *                         cleanly mirror any single main-code class the way
 *                         EditCardsPropertiesDialogFinishTest/
 *                         DeckExportPageSelectionTest do (it spans
 *                         BrowseWebsiteDialog, WebFavoritesStore and
 *                         DeckTextExtractor), same as the other tests
 *                         already here with no explicit import
 *                         (UnsortedCopyPositionTest, PrintOrderSortTest, ...).
 */

package com.reflexit.magiccards.ui.tests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.reflexit.magiccards.ui.dialogs.EditCardsPropertiesDialogFinishTest;
import com.reflexit.magiccards.ui.exportWizards.DeckExportPageSelectionTest;
import com.reflexit.magiccards.ui.exportWizards.ImportNameDiagnosisTest;
import com.reflexit.magiccards.ui.view.model.RootTreeViewerContentProviderTest;
import com.reflexit.magiccards.ui.view.model.TreeViewerContentProviderTest;

@RunWith(Suite.class)
@SuiteClasses({ TreeViewerContentProviderTest.class, RootTreeViewerContentProviderTest.class,
		UnsortedCopyPositionTest.class, ImportNameDiagnosisTest.class, PrintOrderSortTest.class,
		PrintingsColumnCollectionTest.class, InstanceLocationSortTest.class, DeckExportPageSelectionTest.class,
		EditCardsPropertiesDialogFinishTest.class, DeckColumnCollectionTest.class, BrowseWebsiteLiveTest.class })
public class AllTests {
}
