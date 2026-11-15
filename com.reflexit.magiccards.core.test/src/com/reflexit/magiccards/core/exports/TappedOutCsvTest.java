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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.test.assist.MemCardHandler;

import junit.framework.TestCase;

/** TappedOut deck CSV import / export (plain JUnit, no Eclipse runtime). */
public class TappedOutCsvTest extends TestCase {
	private static final String REAL = "Board,Qty,Name,Printing,Foil,Alter,Signed,Condition,Language\n"
			+ "side,1,\"Aang, Swift Savior\",,,,,,\n" //
			+ "main,1,Dread Linnorm / Scale Deflection,,,,,,\n" //
			+ "side,1,Plains,,,,,,\n" //
			+ "main,1,\"Karn, the Great Creator\",,,,,,\n" //
			+ "main,1,Wall of Bone,,,,,,\n";

	private List<MagicCardPhysical> csv(String text) throws Exception {
		ImportData data = new ImportData();
		data.setText(text);
		TappedOutCsvImportDelegate importer = new TappedOutCsvImportDelegate();
		importer.init(data);
		importer.run(ICoreProgressMonitor.NONE);
		if (data.getError() != null)
			throw new Exception(data.getError());
		List<MagicCardPhysical> out = new ArrayList<>();
		for (Object o : data.getList())
			out.add((MagicCardPhysical) o);
		return out;
	}

	private static MagicCardPhysical find(List<MagicCardPhysical> l, String name) {
		for (MagicCardPhysical c : l)
			if (name.equals(c.getName()))
				return c;
		fail("no card " + name);
		return null;
	}

	private static MagicCardPhysical card(String name, int count, Location loc) {
		MagicCard base = new MagicCard();
		base.setName(name);
		MagicCardPhysical c = new MagicCardPhysical(base, loc);
		c.setCount(count);
		return c;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private String export(MagicCardPhysical... cards) throws Exception {
		MemCardHandler store = new MemCardHandler();
		for (MagicCardPhysical c : cards)
			store.getCardStore().add(c);
		store.update();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TappedOutCsvExportDelegate exporter = new TappedOutCsvExportDelegate();
		exporter.setReportType(ImportExportFactory.createReportType("tappedout-test"));
		exporter.init(out, true, store);
		exporter.run(ICoreProgressMonitor.NONE);
		return out.toString("UTF-8");
	}

	public void testRealExport() throws Exception {
		List<MagicCardPhysical> c = csv(REAL);
		assertEquals(5, c.size());
		assertTrue(find(c, "Aang, Swift Savior").getLocation().isSideboard());
		assertTrue(find(c, "Plains").getLocation().isSideboard());
		assertFalse(find(c, "Karn, the Great Creator").getLocation().isSideboard());
		assertEquals(1, find(c, "Wall of Bone").getCount());
		// "Front / Back" is the card "Front // Back"
		assertEquals("Dread Linnorm // Scale Deflection", c.get(1).getName());
	}

	public void testColumnsInAnyOrderAndBoards() throws Exception {
		List<MagicCardPhysical> c = csv("Name,Qty,Board,Foil,Condition\nSol Ring,2,main,foil,NM\n"
				+ "Shock,1,maybe,,\nNegate,3,sideboard,,SP\n\n");
		assertEquals(2, c.size()); // the maybeboard is not read
		MagicCardPhysical sol = find(c, "Sol Ring");
		assertEquals(2, sol.getCount());
		assertEquals(CardFinish.FOIL, sol.getRawFinish());
		assertEquals(CardCondition.NEAR_MINT, sol.getCondition());
		MagicCardPhysical negate = find(c, "Negate");
		assertTrue(negate.getLocation().isSideboard());
		assertEquals(CardCondition.LIGHTLY_PLAYED, negate.getCondition());
	}

	/** A real TappedOut collection export ("-" is an empty cell, the heading "Languange" is TappedOut's). */
	public void testRealCollectionExport() throws Exception {
		List<MagicCardPhysical> c = csv("Qty,Name,Set,Set Number,Foil,Alter,Signed,Condition,Languange,Proxy\n"
				+ "1,The Legend of Yangchen,TLA,27,-,-,-,SL,FR,-\n" //
				+ "1,Lightning Bolt,MSC,806,f,-,-,NM,EN,-\n" //
				+ "1,Lightning Bolt,MSC,806,-,-,-,MP,EN,-\n" //
				+ "1,Lightning Coils,PSAL,I37,-,-,-,NM,EN,-\n" //
				+ "1,Lightning Bolt,STA,42,f-etch,-,-,NM,EN,-\n");
		assertEquals(5, c.size());
		assertEquals(CardFinish.ETCHED, c.get(4).getRawFinish());
		assertEquals("27", c.get(0).getCollectorId());
		assertEquals(CardCondition.LIGHTLY_PLAYED, c.get(0).getCondition());
		assertNull(c.get(0).getRawFinish());
		assertEquals(CardFinish.FOIL, c.get(1).getRawFinish());
		assertEquals(CardCondition.MODERATELY_PLAYED, c.get(2).getCondition());
		assertEquals("I37", c.get(3).getCollectorId());
		assertFalse(c.get(3).isProxy());
	}

	public void testCollectionExportAndRoundTrip() throws Exception {
		Location col = Location.createLocation("Mine");
		MagicCardPhysical a = card("Lightning Bolt", 2, col);
		a.setFinish(CardFinish.FOIL);
		a.setCondition(CardCondition.LIGHTLY_PLAYED);
		MagicCardPhysical b = card("Shock", 1, col);
		b.setProxy(true);
		MemCardHandler store = new MemCardHandler();
		store.getCardStore().add(a);
		store.getCardStore().add(b);
		store.update();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TappedOutCollectionCsvExportDelegate exporter = new TappedOutCollectionCsvExportDelegate();
		exporter.setReportType(ImportExportFactory.createReportType("tappedout-col-test"));
		exporter.init(out, true, store);
		exporter.run(ICoreProgressMonitor.NONE);
		String t = out.toString("UTF-8");
		String[] lines = t.split("\r?\n");
		assertEquals(TappedOutCollectionCsvExportDelegate.HEADER, lines[0]);
		assertTrue(t, t.contains("2,Lightning Bolt,"));
		assertTrue(t, t.contains(",f,-,-,SL,EN,-"));
		List<MagicCardPhysical> c = csv(t);
		assertEquals(2, c.size());
		assertEquals(2, find(c, "Lightning Bolt").getCount());
		assertEquals(CardFinish.FOIL, find(c, "Lightning Bolt").getRawFinish());
		assertEquals(CardCondition.LIGHTLY_PLAYED, find(c, "Lightning Bolt").getCondition());
		assertTrue(find(c, "Shock").isProxy());
		assertTrue(t, t.contains(",NM,EN,TRUE"));
		assertFalse(find(c, "Lightning Bolt").isProxy());
	}

	/** Any value in Alter / Proxy (the file's own codes) is read as set. */
	public void testAlterAndProxyAreReadLeniently() throws Exception {
		List<MagicCardPhysical> c = csv("Qty,Name,Set,Set Number,Foil,Alter,Signed,Condition,Languange,Proxy\n"
				+ "1,Sol Ring,C21,263,-,12345,-,NM,EN,x\n1,Shock,M21,159,-,-,-,NM,EN,-\n");
		assertTrue(c.get(0).isProxy());
		assertTrue(c.get(0).isSpecialTag("altered"));
		assertFalse(c.get(1).isProxy());
		assertFalse(c.get(1).isSpecialTag("altered"));
	}

	public void testCellParsers() {
		assertTrue(TappedOutCsvImportDelegate.isBlank("-"));
		assertEquals(CardFinish.FOIL, TappedOutCsvImportDelegate.finish("f"));
		assertEquals(CardFinish.FOIL, TappedOutCsvImportDelegate.finish("foil"));
		assertEquals(CardFinish.ETCHED, TappedOutCsvImportDelegate.finish("f-etch"));
		assertNull(TappedOutCsvImportDelegate.finish("-"));
		assertEquals("French", TappedOutCsvImportDelegate.language("FR"));
		assertEquals("English", TappedOutCsvImportDelegate.language("EN"));
		assertEquals("Japanese", TappedOutCsvImportDelegate.language("JP"));
		assertNull(TappedOutCsvImportDelegate.language("-"));
		assertEquals(CardCondition.LIGHTLY_PLAYED, TappedOutCsvImportDelegate.condition("SL"));
		assertEquals("SL", TappedOutCollectionCsvExportDelegate.condition(CardCondition.LIGHTLY_PLAYED));
		assertEquals("FR", TappedOutCollectionCsvExportDelegate.languageCode("French"));
	}

	public void testZoneAndCondition() {
		assertEquals(TappedOutCsvImportDelegate.Zone.MAIN, TappedOutCsvImportDelegate.zone("main"));
		assertEquals(TappedOutCsvImportDelegate.Zone.MAIN, TappedOutCsvImportDelegate.zone(""));
		assertEquals(TappedOutCsvImportDelegate.Zone.SIDE, TappedOutCsvImportDelegate.zone(" SIDE "));
		assertEquals(TappedOutCsvImportDelegate.Zone.SKIP, TappedOutCsvImportDelegate.zone("maybe"));
		assertEquals(CardCondition.DAMAGED, TappedOutCsvImportDelegate.condition("DMG"));
		assertEquals(CardCondition.LIGHTLY_PLAYED, TappedOutCsvImportDelegate.condition("Slightly Played"));
		assertNull(TappedOutCsvImportDelegate.condition(""));
	}

	public void testRejectsOtherFormats() throws Exception {
		for (String s : new String[] { "Count,Name,Edition,Card Number\n1,Sol Ring,Commander 2021,263\n",
				"amount,card_name\n1,Sol Ring\n", "Qty,Name\n1,Sol Ring\n", "4 Lightning Bolt\n" }) {
			try {
				csv(s);
				fail("should be rejected: " + s);
			} catch (Exception expected) {
				// another importer gets a go
			}
		}
	}

	public void testExportAndRoundTrip() throws Exception {
		Location deck = Location.createLocation("Burn");
		MagicCardPhysical a = card("Lightning Bolt", 2, deck);
		a.setFinish(CardFinish.FOIL);
		String t = export(a, card("Negate", 1, deck.toSideboard()), card("Treasure", 1, deck.toExtra()),
				card("Fire // Ice", 1, deck));
		String[] lines = t.split("\r?\n");
		assertEquals(TappedOutCsvExportDelegate.HEADER, lines[0]);
		assertFalse(t, t.contains("Treasure")); // the extra list is not written
		assertTrue(t, t.contains("Fire / Ice"));
		assertTrue(t, t.contains("side,1,Negate"));
		List<MagicCardPhysical> c = csv(t);
		assertEquals(3, c.size());
		assertEquals(2, find(c, "Lightning Bolt").getCount());
		assertEquals(CardFinish.FOIL, find(c, "Lightning Bolt").getRawFinish());
		assertTrue(find(c, "Negate").getLocation().isSideboard());
		assertEquals("Fire // Ice", c.get(2).getName());
	}

	public void testExamplesAreImportable() throws Exception {
		assertEquals(3, csv(new TappedOutCsvExportDelegate().getExample()).size());
		assertEquals(3, csv(new TappedOutCsvImportDelegate().getExample()).size());
		assertEquals(2, csv(new TappedOutCollectionCsvExportDelegate().getExample()).size());
	}
}
