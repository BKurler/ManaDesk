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
package com.reflexit.magiccards.core.model;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.Date;

import junit.framework.TestCase;

/**
 * "(coming soon)": shown after the name of a set not released yet, computed from its release date, never
 * stored. Plain JUnit, with the core test database fixtures like {@code EditionsTest}.
 */
public class EditionComingSoonTest extends TestCase {

	private static Date daysFromNow(int days) {
		Calendar c = Calendar.getInstance();
		c.add(Calendar.DAY_OF_YEAR, days);
		return c.getTime();
	}

	@Override
	protected void tearDown() throws Exception {
		Editions.getInstance().init(); // the tests below replace the set list with their own
		super.tearDown();
	}

	public void testOnlyAReleaseDateAfterTodayIsComingSoon() {
		Date today = new Date();
		assertTrue(Edition.isComingSoon(daysFromNow(10), today));
		assertFalse(Edition.isComingSoon(daysFromNow(-10), today));
		assertFalse("a set with no known date is not coming soon", Edition.isComingSoon(null, today));
	}

	public void testTheLabelFollowsTheReleaseDate() {
		Edition future = new Edition("Future Set", "FUT");
		future.setReleaseDate(daysFromNow(30));
		assertTrue(future.isComingSoon());
		assertEquals("Future Set (coming soon)", future.getDisplayName());
		assertEquals("the name itself has no label", "Future Set", future.getName());
		// after the release date the label is gone, with nothing to update
		future.setReleaseDate(daysFromNow(-1));
		assertFalse(future.isComingSoon());
		assertEquals("Future Set", future.getDisplayName());
	}

	public void testEditionsKnowTheDisplayNameAndStripItBack() throws Exception {
		Editions eds = Editions.getInstance(true);
		eds.init(true);
		Edition future = eds.addEdition("Star Trek Test", "STT");
		future.setReleaseDate(daysFromNow(20));
		Edition past = eds.addEdition("Old Test", "OLD");
		past.setReleaseDate(daysFromNow(-200));

		assertEquals("Star Trek Test (coming soon)", eds.getDisplayName("Star Trek Test"));
		assertEquals("Old Test", eds.getDisplayName("Old Test"));
		assertEquals("an unknown set is shown as it is", "Nowhere", eds.getDisplayName("Nowhere"));
		assertNull(eds.getDisplayName(null));
		assertEquals("Star Trek Test", Editions.stripLabel("Star Trek Test (coming soon)"));
		assertEquals("Old Test", Editions.stripLabel("Old Test"));
		assertNull(Editions.stripLabel(null));
	}

	public void testTheLabelIsNeverWrittenToEditionsTxt() throws Exception {
		Editions eds = Editions.getInstance(true);
		eds.init(true);
		eds.addEdition("Star Trek Test", "STT").setReleaseDate(daysFromNow(20));
		File tmp = File.createTempFile("editions-soon-", ".txt");
		try {
			eds.save(tmp);
			String saved = new String(Files.readAllBytes(tmp.toPath()), StandardCharsets.UTF_8);
			assertTrue(saved, saved.startsWith("Star Trek Test|STT|"));
			assertFalse(saved, saved.contains("coming soon"));
			// and a set read back keeps its plain name
			eds.init(true);
			eds.loadEditions(new ByteArrayInputStream(saved.getBytes(StandardCharsets.UTF_8)));
			assertNotNull(eds.getEditionByName("Star Trek Test"));
			assertNull(eds.getEditionByName("Star Trek Test (coming soon)"));
		} finally {
			tmp.delete();
		}
	}
}
