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
package com.reflexit.magiccards.core.exports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

/** Export lists: ManaDesk / Print formats, then other applications', then the user's. */
public class ExportTypeOrderTest extends TestCase {
	private static ReportType type(String label, boolean application, boolean custom) {
		ReportType t = ImportExportFactory.createReportType("order-test " + label);
		t.setApplication(application);
		t.setCustom(custom);
		return t;
	}

	public void testOrder() {
		ReportType userB = type("User B", false, true);
		ReportType appZ = type("Wagic", true, false);
		ReportType md2 = type("ManaDesk Minimum CSV", false, false);
		ReportType userA = type("User A", false, true);
		ReportType appA = type("DeckBox", true, false);
		ReportType print = type("Print Proxies HTML", false, false);
		ReportType md1 = type("ManaDesk XML", false, false);
		List<ReportType> list = new ArrayList<>(Arrays.asList(userB, appZ, md2, userA, appA, print, md1));
		ImportExportFactory.sortForLists(list);
		// ManaDesk's own keep their (registration) order, then apps and user formats alphabetically
		assertEquals(Arrays.asList(md2, print, md1, appA, appZ, userA, userB), list);
	}
}
