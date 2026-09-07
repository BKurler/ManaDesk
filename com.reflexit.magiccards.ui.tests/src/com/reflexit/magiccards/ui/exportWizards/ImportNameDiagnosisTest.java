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

package com.reflexit.magiccards.ui.exportWizards;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * {@link DeckImportPreviewPage#diagnose(String, String, String, java.util.Collection)}
 * and {@link DeckImportPreviewPage#stripTypeLine(String)} - the "why did this
 * import row not resolve" message. The overriding rule the tests pin down: <b>a
 * card name that is not in the database is reported as "Name not found",
 * whatever else is wrong with the row</b>.
 */
public class ImportNameDiagnosisTest {

	/** a DB printing as {@code {set, collectorNumber}} - what diagnose() consumes */
	private static String[] printing(String set, String num) {
		return new String[] { set, num };
	}

	@SafeVarargs
	private static List<String[]> db(String[]... p) {
		return Arrays.asList(p);
	}

	private static String type(String err) {
		return DeckImportPreviewPage.errorShort(err);
	}

	private static String diagnose(String name, String set, String num, List<String[]> db) {
		return DeckImportPreviewPage.diagnose(name, set, num, db);
	}

	// --- name not found wins over everything ------------------------------

	@Test
	public void unknownName_noOtherInfo_isNameNotFound() {
		assertEquals("Name not found", type(diagnose("Jasmine Dragon Tea Shop", "", "", Collections.emptyList())));
	}

	@Test
	public void unknownName_beatsMissingSetAndNum() {
		assertEquals("Name not found", type(diagnose("Plains Basic M10", "", "", Collections.emptyList())));
	}

	@Test
	public void unknownName_beatsBogusSetAndNum() {
		// a wrong set and a wrong num, but the name error is the one to report
		assertEquals("Name not found",
				type(diagnose("Plains M10", "Some Bogus Set", "999", Collections.emptyList())));
	}

	@Test
	public void emptyName_isNameNotFound() {
		assertEquals("Name not found", type(diagnose("", "Alpha", "1", Collections.emptyList())));
	}

	// --- a real name falls through to the set / num diagnosis -------------

	@Test
	public void knownName_wrongSet_isSetNotFound() {
		assertEquals("Set not found",
				type(diagnose("Plains", "No Such Set", "", db(printing("Limited Edition Alpha", "1")))));
	}

	@Test
	public void knownName_wrongNum_isNumNotFound() {
		assertEquals("Num not found", type(diagnose("Plains", "", "999",
				db(printing("Limited Edition Alpha", "1"), printing("Ice Age", "2")))));
	}

	@Test
	public void knownName_rightSet_wrongNum_isNumNotFound() {
		// the set is real and has the card, but not at that collector number
		assertEquals("Num not found", type(diagnose("Plains", "Ice Age", "999",
				db(printing("Ice Age", "1"), printing("Ice Age", "2")))));
	}

	@Test
	public void knownName_noSetNoNum_needsBoth() {
		assertEquals("Num + Set needed", type(diagnose("Plains", "", "", db(printing("Limited Edition Alpha", "1")))));
	}

	@Test
	public void knownName_setButNoNum_needsNum() {
		assertEquals("Num needed",
				type(diagnose("Plains", "Ice Age", "", db(printing("Ice Age", "1"), printing("Ice Age", "2")))));
	}

	// --- stripTypeLine: only known type words are junk -------------------

	@Test
	public void stripsTrailingSupertype() {
		assertEquals("Andúril, Flame of the West",
				DeckImportPreviewPage.stripTypeLine("Andúril, Flame of the West Legendary"));
	}

	@Test
	public void stripsTrailingCardType() {
		assertEquals("Lightning Bolt", DeckImportPreviewPage.stripTypeLine("Lightning Bolt Instant"));
		assertEquals("Wall of Faith", DeckImportPreviewPage.stripTypeLine("Wall of Faith Creature"));
	}

	@Test
	public void stripsWholeTypeLineIncludingSubtypes() {
		assertEquals("Serra Angel",
				DeckImportPreviewPage.stripTypeLine("Serra Angel Legendary Creature - Angel Warrior"));
		assertEquals("Wall of Bone", DeckImportPreviewPage.stripTypeLine("Wall of Bone Creature - Skeleton Wall"));
	}

	@Test
	public void leavesNonTypeJunkAlone() {
		// "M10" / "2011" are not type words - the name stays "not found"
		assertEquals("Plains Basic M10", DeckImportPreviewPage.stripTypeLine("Plains Basic M10"));
		assertEquals("Plains M10", DeckImportPreviewPage.stripTypeLine("Plains M10"));
		assertEquals("Jasmine Dragon Tea Shop", DeckImportPreviewPage.stripTypeLine("Jasmine Dragon Tea Shop"));
	}

	@Test
	public void leavesACleanNameAlone() {
		assertEquals("Karn, the Great Creator", DeckImportPreviewPage.stripTypeLine("Karn, the Great Creator"));
		assertEquals("", DeckImportPreviewPage.stripTypeLine(null));
	}
}
