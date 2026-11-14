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

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.test.assist.MemCardHandler;

import junit.framework.TestCase;

/** Cockatrice deck (.cod) import / export (pure logic: plain JUnit, no Eclipse runtime needed). */
public class CockatriceTest extends TestCase {
	private static final String DECK = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" //
			+ "<cockatrice_deck version=\"1\">\n" //
			+ "  <deckname>Burn &amp; more</deckname>\n" //
			+ "  <comments>ignored</comments>\n" //
			+ "  <zone name=\"main\">\n" //
			+ "    <card number=\"4\" name=\"Lightning Bolt\"/>\n" //
			+ "    <card number=\"1\" name=\"Fire // Ice\"/>\n" //
			+ "    <card number=\"x\" name=\"Shock\"/>\n" // unreadable count -> 1
			+ "    <card number=\"2\" name=\"\"/>\n" // no name -> skipped
			+ "  </zone>\n" //
			+ "  <zone name=\"side\">\n" //
			+ "    <card number=\"2\" name=\"Negate\"/>\n" //
			+ "  </zone>\n" //
			+ "  <zone name=\"tokens\">\n" //
			+ "    <card number=\"1\" name=\"Goblin\"/>\n" //
			+ "  </zone>\n" //
			+ "</cockatrice_deck>\n";

	private ImportData data;

	private List<MagicCardPhysical> parse(String text) throws Exception {
		CockatriceImportDelegate importer = new CockatriceImportDelegate();
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

	public void testImportZones() throws Exception {
		List<MagicCardPhysical> c = parse(DECK);
		assertEquals(4, c.size()); // tokens zone and the nameless card are not read
		assertEquals("Lightning Bolt", c.get(0).getName());
		assertEquals(4, c.get(0).getCount());
		assertEquals("Fire // Ice", c.get(1).getName());
		assertEquals(1, c.get(2).getCount());
		assertFalse(c.get(0).getLocation().isSideboard());
		assertEquals("Negate", c.get(3).getName());
		assertEquals(2, c.get(3).getCount());
		assertTrue(c.get(3).getLocation().isSideboard());
	}

	public void testNotACockatriceFile() throws Exception {
		for (String text : new String[] { "4 Lightning Bolt\n", "<deck><zone name=\"main\"/></deck>",
				"<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><cockatrice_deck/>" }) {
			try {
				parse(text);
				fail("should be rejected: " + text);
			} catch (Exception expected) {
				// rejected, so another importer gets a go
			}
		}
	}

	public void testEmptyDeckHasNoCards() throws Exception {
		assertEquals(0, parse("<cockatrice_deck version=\"1\"><zone name=\"main\"/></cockatrice_deck>").size());
	}

	private static MagicCardPhysical card(String name, int count, Location loc) {
		MagicCard base = new MagicCard();
		base.setName(name);
		MagicCardPhysical c = new MagicCardPhysical(base, loc);
		c.setCount(count);
		return c;
	}

	private String export(MagicCardPhysical... cards) throws Exception {
		MemCardHandler store = new MemCardHandler();
		for (MagicCardPhysical c : cards)
			store.getCardStore().add(c);
		store.update();
		CockatriceExportDelegate exporter = new CockatriceExportDelegate();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		exporter.setReportType(ImportExportFactory.createReportType("cockatrice-test"));
		exporter.init(out, true, store);
		exporter.run(ICoreProgressMonitor.NONE);
		return out.toString("UTF-8");
	}

	public void testExportZonesAndMerge() throws Exception {
		Location deck = Location.createLocation("Burn");
		String text = export(card("Lightning Bolt", 2, deck), card("Negate", 1, deck.toSideboard()),
				card("Lightning Bolt", 3, deck), // another printing: added up
				card("Treasure", 1, deck.toExtra()), // extra list: left out
				card("Ice \"&\" <Fire>", 1, deck));
		assertTrue(text, text.contains("<cockatrice_deck version=\"1\">"));
		assertTrue(text, text.contains("<card number=\"5\" name=\"Lightning Bolt\"/>"));
		assertTrue(text, text.contains("name=\"Ice &quot;&amp;&quot; &lt;Fire&gt;\""));
		assertFalse(text, text.contains("Treasure"));
		int side = text.indexOf("<zone name=\"side\">");
		assertTrue(side > text.indexOf("<zone name=\"main\">"));
		assertTrue(text.indexOf("Negate") > side);
		assertTrue(text.indexOf("Lightning Bolt") < side);
	}

	public void testExportRoundTrip() throws Exception {
		Location deck = Location.createLocation("Burn");
		String text = export(card("Lightning Bolt", 4, deck), card("Fire // Ice (split)", 1, deck),
				card("Negate", 2, deck.toSideboard()));
		List<MagicCardPhysical> c = parse(text);
		assertEquals(3, c.size());
		assertEquals("Lightning Bolt", c.get(0).getName());
		assertEquals(4, c.get(0).getCount());
		assertEquals("Fire // Ice", c.get(1).getName()); // layout note dropped
		assertEquals("Negate", c.get(2).getName());
		assertTrue(c.get(2).getLocation().isSideboard());
	}

	public void testExportExampleIsImportable() throws Exception {
		String example = new CockatriceExportDelegate().getExample();
		assertEquals(3, parse(example).size());
		assertEquals(3, parse(new CockatriceImportDelegate().getExample()).size());
	}

	public void testRegisteredAndDetectedByExtension() throws Exception {
		ReportType t = ImportExportFactory.getByLabel("Cockatrice Deck");
		if (t == null)
			return; // plugin.xml registry is only there in a plug-in run: nothing to check in plain JUnit
		assertEquals("cod", t.getExtension());
		assertTrue(t.isApplication());
		assertNotNull(t.getImportDelegate());
		assertNotNull(t.getExportDelegate());
	}
}
