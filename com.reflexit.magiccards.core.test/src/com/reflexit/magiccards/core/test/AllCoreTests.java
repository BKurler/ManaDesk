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
 */

package com.reflexit.magiccards.core.test;

import com.reflexit.magiccards.core.exports.ScrtFallImportTest;
import com.reflexit.magiccards.core.model.storage.PerformanceFilteringTest;
import com.reflexit.magiccards.core.sync.CurrencyConvertorTest;

import junit.framework.JUnit4TestAdapter;
import junit.framework.Test;
import junit.framework.TestSuite;

public class AllCoreTests {
	public static Test suite() {
		TestSuite suite = new TestSuite("Test for com.reflexit.magiccards.core.test");
		// $JUnit-BEGIN$
		suite.addTest(AllLocalTests.suite());
		// gatherer
		// !!! RD Not used anymore		suite.addTestSuite(ParseGathererCardLanguagesTest.class);
		// !!! RD Not used anymore		suite.addTestSuite(ParseGathererSetsTest.class);
		// !!! RD Not used anymore		suite.addTestSuite(ParseGathererLegalityTest.class);
		// !!! RD Not used anymore		suite.addTestSuite(ParseGathererOracleTest.class);
		// !!! RD Not used anymore		suite.addTestSuite(ParseGathererPrintedTest.class);
		// !!! RD Not used anymore		suite.addTestSuite(ParseGathererSearchStandardTest.class);
		// !!! RD Not used anymore		suite.addTestSuite(ParseGathererSearchChecklistTest.class);
		suite.addTestSuite(CurrencyConvertorTest.class);
		suite.addTestSuite(PerformanceFilteringTest.class);
		// online import
		suite.addTest(new JUnit4TestAdapter(ScrtFallImportTest.class));
		// price providers
		// !!! RD suite.addTest(AllSellerTests.suite());
		// $JUnit-END$
		return suite;
	}
}
