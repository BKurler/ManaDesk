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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.test.assist.MemCardHandler;

import junit.framework.TestCase;

/** Moxfield deck text ("Copy for Moxfield") and collection CSV import / export (plain JUnit, no Eclipse runtime). */
public class MoxfieldTest extends TestCase {
	private ImportData data;

	private List<MagicCardPhysical> run(AbstractImportDelegate importer, String text) throws Exception {
		data = new ImportData();
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
		return run(new MoxfieldTextImportDelegate(), text);
	}

	private List<MagicCardPhysical> csv(String text) throws Exception {
		return run(new MoxfieldCsvImportDelegate(), text);
	}

	private static MagicCardPhysical find(List<MagicCardPhysical> l, String name) {
		for (MagicCardPhysical c : l)
			if (name.equals(c.getName()))
				return c;
		fail("no card " + name);
		return null;
	}

	// ---- deck text ----

	public void testRealCopyForMoxfield() throws Exception {
		String s;
		try (InputStream in = MoxfieldTest.class.getResourceAsStream("testdata/moxfield-copy-for-moxfield.txt")) {
			assertNotNull(in);
			s = new String(in.readAllBytes(), "UTF-8");
		}
		List<MagicCardPhysical> c = text(s);
		assertEquals(17, c.size());
		MagicCardPhysical w = c.get(0);
		assertEquals("Wilhelt, the Rotcleaver", w.getName());
		assertEquals(1, w.getCount());
		assertEquals("2", w.getCollectorId());
		assertEquals("CMA-30", find(c, "Arcane Denial").getCollectorId());
		assertEquals("1755", find(c, "Saw in Half").getCollectorId());
		assertEquals(10, find(c, "Island").getCount());
		assertEquals(13, find(c, "Swamp").getCount());
		assertEquals("Grave Researcher // Reanimate", find(c, "Grave Researcher // Reanimate").getName());
		assertEquals("Ashnod's Altar", find(c, "Ashnod's Altar").getName());
		for (MagicCardPhysical card : c)
			assertFalse(card.getLocation() != null && card.getLocation().isSideboard());
	}

	public void testFoilEtchedAndSections() throws Exception {
		List<MagicCardPhysical> c = text("1 Sol Ring (C21) 263 *F*\n2 Atraxa (MUL) 98 *E*\n3 Shock (M21) 159\n\n"
				+ "SIDEBOARD:\n2 Negate (M21) 59\n\nMAYBEBOARD:\n1 Brainstorm (FRC) 39\n");
		assertEquals(4, c.size()); // the maybeboard is not read
		assertEquals(CardFinish.FOIL, c.get(0).getRawFinish());
		assertEquals(CardFinish.ETCHED, c.get(1).getRawFinish());
		assertNull(c.get(2).getRawFinish());
		assertFalse(c.get(2).getLocation().isSideboard());
		assertEquals("Negate", c.get(3).getName());
		assertTrue(c.get(3).getLocation().isSideboard());
	}

	public void testNamesOnlyLinesAreAcceptedBesideAPrinting() throws Exception {
		List<MagicCardPhysical> c = text("COMMANDER:\n1 Atraxa, Praetors' Voice (C16) 28\nDECK:\n4 Lightning Bolt\n");
		assertEquals(2, c.size());
		assertEquals("Lightning Bolt", c.get(1).getName());
		assertEquals(4, c.get(1).getCount());
	}

	public void testNotAMoxfieldList() throws Exception {
		for (String s : new String[] { "4 Lightning Bolt\n2 Counterspell\n", "Name,Count\nBolt,4\n",
				"# my deck\n4 Lightning Bolt\n" }) {
			try {
				text(s);
				fail("should be rejected: " + s);
			} catch (Exception expected) {
				// rejected so Freeform (or another importer) gets a go
			}
		}
	}

	// ---- export ----

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
		exporter.setReportType(ImportExportFactory.createReportType("moxfield-test"));
		exporter.init(out, true, store);
		exporter.run(ICoreProgressMonitor.NONE);
		return out.toString("UTF-8");
	}

	public void testLine() {
		assertEquals("Fire / Ice",
				MoxfieldTextExportDelegate.line("Fire // Ice (split)", null, null, CardFinish.NONFOIL));
		// no known set: the name alone
		assertEquals("Sol Ring", MoxfieldTextExportDelegate.line("Sol Ring", "No Such Set", "263", CardFinish.FOIL));
	}

	public void testTextExportZonesAndMerge() throws Exception {
		Location deck = Location.createLocation("Burn");
		String t = export(new MoxfieldTextExportDelegate(), card("Lightning Bolt", 2, deck),
				card("Negate", 1, deck.toSideboard()), card("Lightning Bolt", 3, deck),
				card("Treasure", 1, deck.toExtra()));
		assertTrue(t, t.contains("5 Lightning Bolt"));
		assertFalse(t, t.contains("Treasure"));
		assertTrue(t, t.indexOf("SIDEBOARD:") > t.indexOf("Lightning Bolt"));
		assertTrue(t, t.indexOf("Negate") > t.indexOf("SIDEBOARD:"));
	}

	public void testTextWriteIsReadBack() throws Exception {
		LinkedHashMap<String, Integer> main = new LinkedHashMap<>();
		main.put("Sol Ring (C21) 263 *F*", 1);
		LinkedHashMap<String, Integer> side = new LinkedHashMap<>();
		side.put("Negate (M21) 59", 2);
		List<MagicCardPhysical> c = text(MoxfieldTextExportDelegate.write(main, side));
		assertEquals(2, c.size());
		assertEquals(CardFinish.FOIL, c.get(0).getRawFinish());
		assertTrue(c.get(1).getLocation().isSideboard());
		// fake cards have no printing: a list of names only is not a Moxfield list
		try {
			text(export(new MoxfieldTextExportDelegate(), card("Sol Ring", 1, Location.createLocation("Burn"))));
			fail("a list with no printing is not a Moxfield list");
		} catch (Exception expected) {
			// ok
		}
	}

	public void testExamplesAreImportable() throws Exception {
		assertEquals(5, text(new MoxfieldTextExportDelegate().getExample()).size());
		assertEquals(5, text(new MoxfieldTextImportDelegate().getExample()).size());
		assertEquals(2, csv(new MoxfieldCsvExportDelegate().getExample()).size());
		assertEquals(2, csv(new MoxfieldCsvImportDelegate().getExample()).size());
	}

	// ---- collection CSV ----

	private static final String CSV = MoxfieldCsvExportDelegate.HEADER + "\n" //
			+ "3,0,Sol Ring,c21,Near Mint,English,,,,263,,,\n" //
			+ "1,5,Arcane Denial,plst,Good (Lightly Played),English,foil,\"red, blue\",,CMA-30,true,true,2.50\n" //
			+ "2,0,Atraxa,mul,Damaged,English,etched,,,98,,,\n" //
			+ "4,0,Shock,m21,Heavily Played,English,,,,159,,,\n" //
			+ "\n";

	public void testCsvImport() throws Exception {
		List<MagicCardPhysical> c = csv(CSV);
		MagicCardPhysical sol = find(c, "Sol Ring");
		assertEquals(3, sol.getCount());
		assertEquals("263", sol.getCollectorId());
		assertEquals(CardCondition.NEAR_MINT, sol.getCondition());
		MagicCardPhysical arcane = find(c, "Arcane Denial");
		assertEquals(CardFinish.FOIL, arcane.getRawFinish());
		assertEquals(CardCondition.LIGHTLY_PLAYED, arcane.getCondition());
		assertEquals("red, blue", arcane.getComment());
		assertTrue(arcane.isSpecialTag("altered"));
		assertTrue(arcane.isProxy());
		assertEquals(2.5f, arcane.getPrice(), 0.001f);
		assertEquals(1, arcane.getCount()); // Tradelist Count 5 never raises Count
		assertEquals(CardFinish.ETCHED, find(c, "Atraxa").getRawFinish());
		assertEquals(CardCondition.DAMAGED, find(c, "Atraxa").getCondition());
		assertEquals(CardCondition.HEAVILY_PLAYED, find(c, "Shock").getCondition());
	}

	/** A real Moxfield collection export (quoted cells, "True" / "False", full language and condition names). */
	public void testRealCollectionExport() throws Exception {
		String s;
		try (InputStream in = MoxfieldTest.class.getResourceAsStream("testdata/moxfield-collection-export.csv")) {
			assertNotNull(in);
			s = new String(in.readAllBytes(), "UTF-8");
		}
		List<MagicCardPhysical> c = csv(s);
		assertEquals(5, c.size());
		for (MagicCardPhysical card : c)
			assertEquals("Lightning Bolt", card.getName());
		assertEquals("806", c.get(0).getCollectorId());
		assertEquals(CardCondition.NEAR_MINT, c.get(0).getCondition());
		assertEquals(CardCondition.NEAR_MINT, c.get(1).getCondition()); // Mint
		assertFalse(c.get(0).isProxy());
		assertTrue(c.get(2).isProxy());
		assertFalse(c.get(2).isSpecialTag("altered"));
		assertEquals(10f, c.get(3).getPrice(), 0.001f);
		assertTrue(c.get(4).isSpecialTag("altered"));
		assertEquals(CardCondition.LIGHTLY_PLAYED, c.get(4).getCondition());
		// the French copy: declared, kept English when the DB has no French printing
		assertNotNull(data.getLanguageOverridden());
	}

	public void testCsvImportColumnsInAnyOrder() throws Exception {
		List<MagicCardPhysical> c = csv("Collector Number,Name,Edition,Count\n263,Sol Ring,c21,2\n");
		assertEquals(1, c.size());
		assertEquals(2, c.get(0).getCount());
		assertEquals("263", c.get(0).getCollectorId());
	}

	public void testCsvRejectsOtherFormats() throws Exception {
		for (String s : new String[] { "Count,Name,Edition,Card Number\n1,Sol Ring,Commander 2021,263\n",
				"Name,Set code,Collector number,Quantity\nSol Ring,C21,263,1\n", "4 Lightning Bolt\n" }) {
			try {
				csv(s);
				fail("should be rejected: " + s);
			} catch (Exception expected) {
				// another importer gets a go
			}
		}
	}

	public void testCsvExport() throws Exception {
		Location deck = Location.createLocation("Burn");
		MagicCardPhysical a = card("Lightning Bolt", 2, deck);
		a.setCondition(CardCondition.DAMAGED);
		a.setFinish(CardFinish.FOIL);
		a.setProxy(true);
		a.setSpecialTag("altered");
		String t = export(new MoxfieldCsvExportDelegate(), a, card("Shock", 1, deck));
		String[] lines = t.split("\r?\n");
		assertEquals(MoxfieldCsvExportDelegate.HEADER, lines[0]);
		assertEquals("2,0,Lightning Bolt,,Damaged,English,foil,,,,true,true,", lines[1]);
		assertEquals("1,0,Shock,,Near Mint,English,,,,,,,", lines[2]);
	}

	public void testCsvExportRoundTrip() throws Exception {
		Location deck = Location.createLocation("Burn");
		MagicCardPhysical a = card("Lightning Bolt", 2, deck);
		a.setCondition(CardCondition.LIGHTLY_PLAYED);
		a.setProxy(true);
		List<MagicCardPhysical> c = csv(export(new MoxfieldCsvExportDelegate(), a));
		assertEquals(1, c.size());
		assertEquals(CardCondition.LIGHTLY_PLAYED, c.get(0).getCondition());
		assertTrue(c.get(0).isProxy());
		assertEquals(2, c.get(0).getCount());
	}

	private static ReportType type(String label, String ext, IImportDelegate delegate) {
		ReportType t = ImportExportFactory.createReportType("moxfield-detect " + label);
		t.setExtension(ext);
		t.setImportDelegate(delegate);
		return t;
	}

	/** Auto-detection in the plugin.xml order: a Moxfield file also parses with Deckbox / Freeform, the first registered wins. */
	public void testAutoDetect() throws Exception {
		java.util.List<ReportType> csvTypes = java.util.Arrays.asList(
				type("Moxfield", "csv", new MoxfieldCsvImportDelegate()),
				type("Deckbox", "csv", new DeckBoxImportDelegate()), type("ManaBox", "csv", new ManaBoxCsvImportDelegate()));
		assertEquals("moxfield-detect Moxfield", ReportType.autoDetectType(CSV, csvTypes).getLabel());
		// a Deckbox file is not taken for a Moxfield one
		String deckbox = "Count,Name,Edition,Card Number\n3,Platinum Angel,Magic 2010,218\n";
		assertEquals("moxfield-detect Deckbox", ReportType.autoDetectType(deckbox, csvTypes).getLabel());
		java.util.List<ReportType> txtTypes = java.util.Arrays.asList(
				type("Moxfield", "txt", new MoxfieldTextImportDelegate()),
				type("Freeform", "txt", new FreeformImportDelegate()));
		String deck = "1 Wilhelt, the Rotcleaver (MIC) 2\n1 Arcane Denial (PLST) CMA-30\n1 Sol Ring (C21) 263 *F*\n";
		assertEquals("moxfield-detect Moxfield", ReportType.autoDetectType(deck, txtTypes).getLabel());
		// a plain list is left to Freeform
		assertEquals("moxfield-detect Freeform",
				ReportType.autoDetectType("4 Lightning Bolt\n2 Counterspell\n", txtTypes).getLabel());
	}

	public void testRegisteredBeforeTheFormatsTheyCouldBeTakenFor() throws Exception {
		ReportType csvT = ImportExportFactory.getByLabel("Moxfield Collection CSV");
		if (csvT == null)
			return; // plugin.xml registry is only there in a plug-in run
		List<ReportType> types = new ArrayList<>(ImportExportFactory.getImportTypes());
		assertTrue(csvT.isApplication());
		assertTrue(types.indexOf(csvT) < types.indexOf(ImportExportFactory.getByLabel("Deckbox Inventory CSV")));
		ReportType txt = ImportExportFactory.getByLabel("Moxfield Deck List");
		assertTrue(types.indexOf(txt) < types.indexOf(ImportExportFactory.getByLabel("Freeform Text")));
		assertNotNull(txt.getExportDelegate());
		assertNotNull(csvT.getExportDelegate());
	}
}
