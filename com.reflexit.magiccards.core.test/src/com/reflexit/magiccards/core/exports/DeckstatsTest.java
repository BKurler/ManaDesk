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
import java.util.LinkedHashMap;
import java.util.List;

import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.test.assist.MemCardHandler;

import junit.framework.TestCase;

/** Deckstats deck text and collection CSV import / export (plain JUnit, no Eclipse runtime). */
public class DeckstatsTest extends TestCase {
	private List<MagicCardPhysical> run(AbstractImportDelegate importer, String text) throws Exception {
		ImportData data = new ImportData();
		data.setText(text);
		importer.init(data);
		importer.run(ICoreProgressMonitor.NONE);
		if (data.getError() != null)
			throw new Exception(data.getError());
		List<MagicCardPhysical> out = new ArrayList<>();
		for (Object o : data.getList())
			out.add((MagicCardPhysical) o);
		return out;
	}

	private List<MagicCardPhysical> text(String text) throws Exception {
		return run(new DeckstatsTextImportDelegate(), text);
	}

	private List<MagicCardPhysical> csv(String text) throws Exception {
		return run(new DeckstatsCsvImportDelegate(), text);
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
	private String export(AbstractExportDelegate exporter, MagicCardPhysical... cards) throws Exception {
		MemCardHandler store = new MemCardHandler();
		for (MagicCardPhysical c : cards)
			store.getCardStore().add(c);
		store.update();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		exporter.setReportType(ImportExportFactory.createReportType("deckstats-test"));
		exporter.init(out, true, store);
		exporter.run(ICoreProgressMonitor.NONE);
		return out.toString("UTF-8");
	}

	// ---- deck text ----

	/** A real Deckstats export. */
	public void testRealDeckstatsExport() throws Exception {
		List<MagicCardPhysical> c = text("//Main\n1 Lightning Bolt (SLZ) 305\n1 Lightning Bolt (SLZ) 184\n"
				+ "1 Lightning Bolt (STA) 42\n");
		assertEquals(3, c.size());
		assertEquals("Lightning Bolt", c.get(0).getName());
		assertEquals("305", c.get(0).getCollectorId());
		assertEquals("184", c.get(1).getCollectorId());
		assertEquals("42", c.get(2).getCollectorId());
		assertEquals(1, c.get(2).getCount());
	}

	/** A real Deckstats export with a sideboard. */
	public void testRealDeckstatsExportWithSideboard() throws Exception {
		List<MagicCardPhysical> c = text("//Main\n1 Sol Ring (FRC) 21\n\n//Sideboard\n2 Lightning Bolt (MSC) 806\n");
		assertEquals(2, c.size());
		assertFalse(c.get(0).getLocation().isSideboard());
		assertEquals("21", c.get(0).getCollectorId());
		assertTrue(c.get(1).getLocation().isSideboard());
		assertEquals(2, c.get(1).getCount());
		assertEquals("806", c.get(1).getCollectorId());
	}

	public void testTextImport() throws Exception {
		List<MagicCardPhysical> c = text("//Main\n1 Sol Ring (C21) 263\n2 Lightning Bolt # burn\n\n"
				+ "//Sideboard\n2 Negate (M21) 59\nSB: 1 [M21#60] Shock\n\n//Maybeboard\n1 [FRC#39] Brainstorm\n");
		assertEquals(4, c.size()); // the maybeboard is not read
		MagicCardPhysical sol = find(c, "Sol Ring");
		assertEquals(1, sol.getCount());
		assertEquals("263", sol.getCollectorId());
		assertFalse(sol.getLocation().isSideboard());
		assertEquals(2, find(c, "Lightning Bolt").getCount());
		assertTrue(find(c, "Negate").getLocation().isSideboard());
		assertTrue(find(c, "Shock").getLocation().isSideboard());
	}

	public void testTextSbPrefixWithoutHeading() throws Exception {
		List<MagicCardPhysical> c = text("4 [M21#159] Shock\nSB: 2 Negate\n");
		assertEquals(2, c.size());
		assertFalse(c.get(0).getLocation().isSideboard());
		assertTrue(c.get(1).getLocation().isSideboard());
		assertEquals("Negate", c.get(1).getName());
	}

	public void testNotADeckstatsList() throws Exception {
		for (String s : new String[] { "4 Lightning Bolt\n2 Counterspell\n", "1 Sol Ring [C21]\n",
				"Name,Count\nBolt,4\n" }) {
			try {
				text(s);
				fail("should be rejected: " + s);
			} catch (Exception expected) {
				// rejected so Freeform (or another importer) gets a go
			}
		}
	}

	public void testTextExportZonesAndMerge() throws Exception {
		Location deck = Location.createLocation("Burn");
		String t = export(new DeckstatsTextExportDelegate(), card("Lightning Bolt", 2, deck),
				card("Negate", 1, deck.toSideboard()), card("Lightning Bolt", 3, deck),
				card("Treasure", 1, deck.toExtra()));
		assertTrue(t, t.contains("5 Lightning Bolt"));
		assertFalse(t, t.contains("Treasure"));
		assertTrue(t.indexOf("//Sideboard") > t.indexOf("Lightning Bolt"));
		assertTrue(t.indexOf("1 Negate") > t.indexOf("//Sideboard"));
	}

	/** Deckstats calls The List "plist", Scryfall "plst". */
	public void testSetCodesDifferingFromScryfall() throws Exception {
		assertEquals("plist", DeckstatsCsvExportDelegate.deckstatsCode("plst"));
		assertEquals("c21", DeckstatsCsvExportDelegate.deckstatsCode("c21"));
		assertEquals("plst", DeckstatsCsvExportDelegate.scryfallCode("PLIST"));
		assertEquals("c21", DeckstatsCsvExportDelegate.scryfallCode("c21"));
		assertNull(DeckstatsCsvExportDelegate.scryfallCode(null));
		// read back: the card keeps its number whatever the code
		assertEquals("M20-125", text("1 Cavalier of Flame (PLIST) M20-125\n").get(0).getCollectorId());
		assertEquals("M20-125", csv("amount,card_name,set_code,collector_number\n1,Cavalier of Flame,plist,M20-125\n")
				.get(0).getCollectorId());
	}

	public void testLine() {
		assertEquals("Fire // Ice", DeckstatsTextExportDelegate.line("Fire // Ice (split)", null, null));
		assertEquals("Sol Ring", DeckstatsTextExportDelegate.line("Sol Ring", "No Such Set", "263"));
	}

	public void testTextWriteIsReadBack() throws Exception {
		LinkedHashMap<String, Integer> main = new LinkedHashMap<>();
		main.put("Sol Ring (C21) 263", 1);
		LinkedHashMap<String, Integer> side = new LinkedHashMap<>();
		side.put("Negate (M21) 59", 2);
		List<MagicCardPhysical> c = text(DeckstatsTextExportDelegate.write(main, side));
		assertEquals(2, c.size());
		assertEquals("263", c.get(0).getCollectorId());
		assertTrue(c.get(1).getLocation().isSideboard());
		assertEquals(2, c.get(1).getCount());
	}

	public void testExamplesAreImportable() throws Exception {
		assertEquals(4, text(new DeckstatsTextExportDelegate().getExample()).size());
		assertEquals(4, text(new DeckstatsTextImportDelegate().getExample()).size());
		assertEquals(2, csv(new DeckstatsCsvExportDelegate().getExample()).size());
		assertEquals(2, csv(new DeckstatsCsvImportDelegate().getExample()).size());
	}

	// ---- collection CSV ----

	public void testCsvImport() throws Exception {
		List<MagicCardPhysical> c = csv("\"amount\",\"card_name\",\"is_foil\",\"set_code\",\"collector_number\"\n"
				+ "\"3\",\"Sol Ring\",\"0\",\"c21\",\"263\"\n\"1\",\"Lightning Bolt\",\"1\",\"m11\",\"149\"\n\n");
		assertEquals(2, c.size());
		MagicCardPhysical sol = find(c, "Sol Ring");
		assertEquals(3, sol.getCount());
		assertEquals("263", sol.getCollectorId());
		assertNull(sol.getRawFinish());
		assertEquals(CardFinish.FOIL, find(c, "Lightning Bolt").getRawFinish());
	}

	public void testCsvColumnsInAnyOrderAndExtras() throws Exception {
		List<MagicCardPhysical> c = csv("card_name,amount,added,collector_number\nSol Ring,2,2026-01-01,263\n");
		assertEquals(1, c.size());
		assertEquals(2, c.get(0).getCount());
		assertEquals("263", c.get(0).getCollectorId());
	}

	public void testCsvRejectsOtherFormats() throws Exception {
		for (String s : new String[] { "Count,Name,Edition,Card Number\n1,Sol Ring,Commander 2021,263\n",
				"Count,Tradelist Count,Name,Edition,Collector Number\n1,0,Sol Ring,c21,263\n", "4 Lightning Bolt\n" }) {
			try {
				csv(s);
				fail("should be rejected: " + s);
			} catch (Exception expected) {
				// another importer gets a go
			}
		}
	}

	public void testCsvExportAndRoundTrip() throws Exception {
		Location deck = Location.createLocation("Burn");
		MagicCardPhysical a = card("Lightning Bolt", 2, deck);
		a.setFinish(CardFinish.FOIL);
		String t = export(new DeckstatsCsvExportDelegate(), a, card("Shock", 1, deck));
		String[] lines = t.split("\r?\n");
		assertEquals(DeckstatsCsvExportDelegate.HEADER, lines[0]);
		assertTrue(lines[1], lines[1].contains("Lightning Bolt"));
		List<MagicCardPhysical> c = csv(t);
		assertEquals(2, c.size());
		assertEquals(2, find(c, "Lightning Bolt").getCount());
		assertEquals(CardFinish.FOIL, find(c, "Lightning Bolt").getRawFinish());
	}

	public void testAutoDetect() throws Exception {
		ReportType deckstats = ImportExportFactory.createReportType("deckstats-detect Deckstats");
		deckstats.setExtension("txt");
		deckstats.setImportDelegate(new DeckstatsTextImportDelegate());
		ReportType freeform = ImportExportFactory.createReportType("deckstats-detect Freeform");
		freeform.setExtension("txt");
		freeform.setImportDelegate(new FreeformImportDelegate());
		List<ReportType> types = java.util.Arrays.asList(deckstats, freeform);
		assertEquals("deckstats-detect Deckstats",
				ReportType.autoDetectType("//Main\n1 Sol Ring (C21) 263\n2 Negate (M21) 59\n", types).getLabel());
		assertEquals("deckstats-detect Freeform",
				ReportType.autoDetectType("4 Lightning Bolt\n2 Counterspell\n", types).getLabel());
	}
}
