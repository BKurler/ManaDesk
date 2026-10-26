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

import junit.framework.TestCase;

/**
 * Digital cards: the per-card flags ({@link MagicCardField#DIGITAL} /
 * {@link MagicCardField#ONLINE}), {@link DigitalSupport}, and the digital flag of
 * a set (editions.txt field 8; the digital sets are left out of the set lists while
 * the digital support is off). Plain JUnit, but {@link Editions} needs the core
 * test database fixtures like {@code EditionsTest}.
 */
public class DigitalCardsTest extends TestCase {
	private boolean wasEnabled;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		wasEnabled = DigitalSupport.isEnabled();
	}

	@Override
	protected void tearDown() throws Exception {
		DigitalSupport.setEnabled(wasEnabled);
		Editions.getInstance().init(); // the tests above replace the set list with their own
		super.tearDown();
	}

	private static MagicCard card(String digital, String online) {
		MagicCard c = new MagicCard();
		c.set(MagicCardField.DIGITAL, digital);
		c.set(MagicCardField.ONLINE, online);
		return c;
	}

	public void testPaperCardIsNeitherDigitalNorOnline() {
		MagicCard c = card(null, null);
		assertFalse(DigitalSupport.isDigitalOnly(c));
		assertFalse(DigitalSupport.isOnline(c));
		assertFalse(DigitalSupport.isUsableDigitally(c));
	}

	public void testDigitalOnlyCard() {
		MagicCard c = card("true", null);
		assertTrue(DigitalSupport.isDigitalOnly(c));
		assertTrue(DigitalSupport.isUsableDigitally(c));
	}

	public void testPaperCardAlsoOnlineIsUsableDigitallyButStaysPaper() {
		MagicCard c = card(null, "true");
		assertFalse(DigitalSupport.isDigitalOnly(c));
		assertTrue(DigitalSupport.isOnline(c));
		assertTrue(DigitalSupport.isUsableDigitally(c));
	}

	public void testACopyFollowsItsPrinting() {
		MagicCardPhysical copy = new MagicCardPhysical(card("true", null), null);
		assertTrue(DigitalSupport.isDigitalOnly(copy));
	}

	public void testNothingIsDigitalForNonCards() {
		assertFalse(DigitalSupport.isDigitalOnly(null));
		assertFalse(DigitalSupport.isDigitalOnly("a string"));
	}

	public void testDigitalOnlyCardIsHiddenOnlyWhileTheSupportIsOff() {
		MagicCard digital = card("true", null);
		MagicCard online = card(null, "true");
		MagicCard paper = card(null, null);
		DigitalSupport.setEnabled(false);
		assertTrue(DigitalSupport.isHidden(digital));
		assertFalse("a paper card also online is a paper card", DigitalSupport.isHidden(online));
		assertFalse(DigitalSupport.isHidden(paper));
		DigitalSupport.setEnabled(true);
		assertFalse(DigitalSupport.isHidden(digital));
	}

	public void testFlagsAreInThePropertyMapNotExported() {
		assertTrue(MagicCardField.DIGITAL.isTransient());
		assertTrue(MagicCardField.ONLINE.isTransient());
	}

	// ------------------------------------------------------------------ editions

	private static Editions loaded(String... lines) throws Exception {
		Editions eds = Editions.getInstance(true);
		eds.init(true);
		StringBuilder sb = new StringBuilder();
		for (String l : lines)
			sb.append(l).append('\n');
		eds.loadEditions(new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)));
		return eds;
	}

	private static final String PAPER = "Khans of Tarkir|KTK||September 2014|Expansion|Khans of Tarkir|ktk|";
	private static final String DIGITAL = "Historic Anthology 1|HA1||December 2018|Masters|Historic|ha1||digital";

	public void testDigitalColumnIsReadFromEditionsFile() throws Exception {
		Editions eds = loaded(PAPER, DIGITAL);
		assertFalse(eds.getEditionByName("Khans of Tarkir").isDigital());
		assertTrue(eds.getEditionByName("Historic Anthology 1").isDigital());
	}

	public void testOldEditionsLinesWithoutTheColumnAreNotDigital() throws Exception {
		Editions eds = loaded("Old Set|OLD||August 1993|Core|Core Set|old|", "Older|OLR");
		assertFalse(eds.getEditionByName("Old Set").isDigital());
		assertFalse(eds.getEditionByName("Older").isDigital());
	}

	public void testDigitalSetsAreLeftOutOfTheListsWhileTheSupportIsOff() throws Exception {
		Editions eds = loaded(PAPER, DIGITAL);
		DigitalSupport.setEnabled(false);
		assertEquals(1, eds.getEditions().size());
		assertFalse(eds.getNames().contains("Historic Anthology 1"));
		assertEquals(1, eds.getIds().size());
		assertEquals("every edition is still there", 2, eds.getAllEditions().size());
		DigitalSupport.setEnabled(true);
		assertEquals(2, eds.getEditions().size());
		assertTrue(eds.getNames().contains("Historic Anthology 1"));
		assertEquals(2, eds.getIds().size());
	}

	public void testDigitalColumnSurvivesASave() throws Exception {
		Editions eds = loaded(PAPER, DIGITAL);
		File tmp = File.createTempFile("editions-digital-", ".txt");
		try {
			eds.save(tmp);
			String saved = new String(Files.readAllBytes(tmp.toPath()), StandardCharsets.UTF_8);
			assertTrue(saved, saved.contains("Historic Anthology 1|HA1|"));
			Editions again = loaded(saved.split("\n"));
			assertTrue(again.getEditionByName("Historic Anthology 1").isDigital());
			assertFalse(again.getEditionByName("Khans of Tarkir").isDigital());
		} finally {
			tmp.delete();
		}
	}
}
