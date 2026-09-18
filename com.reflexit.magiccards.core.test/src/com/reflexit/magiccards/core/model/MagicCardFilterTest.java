/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - removed testCOMMUNITYRATING() (community rating is
 *                         not a concept this app tracks anymore)
 *     Rémi Dutil (2026) - testPOWER/TOUGHNESS/CCC/DBPRICE/COLLNUM now exercise
 *                         the "min:max" range format via the new
 *                         genericRangeFieldText()/rangeFieldCheck() helpers
 *     Rémi Dutil (2026) - Finish "Only" mode tests (contains vs. is only)
 *     Rémi Dutil (2026) - testFinishOnlyExplicitlyFalseStillMeansContains():
 *                         locks in the map.containsKey() -> "true".equals()
 *                         fix in MagicCardFilter#createFinishGroup()
 *     Rémi Dutil (2026) - replaced the Only-mode tests with And-mode ones
 *                         (contains several checked finishes at once, never
 *                         excludes an unchecked one) - same rename as
 *                         CardFinishes.ONLY_ID -> AND_ID
 *     Rémi Dutil (2026) - And redefined as an exact match: replaced
 *                         testFinishAndDoesNotExcludeUncheckedFinishes with
 *                         testFinishAndExcludesUncheckedFinishes (opposite
 *                         expectation) and added
 *                         testFinishAndSingleCheckedMeansExactlyThatFinish
 *     Rémi Dutil (2026) - split each old *Identity test in two, covering both
 *                         sides of the Identity/Extended Identity swap:
 *                         testColorBlackOrRedIdentity/testColorBlackAndRedIdentity/
 *                         testColorlessIdentity now test strict Identity via
 *                         the new mcpIdentity() helper (an explicit
 *                         MagicCardField.COLOR_IDENTITY value, the way
 *                         ParseScryFallChecklist actually populates it from
 *                         Scryfall) instead of inferring anything from cost;
 *                         testColorIdentityNeverInfersFromCostOrOracleText
 *                         locks that in. The original cost/oracle-text-based
 *                         versions moved to
 *                         testColorBlackOrRedExtendedIdentity/
 *                         testColorBlackAndRedExtendedIdentity/
 *                         testColorlessExtendedIdentity (ColorTypes.EXTENDED_ID)
 *                         - unchanged otherwise, that heuristic is exactly
 *                         what they were already testing
 */
package com.reflexit.magiccards.core.model;

import java.util.HashMap;

import org.junit.Test;

import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.expr.BinaryExpr;
import com.reflexit.magiccards.core.model.expr.Expr;
import com.reflexit.unittesting.CardGenerator;

import junit.framework.TestCase;

public class MagicCardFilterTest extends TestCase {
	MagicCardFilter filter;
	private HashMap<String, String> propMap;
	private MagicCard mc;
	private MagicCardPhysical mcp;

	@Override
	public void setUp() {
		propMap = new HashMap<String, String>();
		filter = new MagicCardFilter();
		mc = CardGenerator.generateCardWithValues();
		mcp = mcp();
	}

	private void search(String expr, String text) {
		search(expr, text, true);
	}

	private void search(String expr, String text, boolean exp) {
		Expr e = BinaryExpr.textSearch(MagicCardField.ORACLE, expr);
		MagicCard mc = new MagicCard();
		mc.setOracleText(text);
		boolean res = e.evaluate(mc);
		assertEquals(exp, res);
	}

	@Test
	public void testTextSearchWord() {
		search("word", "bla bla word");
		search("word", "bla word bla");
	}

	@Test
	public void testTextSearchCaseIns() {
		search("word", "bla bla Word");
		search("Word", "bla word bla");
	}

	@Test
	public void testTextSearchBound() {
		search("wor", "bla bla Word", false);
	}

	@Test
	public void testTextSearchSym() {
		search("1/1", "1/1 creatures");
		search("1/*", "1/* creatures");
	}

	@Test
	public void testTextSearchNot() {
		search("flying -with", "create with flying", false);
		search("flying -with", "Flying", true);
	}

	@Test
	public void testTextSearchAnd() {
		search("with flying", "create with flying");
		search("with flying", "with fear and flying");
	}

	@Test
	public void testTextSearchQuoted() {
		search("\"with flying\"", "creature with flying");
		search("\"with flying\"", "with fear and flying", false);
	}

	@Test
	public void testTextSearchRegex() {
		search("m/^$/", "");
		search("m/^$/", "with fear and flying", false);
	}

	@Test
	public void testTextSearchRegex2() {
		search("m/\\*/", "1/*");
	}

	private void searchType(String expr, String text, boolean expected) {
		Expr e = BinaryExpr.textSearch(MagicCardField.TYPE, expr);
		MagicCard mc = new MagicCard();
		mc.setType(text);
		boolean res = e.evaluate(mc);
		assertEquals(expected, res);
	}

	@Test
	public void testBasicType() {
		searchType("Artifact", "Artifact", true);
		searchType("Artifact", "Artifact Creature", true);
	}

	public void setQuickFilter(FilterField filterField, String value) {
		propMap.put(filterField.getPrefConstant(), value);
		filter.update(propMap);
	}

	public void setFilterTrue(String... ids) {
		for (int i = 0; i < ids.length; i++) {
			propMap.put(ids[i], "true");
		}
		filter.update(propMap);
	}

	public void genericFieldText(FilterField ff, String value) {
		setQuickFilter(ff, value);
		checkNotFound();
		mcp.set(ff.getField(), value);
		checkFound();
	}

	public void genericFieldTextNot(FilterField ff, String value) {
		setQuickFilter(ff, value);
		checkFound();
		mcp.set(ff.getField(), value);
		checkNotFound();
	}

	public void checkNotFound() {
		checkNotFound(mcp);
	}

	public void checkNotFound(ICard o) {
		assertTrue("Card matches the filter, but should not " + filter + " " + o, filter.isFiltered(o));
	}

	public void testNameBoo() {
		setQuickFilter(FilterField.NAME_LINE, "Boo");
		assertTrue(filter.isFiltered(mc));
		mc.setName("Boo");
		assertFalse(filter.isFiltered(mc));
		mc.setName("Boo Hoo");
		assertFalse(filter.isFiltered(mc));
	}

	public void testNameWildcard() {
		setQuickFilter(FilterField.NAME_LINE, "Bo*");
		mc.setName("Bo");
		assertTrue("no character after 'Bo' - the wildcard needs exactly one", filter.isFiltered(mc));
		mc.setName("Boo");
		assertFalse("'o' satisfies the single-character wildcard", filter.isFiltered(mc));
		mc.setName("Bob");
		assertFalse("any single character satisfies the wildcard, not just 'o'", filter.isFiltered(mc));
	}

	public void testNameWildcardQuoted() {
		// QuickFilterControl (the toolbar name filter) always wraps its stored
		// value in a literal "\"...\"" pair - the wildcard branch must strip
		// that itself, since it bypasses the tokenizer that normally would
		setQuickFilter(FilterField.NAME_LINE, "\"Bo*\"");
		mc.setName("Bo");
		assertTrue("no character after 'Bo' - the wildcard needs exactly one", filter.isFiltered(mc));
		mc.setName("Boo");
		assertFalse("'o' satisfies the single-character wildcard", filter.isFiltered(mc));
	}

	public void testNameBooPhy() {
		String boo = "Boo";
		setQuickFilter(FilterField.NAME_LINE, boo);
		mcp.getBase().setName(boo);
		assertEquals(boo, mcp.getName());
		checkFound();
	}

	public void testTEXT_LINE() {
		genericFieldText(FilterField.TEXT_LINE, "Boo");
	}

	public void testTYPE_LINE() {
		genericFieldText(FilterField.TYPE_LINE, "Boo");
	}

	public void testNAME_LINE() {
		genericFieldText(FilterField.NAME_LINE, "Boo");
	}

	public void testPOWER() {
		genericRangeFieldText(FilterField.POWER, 15);
	}

	public void testTOUGHNESS() {
		genericRangeFieldText(FilterField.TOUGHNESS, 22);
	}

	public void testEDITION() {
		FilterField ff = FilterField.EDITION;
		propMap.put(Editions.getInstance().getPrefConstantByName("Alara Reborn"), "true");
		filter.update(propMap);
		checkNotFound();
		mcp.set(ff.getField(), "Alara Reborn");
		checkFound();
	}

	/** "Foil" checked (no And) matches a printing that offers Foil among
	 *  other finishes too - the default "any of these" (OR) behavior. */
	public void testFinishOrMatchesAPrintingOfferingSeveralFinishes() {
		mc.set(MagicCardField.FINISHES, "foil,etched");
		propMap.put(CardFinishes.getInstance().getPrefConstant("Foil"), "true");
		filter.update(propMap);
		checkFound(mc);
	}

	/** "Foil" + "Etched" + And: must offer both at once - a printing with only
	 *  one of the two does not match. */
	public void testFinishAndRequiresEveryCheckedFinishAtOnce() {
		MagicCard both = new MagicCard();
		both.set(MagicCardField.FINISHES, "foil,etched");
		MagicCard foilOnly = new MagicCard();
		foilOnly.set(MagicCardField.FINISHES, "nonfoil,foil");

		propMap.put(CardFinishes.getInstance().getPrefConstant("Foil"), "true");
		propMap.put(CardFinishes.getInstance().getPrefConstant("Etched"), "true");
		propMap.put(CardFinishes.AND_ID, "true");
		filter.update(propMap);
		checkFound(both);
		checkNotFound(foilOnly);
	}

	/** "Foil" + "Etched" + And is an exact match: a printing that also offers
	 *  Nonfoil (unchecked) does not match, even though it does offer both
	 *  checked finishes - And excludes every unchecked finish too. */
	public void testFinishAndExcludesUncheckedFinishes() {
		MagicCard allThree = new MagicCard();
		allThree.set(MagicCardField.FINISHES, "nonfoil,foil,etched");
		propMap.put(CardFinishes.getInstance().getPrefConstant("Foil"), "true");
		propMap.put(CardFinishes.getInstance().getPrefConstant("Etched"), "true");
		propMap.put(CardFinishes.AND_ID, "true");
		filter.update(propMap);
		checkNotFound(allThree);
	}

	/** "Etched" + And matches only printings offering Etched alone - the
	 *  exact-match reading of a single checked finish. */
	public void testFinishAndSingleCheckedMeansExactlyThatFinish() {
		MagicCard etchedOnly = new MagicCard();
		etchedOnly.set(MagicCardField.FINISHES, "etched");
		MagicCard regularAndEtched = new MagicCard();
		regularAndEtched.set(MagicCardField.FINISHES, "nonfoil,etched");

		propMap.put(CardFinishes.getInstance().getPrefConstant("Etched"), "true");
		propMap.put(CardFinishes.AND_ID, "true");
		filter.update(propMap);
		checkFound(etchedOnly);
		checkNotFound(regularAndEtched);
	}

	/**
	 * Regression: {@code AbstractMagicCardsListControl#storeToMap()} puts an
	 * explicit {@code "false"} entry for an unchecked box, not merely omits
	 * the key - {@code map.containsKey(AND_ID)} would have read this as "on"
	 * regardless. This is the shape a real, unchecked "And" box actually
	 * produces: "Foil" checked, And explicitly "false" - must behave as plain
	 * "any of these" (OR), the same as
	 * {@link #testFinishOrMatchesAPrintingOfferingSeveralFinishes}.
	 */
	public void testFinishAndExplicitlyFalseStillMeansOr() {
		mc.set(MagicCardField.FINISHES, "foil,etched");
		propMap.put(CardFinishes.getInstance().getPrefConstant("Foil"), "true");
		propMap.put(CardFinishes.AND_ID, "false");
		filter.update(propMap);
		checkFound(mc);
	}

	public void testCCC() {
		mcp.set(MagicCardField.COST, "{3}");
		rangeFieldCheck(FilterField.CCC, 3);
	}

	protected void genericFieldText(FilterField ff, int i) {
		genericFieldText(ff, String.valueOf(i));
		intFieldCheck(ff, i);
	}

	protected void intFieldCheck(FilterField ff, int value) {
		setQuickFilter(ff, String.valueOf(value));
		checkFound();
		setQuickFilter(ff, "= " + value);
		checkFound();
		setQuickFilter(ff, "==" + value);
		checkFound();
		setQuickFilter(ff, ">=" + value);
		checkFound();
		setQuickFilter(ff, "<=" + value);
		checkFound();
		setQuickFilter(ff, "<= " + (value - 1));
		checkNotFound();
		setQuickFilter(ff, ">= 0");
		checkFound();
		setQuickFilter(ff, "=" + value);
		checkFound();
	}

	/**
	 * Power/Toughness/Converted CC/Online Price/Collector's Number now filter
	 * on a "&lt;min&gt;:&lt;max&gt;" range instead of a single =/&lt;=/&gt;=
	 * comparison - see {@code RangeComparisonFieldEditor} /
	 * {@code BinaryExpr.fieldRange}.
	 */
	protected void genericRangeFieldText(FilterField ff, int i) {
		genericFieldText(ff, String.valueOf(i));
		rangeFieldCheck(ff, i);
	}

	protected void rangeFieldCheck(FilterField ff, int value) {
		// exact value via "min:max"
		setQuickFilter(ff, value + ":" + value);
		checkFound();
		// open-ended minimum (">= value" equivalent)
		setQuickFilter(ff, value + ":");
		checkFound();
		setQuickFilter(ff, (value + 1) + ":");
		checkNotFound();
		// open-ended maximum ("<= value" equivalent)
		setQuickFilter(ff, ":" + value);
		checkFound();
		setQuickFilter(ff, ":" + (value - 1));
		checkNotFound();
		// fully unbounded minimum
		setQuickFilter(ff, "0:");
		checkFound();
	}

	public void testCOUNT() {
		mcp.setCount(1);
		genericFieldText(FilterField.COUNT, 3);
	}

	public void checkFound() {
		checkFound(mcp);
	}

	public void checkFound(ICard o) {
		assertFalse("Not matching " + filter, filter.isFiltered(o));
	}

	public void testPRICE() {
		genericFieldText(FilterField.PRICE, 2);
	}

	public void testDBPRICE() {
		genericRangeFieldText(FilterField.DBPRICE, 2);
	}

	public void testCOLLNUM() {
		genericRangeFieldText(FilterField.COLLNUM, 23);
	}

	public void testARTIST() {
		genericFieldText(FilterField.ARTIST, "Boo");
	}

	public void testCOMMENT() {
		genericFieldText(FilterField.COMMENT, "Boo");
	}

	public void testFORMAT() {
		genericFieldText(FilterField.FORMAT, "Standard");
	}

	public void testFORMAT2() {
		FilterField ff = FilterField.FORMAT;
		setQuickFilter(ff, "Standard");
		checkNotFound();
		mcp.set(ff.getField(), "Modern");
		checkNotFound();
	}

	public void testOWNERSHIP() {
		mcp.setOwn(true);
		mcp.setCount(1);
		genericFieldText(FilterField.OWNERSHIP, "false");
	}

	public void testTEXT_LINE_2() {
		genericFieldText(FilterField.TEXT_LINE_2, "Boo");
	}

	public void testTEXT_LINE_3() {
		genericFieldText(FilterField.TEXT_LINE_3, "Boo");
	}

	public void testTEXT_NOT_1() {
		genericFieldTextNot(FilterField.TEXT_NOT_1, "Boo");
	}

	public void testTEXT_NOT_2() {
		genericFieldTextNot(FilterField.TEXT_NOT_2, "Boo");
	}

	public void testTEXT_NOT_3() {
		genericFieldTextNot(FilterField.TEXT_NOT_3, "Boo");
	}

	public void testFORTRADECOUNT() {
		genericFieldText(FilterField.SPECIAL, "fortrade");
	}

	public void testSPECIAL() {
		genericFieldText(FilterField.SPECIAL, "promo");
	}

	public void testLANG() {
		genericFieldText(FilterField.LANG, "French");
	}

	private static final String BLACK_COST = "{B}";
	private static final String RED_COST = "{R}";
	private static final String WHITE_COST = "{W}";

	public void testColorBlack() {
		mcp.getCard().setCost(RED_COST);
		String id = Colors.getInstance().getPrefConstant(Colors.getColorName(BLACK_COST));
		setFilterTrue(id);
		checkNotFound();
		mcp.getCard().setCost(BLACK_COST);
		checkFound();
	}

	String black_id = Colors.getInstance().getPrefConstant(Colors.getColorName(BLACK_COST));
	String red_id = Colors.getInstance().getPrefConstant(Colors.getColorName(RED_COST));

	public MagicCardPhysical mcp() {
		return CardGenerator.generatePhysicalCardWithValues();
	}

	public MagicCardPhysical mcpCost(String cost) {
		MagicCardPhysical b = mcp();
		b.getCard().setCost(cost);
		return b;
	}

	public void testColorBlackOrRed() {
		MagicCardPhysical b = mcpCost(BLACK_COST);
		MagicCardPhysical r = mcpCost(RED_COST);
		MagicCardPhysical w = mcpCost(WHITE_COST);
		MagicCardPhysical wb = mcpCost(WHITE_COST + BLACK_COST);
		setFilterTrue(black_id, red_id);
		checkFound(b);
		checkFound(r);
		checkNotFound(w);
		checkFound(wb);
	}

	public void testColorBlackOrRedOnly() {
		MagicCardPhysical b = mcpCost(BLACK_COST);
		MagicCardPhysical r = mcpCost(RED_COST);
		MagicCardPhysical w = mcpCost(WHITE_COST);
		MagicCardPhysical wb = mcpCost(WHITE_COST + BLACK_COST);
		MagicCardPhysical br = mcpCost(BLACK_COST + RED_COST);
		setFilterTrue(black_id, red_id, ColorTypes.ONLY_ID);
		checkFound(b);
		checkFound(r);
		checkNotFound(w);
		checkNotFound(wb);
		checkFound(br);
		setFilterTrue(black_id, red_id, ColorTypes.ONLY_ID, ColorTypes.AND_ID);
		checkNotFound(b);
		checkNotFound(r);
		checkNotFound(w);
		checkNotFound(wb);
		checkFound(br);
		setFilterTrue(black_id, red_id, ColorTypes.AND_ID);
		MagicCardPhysical wbr = mcpCost(WHITE_COST + BLACK_COST + RED_COST);
		checkNotFound(b);
		checkNotFound(r);
		checkNotFound(w);
		checkNotFound(wb);
		checkFound(br);
		checkFound(wbr);
	}

	/** Identity (strict, no Extended Identity) - matches only against an
	 *  EXPLICIT {@link MagicCardField#COLOR_IDENTITY} value, the way
	 *  ParseScryFallChecklist actually populates it from Scryfall's own
	 *  color_identity array. Never inferred from cost/oracle text - that is
	 *  what {@link #testColorBlackOrRedExtendedIdentity} covers instead. */
	public MagicCardPhysical mcpIdentity(String identityCost) {
		MagicCardPhysical p = mcp();
		p.getCard().set(MagicCardField.COLOR_IDENTITY, identityCost);
		return p;
	}

	public void testColorBlackOrRedIdentity() {
		MagicCardPhysical b = mcpIdentity(BLACK_COST);
		MagicCardPhysical r = mcpIdentity(RED_COST);
		MagicCardPhysical w = mcpIdentity(WHITE_COST);
		MagicCardPhysical wb = mcpIdentity(WHITE_COST + BLACK_COST);
		MagicCardPhysical br = mcpIdentity(BLACK_COST + RED_COST);
		setFilterTrue(black_id, red_id, ColorTypes.IDENTITY_ID);
		checkFound(b);
		checkFound(r);
		checkNotFound(w);
		checkFound(wb);
		checkFound(br);
	}

	/** Strict Identity never falls back to guessing from cost/oracle text -
	 *  a card whose COLOR_IDENTITY was never synced from Scryfall reads as
	 *  colorless here, even though its cost clearly implies a color (that
	 *  cost-based guess is exactly what Extended Identity is for). */
	public void testColorIdentityNeverInfersFromCostOrOracleText() {
		MagicCardPhysical b = mcpCost(BLACK_COST); // cost only, no explicit identity
		setFilterTrue(black_id, ColorTypes.IDENTITY_ID);
		checkNotFound(b);
		b.set(MagicCardField.ORACLE, "{B} - do something"); // black in oracle text either
		checkNotFound(b);
	}

	public void testColorBlackAndRedIdentity() {
		MagicCardPhysical b = mcpIdentity(BLACK_COST);
		MagicCardPhysical r = mcpIdentity(RED_COST);
		MagicCardPhysical w = mcpIdentity(WHITE_COST);
		MagicCardPhysical wb = mcpIdentity(WHITE_COST + BLACK_COST);
		MagicCardPhysical br = mcpIdentity(BLACK_COST + RED_COST);
		setFilterTrue(black_id, red_id, ColorTypes.IDENTITY_ID, ColorTypes.AND_ID);
		checkNotFound(b);
		checkNotFound(r);
		checkNotFound(w);
		checkNotFound(wb);
		checkFound(br);
	}

	// --- Extended Identity (the app's own oracle-text heuristic, inferred
	// live from cost/oracle text - ColorTypes.EXTENDED_ID) -------------------

	/** Regression: Extended Identity must work checked on its own, without
	 *  also needing the plain "Identity" checkbox - the two read as
	 *  independent checkboxes in the UI, and checking only Extended Identity
	 *  used to silently fall through to plain COLOR (ignoring identity
	 *  entirely) because MagicCardFilter#createColorGroup() only looked at
	 *  ColorTypes.EXTENDED_ID inside the "if IDENTITY_ID is checked" branch. */
	public void testColorExtendedIdentityWorksWithoutIdentityCheckbox() {
		MagicCardPhysical b = mcpCost(BLACK_COST);
		MagicCardPhysical w = mcpCost(WHITE_COST);
		setFilterTrue(black_id, ColorTypes.EXTENDED_ID); // no IDENTITY_ID here
		checkFound(b);
		checkNotFound(w);
	}

	public void testColorBlackOrRedExtendedIdentity() {
		MagicCardPhysical b = mcpCost(BLACK_COST);
		MagicCardPhysical r = mcpCost(RED_COST);
		MagicCardPhysical w = mcpCost(WHITE_COST);
		MagicCardPhysical wb = mcpCost(WHITE_COST + BLACK_COST);
		MagicCardPhysical br = mcpCost(BLACK_COST + RED_COST);
		setFilterTrue(black_id, red_id, ColorTypes.IDENTITY_ID, ColorTypes.EXTENDED_ID);
		checkFound(b);
		checkFound(r);
		checkNotFound(w);
		checkFound(wb);
		checkFound(br);
		w.set(MagicCardField.ORACLE, "{W} - do something"); // white in text
		checkNotFound(w);
		w.set(MagicCardField.ORACLE, "{R} - do something"); // red
		checkFound(w);
		w.set(MagicCardField.ORACLE, "{W/R} - do something"); // combined cost
		checkFound(w);
		w.set(MagicCardField.ORACLE, "Red"); // W but not cost
		checkNotFound(w);
	}

	public void testColorBlackAndRedExtendedIdentity() {
		MagicCardPhysical b = mcpCost(BLACK_COST);
		MagicCardPhysical r = mcpCost(RED_COST);
		MagicCardPhysical w = mcpCost(WHITE_COST);
		MagicCardPhysical wb = mcpCost(WHITE_COST + BLACK_COST);
		MagicCardPhysical br = mcpCost(BLACK_COST + RED_COST);
		MagicCardPhysical brh = mcpCost("{B/R}");
		setFilterTrue(black_id, red_id, ColorTypes.IDENTITY_ID, ColorTypes.EXTENDED_ID, ColorTypes.AND_ID);
		checkNotFound(b);
		checkNotFound(r);
		checkNotFound(w);
		checkNotFound(wb);
		checkFound(br);
		checkFound(brh);
		b.set(MagicCardField.ORACLE, "{W} - do something"); // white in text
		checkNotFound(b);
		wb.set(MagicCardField.ORACLE, "{R} - do something"); // red
		checkFound(wb);
		b.set(MagicCardField.ORACLE, "{W/R} - do something"); // combined cost
		checkFound(b);
		wb.set(MagicCardField.ORACLE, "Win"); // W but not cost
		checkNotFound(wb);
	}

	public void testColorBlackOnly() {
		MagicCardPhysical b = mcpCost(BLACK_COST);
		MagicCardPhysical r = mcpCost(RED_COST);
		MagicCardPhysical w = mcpCost(WHITE_COST);
		MagicCardPhysical wb = mcpCost(WHITE_COST + BLACK_COST);
		MagicCardPhysical br = mcpCost(BLACK_COST + RED_COST);
		setFilterTrue(black_id, ColorTypes.ONLY_ID);
		checkFound(b);
		checkNotFound(r);
		checkNotFound(w);
		checkNotFound(wb);
		checkNotFound(br);
		setFilterTrue(black_id, ColorTypes.ONLY_ID, ColorTypes.AND_ID);
		checkFound(b);
		checkNotFound(r);
		checkNotFound(w);
		checkNotFound(wb);
		checkNotFound(br);
		setFilterTrue(black_id, ColorTypes.AND_ID);
		MagicCardPhysical wbr = mcpCost(WHITE_COST + BLACK_COST + RED_COST);
		checkFound(b);
		checkNotFound(r);
		checkNotFound(w);
		checkFound(wb);
		checkFound(br);
		checkFound(wbr);
	}

	/** Strict Identity: "Colorless" only matches a printing Scryfall
	 *  positively CONFIRMED colorless (an empty color_identity array, stored
	 *  as the literal marker {@code "{C}"} - see
	 *  ParseScryFallChecklist#colorIdentityCostString()). A card never synced
	 *  from Scryfall (no explicit COLOR_IDENTITY at all) is "" - unknown, not
	 *  colorless - and does NOT match, same as {@link #testColorlessExtendedIdentity}'s
	 *  own {@code c0} (a literal empty cost) doesn't match either. */
	public void testColorlessIdentity() {
		MagicCardPhysical neverSynced = mcp();
		MagicCardPhysical confirmedColorless = mcpIdentity("{C}");
		MagicCardPhysical black = mcpIdentity(BLACK_COST);
		String colorless_id = Colors.getInstance().getPrefConstant(Colors.getColorName("{C}"));
		setFilterTrue(colorless_id, ColorTypes.IDENTITY_ID);
		checkNotFound(neverSynced);
		checkFound(confirmedColorless);
		checkNotFound(black);
	}

	public void testColorlessExtendedIdentity() {
		MagicCardPhysical c2 = mcpCost("{2}");
		MagicCardPhysical c1 = mcpCost("{1}");
		MagicCardPhysical c0 = mcpCost("");
		MagicCardPhysical wc = mcpCost("{W}{1}");
		MagicCardPhysical w = mcpCost("{W}");
		String colorless_id = Colors.getInstance().getPrefConstant(Colors.getColorName("{C}"));
		setFilterTrue(colorless_id, ColorTypes.IDENTITY_ID, ColorTypes.EXTENDED_ID);
		checkFound(c2);
		checkFound(c1);
		checkNotFound(c0);
		checkFound(wc);
		checkNotFound(w);
	}
}
