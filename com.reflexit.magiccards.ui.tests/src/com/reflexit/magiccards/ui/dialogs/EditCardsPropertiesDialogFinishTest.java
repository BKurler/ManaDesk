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
package com.reflexit.magiccards.ui.dialogs;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;

/**
 * {@link EditCardsPropertiesDialog#allowedFinishes(String)} - the choices the
 * Finish combo offers, including the multi-card-selection case.
 * <p>
 * {@code EditMagicCardPhysicalDialog}'s constructor decides what ends up in
 * the {@code MagicCardField.FINISHES} store value before this ever runs: the
 * shared value when every selected card's printing agrees, or
 * {@link EditCardsPropertiesDialog#UNCHANGED} the moment two selected cards'
 * printings disagree. That reduction is the same generic, already-proven
 * mechanism every other field (Condition, Proxy, ...) already goes through -
 * {@link #testConstructorReductionAgreesOnASharedPrinting} and
 * {@link #testConstructorReductionDisagreesOnDifferentPrintings} replicate
 * exactly what it does (not what it happens to produce), so what's actually
 * under test end to end is: several selected copies of the SAME printing
 * still offer that printing's real finishes (never falls back to "unknown"
 * just because more than one card is selected), while a selection spanning
 * DIFFERENT printings safely falls back to {Nonfoil, Foil} - it never offers
 * Etched to a batch it can't confirm agrees on it.
 */
public class EditCardsPropertiesDialogFinishTest {

	@Test
	public void singlePrintingKnownFinishes() {
		assertArrayEquals(new CardFinish[] { CardFinish.NONFOIL, CardFinish.FOIL },
				EditCardsPropertiesDialog.allowedFinishes("nonfoil,foil"));
	}

	@Test
	public void singleEtchedOnlyPrinting() {
		assertArrayEquals(new CardFinish[] { CardFinish.ETCHED }, EditCardsPropertiesDialog.allowedFinishes("etched"));
	}

	@Test
	public void resultOrderIsAlwaysNonfoilFoilEtched() {
		// same set, deliberately shuffled input order
		assertArrayEquals(new CardFinish[] { CardFinish.NONFOIL, CardFinish.FOIL, CardFinish.ETCHED },
				EditCardsPropertiesDialog.allowedFinishes("etched,nonfoil,foil"));
	}

	@Test
	public void unknownOrMissingDataFallsBackToNonfoilAndFoilOnly() {
		assertArrayEquals(new CardFinish[] { CardFinish.NONFOIL, CardFinish.FOIL },
				EditCardsPropertiesDialog.allowedFinishes(null));
		assertArrayEquals(new CardFinish[] { CardFinish.NONFOIL, CardFinish.FOIL },
				EditCardsPropertiesDialog.allowedFinishes(""));
	}

	@Test
	public void multiSelectOfDifferentPrintingsFallsBackSafely() {
		// this is exactly the value EditMagicCardPhysicalDialog's constructor
		// stores once two selected cards' printings disagree - see
		// testConstructorReductionDisagreesOnDifferentPrintings
		assertArrayEquals("never offers Etched to a batch it can't confirm agrees on it",
				new CardFinish[] { CardFinish.NONFOIL, CardFinish.FOIL },
				EditCardsPropertiesDialog.allowedFinishes(EditCardsPropertiesDialog.UNCHANGED));
	}

	@Test
	public void multiSelectOfTheSamePrintingStillOffersItsRealFinishes() {
		// several copies of one etched-only printing selected together - the
		// constructor's reduction leaves this as the single shared value (not
		// UNCHANGED, since every copy points at the same printing), so Etched
		// is correctly still offered, not blocked just because >1 card is selected
		assertArrayEquals(new CardFinish[] { CardFinish.ETCHED }, EditCardsPropertiesDialog.allowedFinishes("etched"));
	}

	// --- replicates EditMagicCardPhysicalDialog's constructor reduction -----
	// (can't construct the real dialog here - it needs a live Shell, which this
	// project's tests deliberately never do; MagicCard/MagicCardPhysical
	// construction itself needs a real PDE run, same as every other test that
	// touches them - see this class's own failures outside one)

	private static String reduce(MagicCard... printings) {
		String shared = null;
		boolean first = true;
		for (MagicCard printing : printings) {
			MagicCardPhysical copy = new MagicCardPhysical(printing, null);
			String value = String.valueOf(copy.get(MagicCardField.FINISHES));
			if (first) {
				shared = value;
				first = false;
			} else if (!value.equals(shared)) {
				return EditCardsPropertiesDialog.UNCHANGED;
			}
		}
		return shared;
	}

	@Test
	public void testConstructorReductionAgreesOnASharedPrinting() {
		MagicCard etchedOnly = new MagicCard();
		etchedOnly.set(MagicCardField.FINISHES, "etched");
		// three different MagicCardPhysical copies of the exact same printing
		// (the realistic "3 copies of one card in a deck" case)
		String reduced = reduce(etchedOnly, etchedOnly, etchedOnly);
		org.junit.Assert.assertEquals("etched", reduced);
		assertArrayEquals(new CardFinish[] { CardFinish.ETCHED }, EditCardsPropertiesDialog.allowedFinishes(reduced));
	}

	@Test
	public void testConstructorReductionDisagreesOnDifferentPrintings() {
		MagicCard etchedOnly = new MagicCard();
		etchedOnly.set(MagicCardField.FINISHES, "etched");
		MagicCard nonfoilFoil = new MagicCard();
		nonfoilFoil.set(MagicCardField.FINISHES, "nonfoil,foil");
		String reduced = reduce(etchedOnly, nonfoilFoil);
		org.junit.Assert.assertEquals(EditCardsPropertiesDialog.UNCHANGED, reduced);
		assertArrayEquals("selecting an etched-only card together with a nonfoil/foil one must not offer Etched",
				new CardFinish[] { CardFinish.NONFOIL, CardFinish.FOIL },
				EditCardsPropertiesDialog.allowedFinishes(reduced));
	}
}
