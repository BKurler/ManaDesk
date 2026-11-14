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
 *     Rémi Dutil (2026) - runs AllLocalTests + the new AllOnlineTests (the network / timing tests)
 */

package com.reflexit.magiccards.core.test;

import junit.framework.Test;
import junit.framework.TestSuite;

/** Everything: the local tests plus the online / timing ones. Maven runs only {@link AllLocalTests}. */
public class AllCoreTests {
	public static Test suite() {
		TestSuite suite = new TestSuite("Test for com.reflexit.magiccards.core.test");
		// $JUnit-BEGIN$
		suite.addTest(AllLocalTests.suite());
		suite.addTest(AllOnlineTests.suite());
		// gatherer and price providers: not used anymore (see git history)
		// $JUnit-END$
		return suite;
	}
}
