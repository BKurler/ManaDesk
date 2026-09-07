/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.ui.tests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.reflexit.magiccards.ui.exportWizards.ImportNameDiagnosisTest;
import com.reflexit.magiccards.ui.view.model.RootTreeViewerContentProviderTest;
import com.reflexit.magiccards.ui.view.model.TreeViewerContentProviderTest;

@RunWith(Suite.class)
@SuiteClasses({ TreeViewerContentProviderTest.class, RootTreeViewerContentProviderTest.class,
		UnsortedCopyPositionTest.class, ImportNameDiagnosisTest.class })
public class AllTests {
}
