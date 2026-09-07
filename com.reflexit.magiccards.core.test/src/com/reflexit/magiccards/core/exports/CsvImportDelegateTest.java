/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.exports;

import java.lang.reflect.InvocationTargetException;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.unittesting.CardGenerator;

@FixMethodOrder(MethodSorters.JVM)
public class CsvImportDelegateTest extends AbstarctImportTest {
	private CsvImportDelegate importd = new CsvImportDelegate();

	private void parseAbove() {
		addLine(getAboveComment());
		parse(importd);
	}

	private void previewAbove() {
		addLine(getAboveComment());
		preview(importd);
	}

	/**
	 * A ManaDesk "Minimum CSV" export must import back through
	 * {@link ManaDeskCsvImportDelegate} - name / set / count / comment kept, no
	 * parse error.
	 */
	@Test
	public void testRoundTripMinimumCsv() {
		MagicCardPhysical a = CardGenerator.generatePhysicalCardWithValues();
		MagicCardPhysical b = CardGenerator.generatePhysicalCardWithValues();
		a.setCount(3);
		a.set(com.reflexit.magiccards.core.model.MagicCardField.COMMENT, "keep me");
		b.setCount(1);
		b.set(com.reflexit.magiccards.core.model.MagicCardField.NAME, "Weird, \"Quoted\" Name");

		MinimumCsvExportDelegate exp = new MinimumCsvExportDelegate();
		exp.setReportType(ImportExportFactory.createReportType("roundtrip"));
		line = exp.export(java.util.Arrays.asList((IMagicCard) a, (IMagicCard) b));

		resolve = false;
		ManaDeskCsvImportDelegate md = new ManaDeskCsvImportDelegate();
		preview(md);
		assertEquals(null, exception);
		assertEquals(2, resSize);
		assertEquals(a.getName(), card1.getName());
		assertEquals(3, ((MagicCardPhysical) card1).getCount());
		assertEquals("keep me", ((MagicCardPhysical) card1).getComment());
		assertEquals("Weird, \"Quoted\" Name", card2.getName());
		assertEquals(1, ((MagicCardPhysical) card2).getCount());
	}

	/**
	 * A "Full CSV" export - deck columns plus card-database columns like COST /
	 * TYPE / RARITY / COLOR_IDENTITY - is accepted: the database columns are
	 * recognised and simply ignored.
	 */
	@Test
	public void manaDeskImport_acceptsFullCsvIgnoringDbColumns() {
		resolve = false;
		addLine("NAME,COST,TYPE,RARITY,COLOR_IDENTITY,SET,COLLNUM,COUNT,COMMENT");
		addLine("Llanowar Elves,{G},Creature - Elf Druid,Common,G,Dominaria,168,4,mine");
		preview(new ManaDeskCsvImportDelegate());
		assertEquals(null, exception);
		assertEquals(1, resSize);
		assertEquals("Llanowar Elves", card1.getName());
		assertEquals("Dominaria", card1.getSet());
		assertEquals(4, ((MagicCardPhysical) card1).getCount());
		assertEquals("mine", ((MagicCardPhysical) card1).getComment());
	}

	/** An unknown header naming (not what ManaDesk writes) is rejected. */
	@Test(expected = MagicException.class)
	public void manaDeskImport_rejectsUnknownHeader() throws Exception {
		resolve = true;
		addLine("Card Name,Quantity");
		addLine("Llanowar Elves,4");
		parseonly(new ManaDeskCsvImportDelegate());
	}

	/** Headers are case-sensitive - ManaDesk writes upper-case. */
	@Test(expected = MagicException.class)
	public void manaDeskImport_rejectsLowerCaseHeader() throws Exception {
		resolve = true;
		addLine("name,set,count");
		addLine("Llanowar Elves,Dominaria,4");
		parseonly(new ManaDeskCsvImportDelegate());
	}

	/** The set column header may be SET / EDITION_ABBR / SET/EDITION_ABBR; the value is a name or an abbr. */
	@Test
	public void manaDeskImport_acceptsSetSlashEditionAbbrHeader() {
		resolve = false;
		addLine("NAME,SET/EDITION_ABBR,COUNT");
		addLine("Accursed Spirit,Magic 2015,2");
		preview(new ManaDeskCsvImportDelegate());
		assertEquals(null, exception);
		assertEquals(1, resSize);
		assertEquals("Accursed Spirit", card1.getName());
		assertEquals("Magic 2015", card1.getSet());
		assertEquals(2, ((MagicCardPhysical) card1).getCount());
	}

	/** A header row with only database columns and no NAME / ID is rejected. */
	@Test(expected = MagicException.class)
	public void manaDeskImport_rejectsNoIdentityColumn() throws Exception {
		resolve = true;
		addLine("COST,TYPE,RARITY,COUNT");
		addLine("{G},Creature,Common,4");
		parseonly(new ManaDeskCsvImportDelegate());
	}

	//
	@Test
	public void testEmpty() {
		parseAbove();
		assertEquals(0, resSize);
	}

	// NAME,COUNT
	@Test
	public void testHeaderOnly() {
		parseAbove();
		assertEquals(0, resSize);
	}

	// NAME,COUNT
	//
	@Test
	public void testBlank() {
		parseAbove();
		assertEquals(0, resSize);
	}

	// ID,NAME,COST,TYPE,POWER,TOUGHNESS,ORACLE,SET,RARITY,DBPRICE,LANG,RATING,ARTIST,COLLNUM,RULINGS,TEXT,ENID,PROPERTIES,COUNT,PRICE,COMMENT,LOCATION,CUSTOM,OWNERSHIP,SPECIAL,DATE
	// -39,name 39,{4},type 39,4,*,bla 39,set 19,Common,1.32256411:3.2,Russian,2.39,Elena 39,39a,,bla <br> bla 39,0,,5,2.1,comment 40,mem,,true,"foil,c=mint",Sun Jan 11 22:37:54 EST 2015
	@Test
	public void testLines() {
		parseAbove();
		assertEquals(1, resSize);
		MagicCardPhysical p = (MagicCardPhysical) card1;
		assertEquals("-39", p.getCardId());
		assertEquals("name 39", p.getName());
		assertEquals("set 19", p.getSet());
		assertEquals("Russian", p.getLanguage());
		assertEquals("bla <br> bla 39", p.getText());
		assertEquals(5, p.getCount());
		assertEquals(2.1f, p.getPrice());
		assertEquals("comment 40", p.getComment());
		assertEquals(true, p.isOwn());
		assertEquals("foil,c=mint", p.getSpecial());
	}

	// NAME,EDITION,QTY
	// Counterspell,Fifth Edition,3
	@Test
	public void test_Alias() {
		parseAbove();
		assertEquals(1, resSize);
		assertEquals("Counterspell", card1.getName());
		assertEquals(3, ((MagicCardPhysical) card1).getCount());
		assertEquals("Fifth Edition", card1.getSet());
	}

	// NAME,TEXT
	// Bla,"Test this exciting ""stuff"""
	@Test
	public void test_Escapes() {
		parseAbove();
		assertEquals(1, resSize);
		assertEquals("Bla", card1.getName());
		assertEquals("Test this exciting \"stuff\"", card1.getText());
	}

	// NAME,COUNT
	//
	// Bla,1
	@Test
	public void test_Black1() {
		parseAbove();
		assertEquals(1, resSize);
		assertEquals("Bla", card1.getName());
	}

	// NAME
	// Bla
	@Test(expected = MagicException.class)
	public void test_Inval() throws InvocationTargetException, InterruptedException {
		addLine(getAboveComment());
		parseonly(importd);
	}

	// Name,Qty
	// Accursed Spirit,1
	@Test(expected = MagicException.class)
	public void test_InvalHeader() throws InvocationTargetException, InterruptedException {
		addLine(getAboveComment());
		parseonly(importd);
	}

	// NAME,QTY,EDITION_ABBR
	// Accursed Spirit,1,M15
	@Test
	public void test_abbr() {
		parseAbove();
		assertEquals(1, resSize);
		assertEquals("Accursed Spirit", card1.getName());
		assertEquals("Magic 2015", card1.getSet());
	}

	// NAME,QTY,EDITION_ABBR
	// Accursed Spirit,1,M15W
	@Test
	public void test_abbr_ukn_resolve() {
		parseAbove();
		assertEquals(1, resSize);
		assertEquals("Accursed Spirit", card1.getName());
		assertEquals("M15W", card1.getSet());
	}

	// NAME,QTY,EDITION_ABBR
	// Hoo,1,M15W
	@Test
	public void test_abbr_ukn_unknown() {
		parseAbove();
		assertEquals(1, resSize);
		assertEquals("Hoo", card1.getName());
		assertEquals("M15W", card1.getSet());
		assertEquals("Set not found", String.valueOf(((MagicCardPhysical) card1).getError()));
	}

	// NAME,COUNT,LOCATION,SIDEBOARD
	// Accursed Spirit,1,deck,true
	// Accursed Spirit,1,deck,
	@Test
	public void test_ignoreLoc() {
		parseAbove();
		assertEquals(2, resSize);
		assertEquals("Accursed Spirit", card1.getName());
		assertEquals("mem-sideboard", ((MagicCardPhysical) card1).getLocation().toString());
		assertEquals("mem", ((MagicCardPhysical) card2).getLocation().toString());
	}

	// NAME,COUNT,FORTRADECOUNT
	// Accursed Spirit,4,1
	@Test
	public void testForTrade() {
		parseAbove();
		assertEquals(2, resSize);
		assertEquals(1, ((MagicCardPhysical) card1).getCount());
		assertEquals(1, ((MagicCardPhysical) card1).getForTrade());
		assertEquals(3, ((MagicCardPhysical) card2).getCount());
		assertEquals(0, ((MagicCardPhysical) card2).getForTrade());
	}

	/*-
	NAME,ID,COST,TYPE,POWER,TOUGHNESS,ORACLE,SET,RARITY,CTYPE,COUNT,LOCATION,OWNERSHIP,COMMENT,PRICE,COLOR,DBPRICE,RATING,ARTIST,COLLNUM,SPECIAL,FORTRADECOUNT,LANG,TEXT,OWN_COUNT,OWN_UNIQUE,LEGALITY,SIDEBOARD,ERROR,DATE,SET_RELEASE
	Blighted Agent,214383,{1}{U},Creature - Human Rogue,1,1,Infect <i>(This creature deals damage to creatures in the form of -1/-1 counters and to players in the form of poison counters.)</i><br>Blighted Agent is unblockable.,New Phyrexia,Common,mono,1,Collections/main,true,,0.0,{1}{U},0.0,0.0,,29,,0,,Infect <i>(This creature deals damage to creatures in the form of -1/-1 counters and to players in the form of poison counters.)</i><br>Blighted Agent is unblockable.,1,1,Extended|Commander-,false,,Wed Feb 11 19:27:06 EST 2015,Sun May 01 00:00:00 EDT 2011
	Blind Zealot,217999,{1}{B}{B},Creature - Human Cleric,2,2,"Intimidate <i>(This creature can't be blocked except by artifact creatures and/or creatures that share a color with it.)</i><br>Whenever Blind Zealot deals combat damage to a player, you may sacrifice it. If you do, destroy target creature that player controls.",New Phyrexia,Common,mono,1,Collections/main,true,,0.0,{1}{B}{B},0.0,0.0,,52,,0,,"Intimidate <i>(This creature can't be blocked except by artifact creatures and/or creatures that share a color with it.)</i><br>Whenever Blind Zealot deals combat damage to a player, you may sacrifice it. If you do, destroy target creature that player controls.",1,1,Extended|Commander-,false,,Wed Feb 11 19:27:06 EST 2015,Sun May 01 00:00:00 EDT 2011
	Birthing Pod,218006,{3}{GP},Artifact,,,"<i>({GP} can be paid with either {G} or 2 life.)</i><br>{1}{GP}, {T}, Sacrifice a creature: Search your library for a creature card with converted mana cost equal to 1 plus the sacrificed creature's converted mana cost, put that card onto the battlefield, then shuffle your library. Activate this ability only any time you could cast a sorcery.",New Phyrexia,Rare,mono,1,Collections/main,true,,0.0,{3}{GP},0.0,0.0,,104,,0,,"<i>({GP} can be paid with either {G} or 2 life.)</i><br>{1}{GP}, {T}, Sacrifice a creature: Search your library for a creature card with converted mana cost equal to 1 plus the sacrificed creature's converted mana cost, put that card onto the battlefield, then shuffle your library. Activate this ability only any time you could cast a sorcery.",1,1,Extended|Commander-,false,,Wed Feb 11 19:27:06 EST 2015,Sun May 01 00:00:00 EDT 2011
	Blinding Souleater,233045,{3},Artifact Creature - Cleric,1,3,"{WP}, {T}: Tap target creature. <i>({WP} can be paid with either {W} or 2 life.)</i>",New Phyrexia,Common,colorless,1,Collections/main,true,,0.0,{3},0.0,0.0,,131,,0,,"{WP}, {T}: Tap target creature. <i>({WP} can be paid with either {W} or 2 life.)</i>",1,1,Extended|Commander-,false,,Wed Feb 11 19:27:06 EST 2015,Sun May 01 00:00:00 EDT 2011
	Blade Splicer,233068,{2}{W},Creature - Human Artificer,1,1,"When Blade Splicer enters the battlefield, put a 3/3 colorless Golem artifact creature token onto the battlefield.<br>Golem creatures you control have first strike.",New Phyrexia,Rare,mono,1,Collections/main,true,,0.0,{2}{W},0.0,0.0,,4,,0,,"When Blade Splicer enters the battlefield, put a 3/3 colorless Golem artifact creature token onto the battlefield.<br>Golem creatures you control have first strike.",1,1,Extended|Commander-,false,,Wed Feb 11 19:27:06 EST 2015,Sun May 01 00:00:00 EDT 2011
	*/
	@Test
	public void testMA1_3_1_14() {
		parseAbove();
		assertEquals(5, resSize);
		assertEquals("ID not found in db", ((MagicCardPhysical) card1).getError().toString());
	}

	/*-
	 NAME,SET,LEGALITY,IMAGE_URL
	 My Card,My New Set,Weird,http://bla
	 */
	@Test
	public void testLegality() {
		previewAbove();
		assertEquals(1, resSize);
		assertEquals("http://bla", card1.getBase().getImageUrl());
		assertEquals("Weird", card1.getBase().getLegalityMap().toExternal());
	}

	/*-
	 NAME,SET,LEGALITY,IMAGE_URL
	 Blighted Agent,N Set,Weird,http://bla
	 */
	@Test
	public void testRegression() {
		previewAbove();
		assertEquals(1, resSize);
		assertEquals("Blighted Agent", card1.getName());
		assertEquals("http://bla", card1.getBase().getImageUrl());
		assertEquals("Weird", card1.getBase().getLegalityMap().toExternal());
	}
}
