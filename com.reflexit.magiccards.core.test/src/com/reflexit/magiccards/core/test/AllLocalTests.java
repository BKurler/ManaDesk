/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - added WebUtilsTest.
 *     Rémi Dutil (2026) - added FormatTest (format shapes + Commander color
 *                         identity rule) - it was never registered.
 *     Rémi Dutil (2026) - registered nine tests that existed but never ran
 *                         (Condition, Finish, Proxy, set rename, storage, SVG, two imports).
 *     Rémi Dutil (2026) - added ReportTypeTest, CardStoreUtilsStatsTest, EditionTest and
 *                         EditionsAliasesTest, ParseScryFallSetsRecordTest and
 *                         DbMultiFileCardStoreHandlerTest, ProgressMonitorTest, CardGroupStructureTest,
 *                         SortOrderStateTest, MagicCardComparatorFieldsTest, FormatValidationTest and
 *                         DeckAccessoriesDbTest, CardCacheTest, ScryfallRulingsTest, ScryfallBulkCacheTest,
 *                         DeckAccessoriesPopulatorTest, SvgRasterizerRenderTest,
 *                         SymbolConverterTest, CachedFileTest and EditionFileCacheTest.
 */

package com.reflexit.magiccards.core.test;

import junit.framework.JUnit4TestAdapter;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import com.reflexit.magiccards.core.DataManagerTest;
import com.reflexit.magiccards.core.FileUtilsTest;
import com.reflexit.magiccards.core.exports.ExportImportSuite;
import com.reflexit.magiccards.core.model.AbilitiesTest;
import com.reflexit.magiccards.core.model.CardGroupTest;
import com.reflexit.magiccards.core.model.DeckAccessoriesTest;
import com.reflexit.magiccards.core.model.CollectorNumberTest;
import com.reflexit.magiccards.core.model.ColorsTest;
import com.reflexit.magiccards.core.model.EditionsTest;
import com.reflexit.magiccards.core.model.GrouppingPerformanceTest;
import com.reflexit.magiccards.core.model.LegalityMapTest;
import com.reflexit.magiccards.core.model.MagicCardComparatorTest;
import com.reflexit.magiccards.core.model.MagicCardFilterTest;
import com.reflexit.magiccards.core.model.MagicCardListTest;
import com.reflexit.magiccards.core.model.MagicCardTest;
import com.reflexit.magiccards.core.model.MagicCardTest_failure_1;
import com.reflexit.magiccards.core.model.MagicCardTest_failure_2;
import com.reflexit.magiccards.core.model.PlayingDeckTest;
import com.reflexit.magiccards.core.model.SortOrderTest;
import com.reflexit.magiccards.core.model.nav.CardElementTest;
import com.reflexit.magiccards.core.model.nav.NewCardCollectionSettingsTest;
import com.reflexit.magiccards.core.model.nav.CardOrganizerTest;
import com.reflexit.magiccards.core.model.storage.AbstractFilteredCardStoreTest;
import com.reflexit.magiccards.core.model.storage.CardCollectionStoreObjectTest;
import com.reflexit.magiccards.core.model.storage.CollectionStoreTest;
import com.reflexit.magiccards.core.model.storage.DbMultiFileCardStoreTest;
import com.reflexit.magiccards.core.model.storage.DeckStoreTest;
import com.reflexit.magiccards.core.model.storage.MultiFileCollectionStoreTest;
import com.reflexit.magiccards.core.model.utils.CardStoreUtilsTest;
import com.reflexit.magiccards.core.sync.AccessoryExtractorTest;
import com.reflexit.magiccards.core.sync.ScryfallBulkSplitTest;
import com.reflexit.magiccards.core.sync.WebUtilsTest;
import com.reflexit.magiccards.core.exports.BuyListFormatTest;
import com.reflexit.magiccards.core.model.nav.DeckNeedsTest;
import com.reflexit.magiccards.core.seller.PriceSourcesTest;
import com.reflexit.magiccards.core.model.utils.SearchStringTokenizerTest;
import com.reflexit.magiccards.core.xml.MagicXmlHandlerTest;


@RunWith(AllTests.class)
public class AllLocalTests {
	public static Test suite() {
		TestSuite suite = new TestSuite("Test for com.reflexit.magiccards.core.test.local");
		// $JUnit-BEGIN$
		// import
		suite.addTest(new JUnit4TestAdapter(ExportImportSuite.class));
		// core
		suite.addTestSuite(MagicCardTest.class);
		suite.addTestSuite(MagicCardTest_failure_1.class);
		suite.addTestSuite(MagicCardTest_failure_2.class);
		suite.addTestSuite(DbMultiFileCardStoreTest.class);
		suite.addTestSuite(MultiFileCollectionStoreTest.class);
		suite.addTestSuite(CardOrganizerTest.class);
		suite.addTestSuite(AbstractFilteredCardStoreTest.class);
		suite.addTestSuite(DeckStoreTest.class);
		suite.addTestSuite(CardCollectionStoreObjectTest.class);
		suite.addTestSuite(CollectionStoreTest.class);
		suite.addTestSuite(MagicCardFilterTest.class);
		suite.addTestSuite(MagicXmlHandlerTest.class);
		suite.addTestSuite(DataManagerTest.class);
		suite.addTestSuite(NewCardCollectionSettingsTest.class);
		suite.addTestSuite(FileUtilsTest.class);
		// suite.addTestSuite(CardTextNL1Test.class); TODO needs the Russian pack of com.reflexit.magiccards.core.nl1
		suite.addTestSuite(CardElementTest.class);
		suite.addTestSuite(CardGroupTest.class);
		suite.addTest(new JUnit4TestAdapter(ColorsTest.class));
		suite.addTest(new JUnit4TestAdapter(CollectorNumberTest.class));
		suite.addTestSuite(SortOrderTest.class);
		suite.addTestSuite(SearchStringTokenizerTest.class);
		suite.addTest(new JUnit4TestAdapter(CardStoreUtilsTest.class));
		suite.addTestSuite(MagicCardComparatorTest.class);
		suite.addTestSuite(LegalityMapTest.class);
		suite.addTestSuite(AbilitiesTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.CardTraitsTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.ExtendedColorIdentityTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.CardTypesProposalsTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.sync.ScryfallReferenceTest.class);
		suite.addTest(new JUnit4TestAdapter(com.reflexit.magiccards.core.sync.ScryfallCardFactsTest.class));
		suite.addTest(new JUnit4TestAdapter(MagicCardListTest.class));
		suite.addTest(new JUnit4TestAdapter(PlayingDeckTest.class));
		suite.addTest(new JUnit4TestAdapter(ScryfallBulkSplitTest.class));
		suite.addTest(new JUnit4TestAdapter(AccessoryExtractorTest.class));
		suite.addTest(new JUnit4TestAdapter(WebUtilsTest.class));
		suite.addTest(new JUnit4TestAdapter(BuyListFormatTest.class));
		suite.addTestSuite(DeckNeedsTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.nav.CollectionTypeTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.IssueReportTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.exports.ManaBoxCsvTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.exports.ExportTypeOrderTest.class);
		suite.addTest(new JUnit4TestAdapter(PriceSourcesTest.class));
		suite.addTest(new JUnit4TestAdapter(DeckAccessoriesTest.class));
		suite.addTest(new JUnit4TestAdapter(com.reflexit.magiccards.core.model.LocationExtraTest.class));
		suite.addTestSuite(com.reflexit.magiccards.core.legality.FormatTest.class);
		// editions
		suite.addTestSuite(EditionsTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.DigitalCardsTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.DigitalRulesTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.DigitalViewsTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.exports.ImportForceKindTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.nav.DeckFormatInheritTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.EditionComingSoonTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.sync.ParseScryFallSetsTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.xml.HiddenDigitalCardsTest.class);
		// fields and storage that were never registered
		suite.addTestSuite(com.reflexit.magiccards.core.model.CardConditionTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.CardFinishTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.CardProxyTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.SetRenameLinkTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.storage.CollectionCardStoreTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.xml.SingleFileCardStorageFinishConversionTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.sync.SvgRasterizerTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.sync.SvgRasterizerRenderTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.sync.SymbolConverterTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.sync.CachedFileTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.sync.EditionFileCacheTest.class);
		// imports that were never registered
		suite.addTest(new JUnit4TestAdapter(com.reflexit.magiccards.core.exports.FreeformImportTest.class));
		suite.addTest(new JUnit4TestAdapter(com.reflexit.magiccards.core.exports.MagicAssistantCsvImportTest.class));
		// deck statistics
		suite.addTest(new JUnit4TestAdapter(com.reflexit.magiccards.core.model.utils.CardStoreUtilsStatsTest.class));
		// editions and the set list
		suite.addTestSuite(com.reflexit.magiccards.core.model.EditionTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.EditionsAliasesTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.sync.ParseScryFallSetsRecordTest.class);
		// card database store
		suite.addTestSuite(com.reflexit.magiccards.core.model.storage.DbMultiFileCardStoreHandlerTest.class);
		// progress, groups and sorting
		suite.addTestSuite(com.reflexit.magiccards.core.monitor.ProgressMonitorTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.CardGroupStructureTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.SortOrderStateTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.model.MagicCardComparatorFieldsTest.class);
		// legality and deck accessories
		suite.addTestSuite(com.reflexit.magiccards.core.legality.FormatValidationTest.class);
		suite.addTest(new JUnit4TestAdapter(com.reflexit.magiccards.core.model.DeckAccessoriesDbTest.class));
		suite.addTestSuite(com.reflexit.magiccards.core.model.DeckAccessoriesPopulatorTest.class);
		// image cache and rulings (file: addresses, no web)
		suite.addTestSuite(com.reflexit.magiccards.core.sync.CardCacheTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.sync.ScryfallRulingsTest.class);
		suite.addTestSuite(com.reflexit.magiccards.core.sync.ScryfallBulkCacheTest.class);
		// import format detection
		suite.addTestSuite(com.reflexit.magiccards.core.exports.ReportTypeTest.class);
		// Perf
		suite.addTestSuite(GrouppingPerformanceTest.class);
		// $JUnit-END$
		return suite;
	}
}
