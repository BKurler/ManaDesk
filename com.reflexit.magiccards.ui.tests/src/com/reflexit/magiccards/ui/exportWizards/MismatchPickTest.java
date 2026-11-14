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

import java.util.ArrayList;
import java.util.List;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;

import junit.framework.TestCase;

/**
 * "Auto complete using first match" on a "Mismatched info" row: the file's Name and Set win, the Num comes
 * from the database. Pure logic, plain JUnit (no SWT needed).
 */
public class MismatchPickTest extends TestCase {
	private final List<IMagicCard> printings = new ArrayList<>();

	private static MagicCard printing(String set, String num) {
		MagicCard c = new MagicCard();
		c.setName("Athreos, Shroud-Veiled");
		c.setSet(set);
		c.setCollNumber(num);
		return c;
	}

	@Override
	protected void setUp() {
		printings.add(printing("Duskmourn: House of Horror Commander", "212"));
		printings.add(printing("Theros Beyond Death", "269"));
		printings.add(printing("Theros Beyond Death", "218"));
		printings.add(printing("Theros Beyond Death", "9"));
	}

	private IMagicCard choose(String set, String num) {
		return MismatchPick.choose(printings, set, num, String::equalsIgnoreCase);
	}

	/** The file's Num is wrong for that set: the set decides, the lowest Num of the set is taken. */
	public void testSetWinsAndNumIsUpdated() {
		IMagicCard c = choose("Theros Beyond Death", "1");
		assertEquals("Theros Beyond Death", c.getSet());
		assertEquals("9", c.getCollectorId());
	}

	/** The lowest Num is compared as a number: 9 comes before 218 and 269. */
	public void testLowestIsNumeric() {
		assertEquals("9", choose("theros beyond death", "").getCollectorId());
	}

	/** The file's Num is kept when that set has it. */
	public void testNumOfTheFileIsKeptWhenTheSetHasIt() {
		IMagicCard c = choose("Theros Beyond Death", "269");
		assertEquals("269", c.getCollectorId());
		assertEquals("Theros Beyond Death", c.getSet());
	}

	/** Another set's Num does not count: a Num 212 is in Duskmourn, not in Theros. */
	public void testNumOfAnotherSetIsNotUsed() {
		IMagicCard c = choose("Theros Beyond Death", "212");
		assertEquals("Theros Beyond Death", c.getSet());
		assertEquals("9", c.getCollectorId());
	}

	/** No printing in the file's set, or no set: the caller falls back to the first match of the name. */
	public void testNothingToPick() {
		assertNull(choose("Some Other Set", "269"));
		assertNull(choose("", "1")); // a Num alone that no printing has
		assertNull(MismatchPick.choose(new ArrayList<IMagicCard>(), "Theros Beyond Death", "9", String::equalsIgnoreCase));
		assertNull(MismatchPick.choose(null, "Theros Beyond Death", "9", String::equalsIgnoreCase));
	}

	/** Without a set the Num can still pick a printing, when one has it. */
	public void testNumWithoutSet() {
		IMagicCard c = choose("", "212");
		assertEquals("Duskmourn: House of Horror Commander", c.getSet());
	}
}
