

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - added CsvImporterTest (regression test for the
 *                         "lineNum.separator" typo)
 *     Rémi Dutil (2026) - added DeckTextExtractorTest - built up over an
 *                         extended session (real-capture regression tests
 *                         for every site BrowseWebsiteDialog's "Browse
 *                         Website..." import supports) entirely through an
 *                         out-of-band javac/java scratchpad recipe, never
 *                         actually wired into this project's own Eclipse-
 *                         buildable suite until now - it never ran as part
 *                         of a normal AllLocalTests pass.
 */

package com.reflexit.magiccards.core.exports;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.reflexit.magiccards.core.sync.TextPrinterTest;

@RunWith(Suite.class)
@SuiteClasses({ CsvImporterTest.class, TablePipedImportTest.class,
		// !!! RD MtgoImportTest.class,
		// !!! RD MagicWorkstationImportTest.class,
		DeckParserTest.class, ImportUtilsTest.class,
// !!! RD Disable for now	ManaDeckImportTest.class,
// !!! RD Disable for now	ShandalarImportTest.class,
// !!! RD Disable for now	MTGStudioImportTest.class,
		PipedTableExportText.class, CsvExportDelegateTest.class, CsvImportDelegateTest.class,
		ClassicExportDelegateTest.class, ClassicImportDelegateTest.class, CustomExportDelegateTest.class,
		SideboardHelpHtmlExportDelegateTest.class,
		// !!! RD DeckBoxImportTest.class,
		TextPrinterTest.class, DeckTextExtractorTest.class,
// !!! RD 		HtmlTableImportTest.class, //
// !!! RD 		ScryGlassImportDelegateTest.class
})
public class ExportImportSuite {
}
