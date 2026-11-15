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
import java.util.Map;

import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.test.assist.MemCardHandler;

import junit.framework.TestCase;

/** Archidekt deck text and collection CSV import / export (plain JUnit, no Eclipse runtime). */
public class ArchidektTest extends TestCase {
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
		return run(new ArchidektTextImportDelegate(), text);
	}

	private List<MagicCardPhysical> csv(String text) throws Exception {
		return run(new ArchidektCsvImportDelegate(), text);
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
		exporter.setReportType(ImportExportFactory.createReportType("archidekt-test"));
		exporter.init(out, true, store);
		exporter.run(ICoreProgressMonitor.NONE);
		return out.toString("UTF-8");
	}

	/** Lines of a real Archidekt export. */
	public void testRealDeckExport() throws Exception {
		List<MagicCardPhysical> c = text("1x Arcane Signet (dsc) 92 [Ramp] ^Have,#37d67a^\n"
				+ "1x Blessing of Leeches (plst) BOK-62 [Enchantment] ^Have,#37d67a^\n"
				+ "1x Bojuka Bog (soc) 363 [Upgrades{noDeck}{noPrice},Land]\n"
				+ "1x Choked Estuary (trc) 517 [Upgrades{noDeck}{noPrice},Land] ^Getting,#2ccce4^\n"
				+ "1x Fell the Profane // Fell Mire (mh3) 244 [DMFC] ^Have,#37d67a^\n"
				+ "4x Island (vow) 270 [Basics] ^Have,#37d67a^\n"
				+ "1x Sheoldred, the Apocalypse (pdmu) 107p [Creature] ^Have,#37d67a^\n"
				+ "1x Singularity Rupture (eoe) 398 *F* [Wipes] ^Have,#37d67a^\n"
				+ "10x Swamp (vow) 272 [Basics,Land] ^Have,#37d67a^\n"
				+ "1x Toxrill, the Corrosive (vow) 321 [Commander{top}] ^Have,#37d67a^\n");
		assertEquals(8, c.size()); // the two {noDeck} (Upgrades) cards are not read
		MagicCardPhysical signet = find(c, "Arcane Signet");
		assertEquals(1, signet.getCount());
		assertEquals("92", signet.getCollectorId());
		assertEquals("BOK-62", find(c, "Blessing of Leeches").getCollectorId());
		assertEquals("Fell the Profane // Fell Mire", c.get(2).getName());
		assertEquals(4, find(c, "Island").getCount());
		assertEquals(10, find(c, "Swamp").getCount());
		assertEquals("107p", find(c, "Sheoldred, the Apocalypse").getCollectorId());
		assertEquals(CardFinish.FOIL, find(c, "Singularity Rupture").getRawFinish());
		assertNull(signet.getRawFinish());
		assertFalse(find(c, "Toxrill, the Corrosive").getLocation().isSideboard());
	}

	public void testSideboardMaybeboardAndEtched() throws Exception {
		List<MagicCardPhysical> c = text("2x Negate (m21) 59 [Sideboard]\n1x Shock (m21) 60 [Maybeboard]\n"
				+ "1x Sol Ring (c21) 263 *E*\n1x Brainstorm (ice) 61 [Instant,Sideboard{noPrice}]\n");
		assertEquals(3, c.size());
		assertTrue(find(c, "Negate").getLocation().isSideboard());
		assertEquals(2, find(c, "Negate").getCount());
		assertTrue(find(c, "Brainstorm").getLocation().isSideboard());
		MagicCardPhysical sol = find(c, "Sol Ring");
		assertFalse(sol.getLocation().isSideboard());
		assertEquals(CardFinish.ETCHED, sol.getRawFinish());
	}

	public void testNotAnArchidektList() throws Exception {
		for (String s : new String[] { "4 Lightning Bolt\n2 Counterspell\n", "4x Lightning Bolt\n",
				"1 Sol Ring (C21) 263\n", "Name,Count\nBolt,4\n" }) {
			try {
				text(s);
				fail("should be rejected: " + s);
			} catch (Exception expected) {
				// rejected so another importer gets a go
			}
		}
	}

	public void testZone() {
		assertEquals(ArchidektTextImportDelegate.Zone.SIDEBOARD, ArchidektTextImportDelegate.zone("Sideboard"));
		assertEquals(ArchidektTextImportDelegate.Zone.SKIP, ArchidektTextImportDelegate.zone("Upgrades{noDeck}{noPrice},Land"));
		assertEquals(ArchidektTextImportDelegate.Zone.DECK, ArchidektTextImportDelegate.zone("Commander{top}"));
	}

	public void testTextExportZonesAndMerge() throws Exception {
		Location deck = Location.createLocation("Burn");
		String t = export(new ArchidektTextExportDelegate(), card("Lightning Bolt", 2, deck),
				card("Negate", 1, deck.toSideboard()), card("Lightning Bolt", 3, deck));
		// no set on these cards: name only, the copies are added up
		assertEquals("5x Lightning Bolt\n1x Negate [Sideboard]\n", t.replace("\r", ""));
	}

	public void testTextLine() {
		assertEquals("Sol Ring", ArchidektTextExportDelegate.line("Sol Ring", null, "263", CardFinish.NONFOIL));
		Map<String, Integer> main = new LinkedHashMap<>();
		main.put("Sol Ring (c21) 263", 3);
		Map<String, Integer> side = new LinkedHashMap<>();
		side.put("Negate (m21) 59", 2);
		assertEquals("3x Sol Ring (c21) 263\n2x Negate (m21) 59 [Sideboard]\n",
				ArchidektTextExportDelegate.write(main, side));
	}

	public void testTextRoundTrip() throws Exception {
		List<MagicCardPhysical> c = text(new ArchidektTextExportDelegate().getExample());
		assertEquals(4, c.size());
		assertTrue(find(c, "Negate").getLocation().isSideboard());
		assertEquals(CardFinish.FOIL, find(c, "Lightning Bolt").getRawFinish());
	}

	public void testCollectionCsvImport() throws Exception {
		List<MagicCardPhysical> c = csv("\"Quantity\",\"Name\",\"Finish\",\"Condition\",\"Language\",\"Date Added\","
				+ "\"Edition Name\",\"Edition Code\",\"Collector Number\"\n"
				+ "\"3\",\"Sol Ring\",\"Normal\",\"NM\",\"English\",\"2026-01-01\",\"Commander 2021\",\"c21\",\"263\"\n"
				+ "\"1\",\"Lightning Bolt\",\"Foil\",\"LP\",\"English\",\"2026-01-01\",\"Magic 2011\",\"m11\",\"149\"\n"
				+ "\"1\",\"Shock\",\"Etched\",\"D\",\"English\",\"\",\"\",\"m21\",\"60\"\n");
		assertEquals(3, c.size());
		assertEquals(3, c.get(0).getCount());
		assertEquals("263", c.get(0).getCollectorId());
		assertEquals(CardCondition.NEAR_MINT, c.get(0).getCondition());
		assertEquals(CardFinish.FOIL, c.get(1).getRawFinish());
		assertEquals(CardCondition.LIGHTLY_PLAYED, c.get(1).getCondition());
		assertEquals(CardFinish.ETCHED, c.get(2).getRawFinish());
		assertEquals(CardCondition.DAMAGED, c.get(2).getCondition());
	}

	/** A real Archidekt deck exported as CSV with only Quantity and Name. */
	public void testRealDeckCsvQuantityAndName() throws Exception {
		List<MagicCardPhysical> c = csv("Quantity,Name\n1,Arcane Signet\n1,\"Drivnod, Carnage Dominus\"\n"
				+ "4,Island\n1,Fell the Profane // Fell Mire\n10,Swamp\n");
		assertEquals(5, c.size());
		assertEquals("Drivnod, Carnage Dominus", c.get(1).getName());
		assertEquals(4, find(c, "Island").getCount());
		assertEquals(10, find(c, "Swamp").getCount());
		assertEquals("Fell the Profane // Fell Mire", c.get(3).getName());
	}

	/** A real Archidekt export with many columns: lower-case "Edition code", two "Collector number" headings and Archidekt's merged "Edition dateCategory". */
	public void testRealCsvManyColumns() throws Exception {
		List<MagicCardPhysical> c = csv("Quantity,Name,Edition name,Edition code,Edition dateCategory,Secondary categories,"
				+ "Label,Finish,Collector number,Salt,Colors,Mana Value,Rarity,Scryfall ID,Types,Price,Collector number,"
				+ "Card text,Mana cost\n"
				+ "1,Manamorphose,The List,plst,2020-09-25,Draw,\"\",default,Normal,SHM-211,0.19,\"Green,Red\",2,common,"
				+ "12ed4051-31c4-4f63-b576-e3761b9d35ae,\"Instant\",4.49,not owned,\"Add two mana.\",1RG\n"
				+ "1,Sol Ring,Star Trek Commander,trc,2026-11-12,Ramp,\"\",default,Foil,499,1.59,\"\",1,uncommon,"
				+ "fc817e07-c1f3-469f-9cf7-0945dea7400f,\"Artifact\",3.49,not owned,\"{T}: Add {C}{C}.\",1\n");
		assertEquals(2, c.size());
		assertEquals("SHM-211", c.get(0).getCollectorId()); // not "not owned", the second heading
		assertEquals("499", c.get(1).getCollectorId());
		assertEquals(CardFinish.FOIL, c.get(1).getRawFinish());
		assertEquals(CardFinish.NONFOIL, c.get(0).getRawFinish());
	}

	/** If Archidekt fixes its heading (two columns), the same rows still read the same. */
	public void testSeparateDateAndCategoryHeadings() throws Exception {
		List<MagicCardPhysical> c = csv("Quantity,Name,Edition name,Edition code,Edition date,Category,Secondary categories,"
				+ "Label,Finish,Collector number\n1,Sol Ring,Star Trek Commander,trc,2026-11-12,Ramp,\"\",default,Foil,499\n");
		assertEquals(1, c.size());
		assertEquals("499", c.get(0).getCollectorId());
		assertEquals(CardFinish.FOIL, c.get(0).getRawFinish());
	}

	/** The same file is an Archidekt one for the detection, never a ManaBox one (it also has a Scryfall ID column). */
	public void testAutoDetectArchidektNotManaBox() throws Exception {
		String csv = "Quantity,Name,Edition name,Edition code,Edition dateCategory,Secondary categories,Label,Finish,"
				+ "Collector number,Salt,Colors,Mana Value,Rarity,Scryfall ID,Types\n"
				+ "1,Sol Ring,Star Trek Commander,trc,2026-11-12,Ramp,\"\",default,Foil,499,1.59,\"\",1,uncommon,"
				+ "fc817e07-c1f3-469f-9cf7-0945dea7400f,\"Artifact\"\n";
		try {
			run(new ManaBoxCsvImportDelegate(), csv);
			fail("ManaBox must not read an Archidekt file");
		} catch (Exception expected) {
			// rejected
		}
		// and a ManaBox file is not an Archidekt one
		try {
			csv("Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,Scryfall ID\nSol Ring,TRC,x,499,normal,uncommon,1,fc817e07\n");
			fail("Archidekt must not read a ManaBox file");
		} catch (Exception expected) {
			// rejected
		}
	}

	/** The Scryfall ID of an export is read back as the card id (the first key of the import). */
	public void testScryfallIdRoundTrip() throws Exception {
		List<MagicCardPhysical> c = csv(ArchidektCsvExportDelegate.HEADER
				+ "\n1,Sol Ring,Foil,NM,English,fc817e07-c1f3-469f-9cf7-0945dea7400f,Star Trek Commander,trc,499\n");
		assertEquals(1, c.size());
		assertEquals("fc817e07-c1f3-469f-9cf7-0945dea7400f", c.get(0).getCardId());
	}

	public void testNotAnArchidektCsv() throws Exception {
		for (String s : new String[] { "Name,Count,Edition\nBolt,4,M11\n", "Name,Set code,Quantity\nBolt,M11,1\n",
				"amount,card_name,set_code\n1,Bolt,m11\n" }) {
			try {
				csv(s);
				fail("should be rejected: " + s);
			} catch (Exception expected) {
				// rejected so another importer gets a go
			}
		}
	}

	public void testCollectionCsvExport() throws Exception {
		Location col = Location.createLocation("Mine");
		MagicCardPhysical a = card("Lightning Bolt", 2, col);
		a.setFinish(CardFinish.FOIL);
		a.setCondition(CardCondition.DAMAGED);
		a.getCard().setCardId("fc817e07-c1f3-469f-9cf7-0945dea7400f");
		String t = export(new ArchidektCsvExportDelegate(), a).replace("\r", "");
		String[] lines = t.split("\n");
		assertEquals(ArchidektCsvExportDelegate.HEADER, lines[0]);
		assertTrue(lines[1], lines[1].startsWith("2,Lightning Bolt,Foil,D,English,fc817e07-c1f3-469f-9cf7-0945dea7400f,"));
	}

	public void testFinishAndConditionValues() {
		assertEquals("Normal", ArchidektCsvExportDelegate.finish(CardFinish.NONFOIL));
		assertEquals("Etched", ArchidektCsvExportDelegate.finish(CardFinish.ETCHED));
		assertEquals("NM", ArchidektCsvExportDelegate.condition(null));
		assertEquals("LP", ArchidektCsvExportDelegate.condition(CardCondition.LIGHTLY_PLAYED));
		assertEquals("D", ArchidektCsvExportDelegate.condition(CardCondition.DAMAGED));
		assertEquals(CardCondition.DAMAGED, ArchidektCsvImportDelegate.condition("D"));
		assertEquals(CardCondition.HEAVILY_PLAYED, ArchidektCsvImportDelegate.condition("HP"));
		// the labels of Archidekt's own menu
		assertEquals(CardCondition.NEAR_MINT, ArchidektCsvImportDelegate.condition("Near Mint (NM)"));
		assertEquals(CardCondition.MODERATELY_PLAYED, ArchidektCsvImportDelegate.condition("Moderately Played (MP)"));
		assertEquals(CardCondition.DAMAGED, ArchidektCsvImportDelegate.condition("Damaged (D)"));
	}
}
