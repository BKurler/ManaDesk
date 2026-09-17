/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - getDbPrice() tests: reads the exact Finish bucket,
 *                         no fallback to another finish's price when its own
 *                         bucket is empty
 *******************************************************************************/
package com.reflexit.magiccards.core.model;

import java.util.regex.Pattern;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.model.expr.Expr;

/**
 * {@link CardFinish} + {@link CardFinishes} + the {@link MagicCardField#FINISH}
 * contract, and {@link MagicCardPhysical#getFinish()}'s derivation rule - unlike
 * {@link CardCondition} a copy's finish never comes back {@code null}.
 */
public class CardFinishTest extends TestCase {

	public void testThreeFinishesRegularFirst() {
		assertEquals(3, CardFinish.values().length);
		assertEquals(CardFinish.NONFOIL, CardFinish.values()[0]);
		assertEquals(CardFinish.FOIL, CardFinish.values()[1]);
		assertEquals(CardFinish.ETCHED, CardFinish.values()[2]);
	}

	public void testToStringIsTheScryfallSpelling() {
		// the canonical serialized form (XML + CSV export, and Scryfall's own
		// "finishes" array spelling) is the lowercase tag
		assertEquals("nonfoil", CardFinish.NONFOIL.toString());
		assertEquals("foil", String.valueOf(CardFinish.FOIL));
		assertEquals("etched", CardFinish.ETCHED.toString());
		// the readable label is still available for the UI - Scryfall's own
		// wording, capitalized, not an invented one
		assertEquals("Nonfoil", CardFinish.NONFOIL.getLabel());
	}

	public void testResolveRoundTripsEveryFinish() {
		for (CardFinish f : CardFinish.values()) {
			assertSame(f, CardFinish.resolve(f.toString())); // "foil" (serialized / read-back path)
			assertSame(f, CardFinish.resolve(f.getLabel())); // "Foil" (filter checkbox label)
			assertSame(f, CardFinish.resolve(f.name())); // "FOIL"
			assertSame(f, CardFinish.resolve(f.name().toLowerCase()));
		}
		assertSame(CardFinish.FOIL, CardFinish.resolve("  Foil "));
		assertSame(CardFinish.ETCHED, CardFinish.resolve("EtchedFoil"));
	}

	public void testResolveKnownSynonyms() {
		// "Regular" was the label briefly, before reverting to Scryfall's own
		// "nonfoil" wording - still accepted as a synonym for old imports/data
		assertSame(CardFinish.NONFOIL, CardFinish.resolve("Regular"));
		assertSame(CardFinish.NONFOIL, CardFinish.resolve("normal"));
		assertSame(CardFinish.FOIL, CardFinish.resolve("premium")); // MTGO's own name for foil
	}

	public void testResolveBlankOrUnknownIsNull() {
		assertNull(CardFinish.resolve(null));
		assertNull(CardFinish.resolve(""));
		assertNull(CardFinish.resolve("   "));
		assertNull(CardFinish.resolve("Pristine"));
	}

	/**
	 * Regression: {@code AbstractMagicCardsListControl#storeToMap()} only
	 * copies a preference key into the filter's map when it appears in
	 * {@link FilterField#getAllIds()} - {@link CardFinishes#AND_ID} being
	 * missing from that list meant the "And" checkbox's value never reached
	 * {@link MagicCardFilter} at all, so checking it did nothing.
	 */
	public void testAndIdIsInGetAllIds() {
		assertTrue("CardFinishes.AND_ID must be in FilterField.getAllIds(), or the "
				+ "\"And\" checkbox's value never reaches the filter",
				FilterField.getAllIds().contains(CardFinishes.AND_ID));
	}

	public void testSearchablePropertyHasExactlyThreeFinishes() {
		CardFinishes cf = CardFinishes.getInstance();
		assertEquals(FilterField.FINISH, cf.getFilterField());
		assertEquals(3, cf.getIds().size());
		for (String id : cf.getIds()) {
			String label = cf.getNameById(id);
			assertNotNull(label);
			assertNotNull("every filter label must resolve to a real finish", CardFinish.resolve(label));
		}
	}

	public void testMagicCardFieldMetadata() {
		assertTrue(MagicCardField.FINISH.isPhysical());
		assertFalse(MagicCardField.FINISH.isTransient());
		assertEquals("finish", MagicCardField.FINISH.getTag());
		// FINISHES (the printing-level field) is kept in the property map, not exported
		assertTrue(MagicCardField.FINISHES.isTransient());
	}

	// --- MagicCardPhysical#getFinish() derivation ---------------------------

	private static MagicCardPhysical copyOf(MagicCard printing) {
		return new MagicCardPhysical(printing, null);
	}

	public void testDefaultsToNonfoilWithNoFoilTagAndNoFinishesData() {
		MagicCard printing = new MagicCard();
		MagicCardPhysical copy = copyOf(printing);
		assertNull("no explicit override yet", copy.getRawFinish());
		assertEquals(CardFinish.NONFOIL, copy.getFinish());
	}

	public void testSpecialTagIsNeverConsultedLiveByGetFinish() {
		// converting the legacy "foil" tag is now a ONE-SHOT step
		// (SingleFileCardStorageFinishConversionTest) that runs once when a
		// deck/collection is loaded - getFinish() itself must never read the tag
		MagicCard printing = new MagicCard();
		MagicCardPhysical copy = copyOf(printing);
		copy.setSpecialTag("FOIL");
		assertNull("the tag alone never sets an explicit Finish", copy.getRawFinish());
		assertEquals("and getFinish() doesn't derive from it either", CardFinish.NONFOIL, copy.getFinish());
	}

	public void testEtchedOnlyPrintingIsAutomaticallyEtched() {
		MagicCard printing = new MagicCard();
		printing.setFinishes("etched");
		MagicCardPhysical copy = copyOf(printing);
		// no foil tag, no explicit override - still Etched, because that's the
		// only physical form this printing can come in
		assertEquals(CardFinish.ETCHED, copy.getFinish());
	}

	public void testMultiFinishPrintingIsNotAutomaticallyEtched() {
		MagicCard printing = new MagicCard();
		printing.setFinishes("nonfoil,foil,etched"); // this printing ALSO offers nonfoil/foil
		MagicCardPhysical copy = copyOf(printing);
		// ambiguous (not etched-only) - falls back to the plain Nonfoil default,
		// same as any other printing with no explicit Finish set
		assertEquals(CardFinish.NONFOIL, copy.getFinish());
	}

	public void testExplicitOverrideWinsOverDerivationWhenStillSupported() {
		MagicCard printing = new MagicCard();
		printing.setFinishes("nonfoil,foil,etched"); // would auto-derive Nonfoil (no foil tag)
		MagicCardPhysical copy = copyOf(printing);
		copy.setFinish(CardFinish.FOIL); // the user says otherwise
		assertEquals(CardFinish.FOIL, copy.getFinish());
		assertEquals(CardFinish.FOIL, copy.getRawFinish());
	}

	public void testExplicitOverrideNoLongerSupportedFallsBackToFirstSupported() {
		MagicCard printing = new MagicCard();
		printing.setFinishes("etched"); // etched-only - Foil isn't a real option here
		MagicCardPhysical copy = copyOf(printing);
		copy.setFinish(CardFinish.FOIL); // stale / imported from elsewhere / hand-edited XML
		// getFinish() self-heals without touching the stored value...
		assertEquals(CardFinish.ETCHED, copy.getFinish());
		assertEquals("the raw override itself is untouched until setMagicCard() runs",
				CardFinish.FOIL, copy.getRawFinish());
	}

	public void testSetMagicCardRevertsAnExplicitOverrideThatNoLongerApplies() {
		MagicCard nonfoilOnly = new MagicCard();
		nonfoilOnly.setFinishes("nonfoil");
		MagicCardPhysical copy = copyOf(nonfoilOnly);
		copy.setFinish(CardFinish.FOIL);
		assertEquals(CardFinish.FOIL, copy.getRawFinish());

		MagicCard etchedOnly = new MagicCard();
		etchedOnly.setFinishes("etched");
		copy.setMagicCard(etchedOnly); // a Set/CollNum edit swapping the printing
		// no need to set Finish back to Auto by hand - it lands on the first
		// finish the new printing actually supports, same as CollNum landing on
		// the lowest collector number after a Set edit
		assertEquals(CardFinish.ETCHED, copy.getRawFinish());
		assertEquals(CardFinish.ETCHED, copy.getFinish());
	}

	public void testSetMagicCardLeavesAStillValidExplicitOverrideAlone() {
		MagicCard printingA = new MagicCard();
		printingA.setFinishes("nonfoil,foil");
		MagicCardPhysical copy = copyOf(printingA);
		copy.setFinish(CardFinish.FOIL);

		MagicCard printingB = new MagicCard();
		printingB.setFinishes("nonfoil,foil,etched"); // still offers Foil
		copy.setMagicCard(printingB);
		assertEquals("Foil is still a valid choice for the new printing - leave it alone",
				CardFinish.FOIL, copy.getRawFinish());
	}

	public void testSetMagicCardLeavesAutoAlone() {
		MagicCard nonfoilOnly = new MagicCard();
		nonfoilOnly.setFinishes("nonfoil");
		MagicCardPhysical copy = copyOf(nonfoilOnly);
		assertNull("never explicitly set", copy.getRawFinish());

		MagicCard etchedOnly = new MagicCard();
		etchedOnly.setFinishes("etched");
		copy.setMagicCard(etchedOnly);
		assertNull("Auto stays Auto - nothing to revert", copy.getRawFinish());
		assertEquals(CardFinish.ETCHED, copy.getFinish());
	}

	public void testSupportedFinishesFallsBackToNonfoilAndFoilWhenUnknown() {
		// Etched is the exotic case - never offered without Scryfall data
		// positively confirming this specific printing has it
		MagicCard printing = new MagicCard();
		assertEquals(java.util.EnumSet.of(CardFinish.NONFOIL, CardFinish.FOIL), printing.getSupportedFinishes());
	}

	public void testSupportedFinishesReflectsFinishesData() {
		MagicCard printing = new MagicCard();
		printing.setFinishes("nonfoil,foil");
		assertEquals(java.util.EnumSet.of(CardFinish.NONFOIL, CardFinish.FOIL), printing.getSupportedFinishes());
	}

	public void testEtchedNeverOfferedWithoutPositiveConfirmation() {
		MagicCard unknown = new MagicCard(); // no FINISHES data at all yet
		assertFalse(unknown.getSupportedFinishes().contains(CardFinish.ETCHED));

		MagicCard confirmedNotEtched = new MagicCard();
		confirmedNotEtched.setFinishes("nonfoil,foil"); // explicitly confirmed - never etched
		assertFalse(confirmedNotEtched.getSupportedFinishes().contains(CardFinish.ETCHED));
	}

	public void testEtchedOnlySupportsOnlyEtched() {
		MagicCard printing = new MagicCard();
		printing.setFinishes("etched");
		assertEquals(java.util.EnumSet.of(CardFinish.ETCHED), printing.getSupportedFinishes());
	}

	public void testClearingTheOverrideGoesBackToDerivation() {
		// unknown finishes data -> supported is {Nonfoil, Foil} (never Etched
		// without positive confirmation), so Foil is the override to use here
		MagicCard printing = new MagicCard();
		MagicCardPhysical copy = copyOf(printing);
		copy.setFinish(CardFinish.FOIL);
		assertEquals(CardFinish.FOIL, copy.getFinish());
		copy.setFinish(null);
		assertNull(copy.getRawFinish());
		assertEquals(CardFinish.NONFOIL, copy.getFinish()); // back to the derived default
	}

	// --- CardFinish.joinTags() / joinLabels() --------------------------------

	public void testJoinTagsAndLabelsAreInEnumOrderRegardlessOfInputOrder() {
		java.util.EnumSet<CardFinish> shuffled = java.util.EnumSet.of(CardFinish.ETCHED, CardFinish.NONFOIL);
		assertEquals("nonfoil,etched", CardFinish.joinTags(shuffled));
		assertEquals("Nonfoil, Etched", CardFinish.joinLabels(shuffled));
	}

	public void testJoinTagsDegeneratesToASingleTagForOneElement() {
		assertEquals("foil", CardFinish.joinTags(java.util.EnumSet.of(CardFinish.FOIL)));
		assertEquals("Foil", CardFinish.joinLabels(java.util.EnumSet.of(CardFinish.FOIL)));
	}

	// --- MagicCardField.FINISH.get() on a plain printing (Printings/Scryfall
	// database view - no specific owned copy) must show what THAT PRINTING
	// supports, never an aggregate of the user's own copies elsewhere ---------

	public void testFieldGetOnAPrintingReturnsItsOwnSupportedFinishesNotAnAggregate() {
		MagicCard printing = new MagicCard();
		printing.setFinishes("nonfoil,foil");
		assertEquals("nonfoil,foil", printing.get(MagicCardField.FINISH));
	}

	public void testFieldGetOnAPrintingWithUnknownDataStillReturnsSomethingNeverBlank() {
		MagicCard printing = new MagicCard(); // no FINISHES data - the exact
												// symptom reported: "many cards
												// don't have any finish" in the
												// database view
		assertEquals("nonfoil,foil", printing.get(MagicCardField.FINISH));
	}

	public void testFieldGetOnAnOwnedCopyIsItsOwnSingleFinishAsAOneItemList() {
		MagicCard printing = new MagicCard();
		MagicCardPhysical copy = copyOf(printing);
		copy.setFinish(CardFinish.FOIL);
		assertEquals("foil", copy.get(MagicCardField.FINISH));
	}

	// --- FilterField.FINISH: a whole tag inside the comma list, never a
	// bare substring ("foil" must not match inside "nonfoil") ----------------

	public void testFilterMatchesASingleTagOwnedCopy() {
		MagicCard printing = new MagicCard();
		MagicCardPhysical copy = copyOf(printing); // Nonfoil by default
		Expr foilExpr = FilterField.FINISH.valueExpr("Foil");
		Expr nonfoilExpr = FilterField.FINISH.valueExpr("Nonfoil");
		assertFalse("this copy is Nonfoil, not Foil", foilExpr.evaluate(copy));
		assertTrue(nonfoilExpr.evaluate(copy));
	}

	public void testFilterDoesNotFalsePositiveFoilInsideNonfoil() {
		MagicCard nonfoilOnlyPrinting = new MagicCard();
		nonfoilOnlyPrinting.setFinishes("nonfoil"); // the reported bug case
		Expr foilExpr = FilterField.FINISH.valueExpr("Foil");
		Expr nonfoilExpr = FilterField.FINISH.valueExpr("Nonfoil");
		assertFalse("\"nonfoil\" must never match a \"Foil\" filter", foilExpr.evaluate(nonfoilOnlyPrinting));
		assertTrue(nonfoilExpr.evaluate(nonfoilOnlyPrinting));
	}

	public void testFilterMatchesAnyTagAPrintingSupports() {
		MagicCard multiFinish = new MagicCard();
		multiFinish.setFinishes("nonfoil,foil,etched");
		assertTrue(FilterField.FINISH.valueExpr("Nonfoil").evaluate(multiFinish));
		assertTrue(FilterField.FINISH.valueExpr("Foil").evaluate(multiFinish));
		assertTrue(FilterField.FINISH.valueExpr("Etched").evaluate(multiFinish));
	}

	public void testFilterRegexIsWellFormedForEveryFinish() {
		// sanity check the raw pattern itself is valid regex, independent of
		// the evaluation plumbing above
		for (CardFinish f : CardFinish.values()) {
			Pattern.compile("(^|,)" + Pattern.quote(f.toString()) + "($|,)");
		}
	}

	// --- MagicCardPhysical#getDbPrice(): reads the exact Finish bucket, no
	// fallback to another finish's price when that bucket is empty ----------

	private static MagicCard uniquePrinting(String testName) {
		MagicCard printing = new MagicCard();
		printing.setCardId("CardFinishTest#" + testName);
		printing.setFinishes("nonfoil,foil,etched");
		return printing;
	}

	public void testGetDbPriceReadsTheBucketMatchingTheCopysFinish() {
		MagicCard printing = uniquePrinting("testGetDbPriceReadsTheBucketMatchingTheCopysFinish");
		com.reflexit.magiccards.core.DataManager.getDBPriceStore().setDbPrice(printing, 1.0f);
		com.reflexit.magiccards.core.DataManager.getDBPriceStore().setDbPriceFoil(printing, 2.0f);
		com.reflexit.magiccards.core.DataManager.getDBPriceStore().setDbPriceEtched(printing, 3.0f);

		MagicCardPhysical copy = copyOf(printing);
		copy.setFinish(CardFinish.NONFOIL);
		assertEquals(1.0f, copy.getDbPrice(), 0.0001f);
		copy.setFinish(CardFinish.FOIL);
		assertEquals(2.0f, copy.getDbPrice(), 0.0001f);
		copy.setFinish(CardFinish.ETCHED);
		assertEquals(3.0f, copy.getDbPrice(), 0.0001f);
	}

	public void testGetDbPriceDoesNotFallBackToAnotherFinishWhenItsOwnBucketIsEmpty() {
		MagicCard printing = uniquePrinting("testGetDbPriceDoesNotFallBackToAnotherFinishWhenItsOwnBucketIsEmpty");
		// only the normal bucket is priced - foil/etched are left empty
		com.reflexit.magiccards.core.DataManager.getDBPriceStore().setDbPrice(printing, 1.0f);

		MagicCardPhysical copy = copyOf(printing);
		copy.setFinish(CardFinish.FOIL);
		assertEquals("no fallback to the normal price", 0f, copy.getDbPrice(), 0.0001f);
		copy.setFinish(CardFinish.ETCHED);
		assertEquals("no fallback to the normal price", 0f, copy.getDbPrice(), 0.0001f);
	}
}
