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
package com.reflexit.magiccards.core.test;

import com.reflexit.magiccards.core.exports.ScrtFallImportTest;
import com.reflexit.magiccards.core.model.storage.PerformanceFilteringTest;
import com.reflexit.magiccards.core.sync.CurrencyConvertorTest;

import junit.framework.JUnit4TestAdapter;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * The tests that are not part of {@link AllLocalTests} because they depend on the network (live Scryfall page, live
 * exchange rates) or on the speed of the machine (wall-clock limits). Not run by the Maven build; {@link AllCoreTests}
 * runs them together with the local tests.
 */
public class AllOnlineTests {
	public static Test suite() {
		TestSuite suite = new TestSuite("Test for com.reflexit.magiccards.core.test.online");
		// $JUnit-BEGIN$
		suite.addTestSuite(CurrencyConvertorTest.class);
		suite.addTestSuite(PerformanceFilteringTest.class);
		// the real Scryfall: a few small requests (fails once, with the reason, when Scryfall is down)
		suite.addTest(new JUnit4TestAdapter(com.reflexit.magiccards.core.sync.ScryfallLiveTest.class));
		// online import
		suite.addTest(new JUnit4TestAdapter(ScrtFallImportTest.class));
		// $JUnit-END$
		return suite;
	}
}
