/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - registered DeckExportPageSelectionTest
 *     Rémi Dutil (2026) - registered EditCardsPropertiesDialogFinishTest
 *     Rémi Dutil (2026) - registered DeckColumnCollectionTest
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
		EditCardsPropertiesDialogFinishTest.class, DeckColumnCollectionTest.class })
public class AllTests {
}
