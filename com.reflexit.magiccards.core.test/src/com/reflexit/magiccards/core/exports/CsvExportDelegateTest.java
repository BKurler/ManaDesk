/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.exports;

import org.junit.Test;

import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.unittesting.CardGenerator;

public class CsvExportDelegateTest extends AbstarctExportTest {
	private CsvExportDelegate exporter = new CsvExportDelegate();

	@Override
	public void setUp() throws Exception {
		super.setUp();
		exporter.setReportType(ImportExportFactory.createReportType("test"));
	}

	@Test
	public void test1() {
		run(exporter);
		assertEquals(4, lines.length);
		assertTrue(lines[0].startsWith("ID,NAME"));
	}

	@Test
	public void test2() {
		exporter.setColumns(new ICardField[] { MagicCardField.COUNT, MagicCardField.NAME });
		run(exporter);
		assertEquals(4, lines.length);
		assertTrue("Not good " + lines[0], lines[0].equals("COUNT,NAME"));
		assertTrue("Not good " + lines[0], lines[1].equals(card1.getCount() + "," + card1.getName()));
	}

	@Test
	public void testEscape() {
		exporter.setColumns(new ICardField[] { MagicCardField.COUNT, MagicCardField.NAME });
		card1.set(MagicCardField.NAME, "My,Name");
		run(exporter);
		assertTrue("Does not match " + lines[1], lines[1].startsWith(card1.getCount() + "," + "\"My,Name\""));
	}

	@Test
	public void testGenericCsvMultiDeckLeavesColumnsAlone() {
		// the full CSV already carries LOCATION in its natural column set, so
		// multi-deck must NOT prepend a second one / reorder the header
		exporter.setColumns(new ICardField[] { MagicCardField.COUNT, MagicCardField.NAME });
		makeDeck();
		exporter.init(out, true, deck);
		exporter.setMultiDeck(true);
		try {
			exporter.run(null);
		} catch (Exception e) {
			fail(e.getMessage());
		}
		splitLines();
		assertTrue("header must be untouched: " + lines[0], lines[0].equals("COUNT,NAME"));
	}

	@Test
	public void testMinimumCsvMultiDeckAddsLocationColumn() {
		MinimumCsvExportDelegate min = new MinimumCsvExportDelegate();
		min.setReportType(ImportExportFactory.createReportType("test"));
		makeDeck();
		min.init(out, true, deck);
		min.setMultiDeck(true);
		try {
			min.run(null);
		} catch (Exception e) {
			fail(e.getMessage());
		}
		splitLines();
		assertTrue("Minimum CSV header should lead with LOCATION: " + lines[0],
				lines[0].equals("LOCATION,NAME,SET,COUNT,SPECIAL,COMMENT,LANG,COLLNUM,GATHERERID,ID,OWNERSHIP"));
		String[] cells = lines[1].split(",", -1);
		assertTrue("data row should have 11 cells, got " + cells.length + ": " + lines[1], cells.length == 11);
	}

	@Test
	public void testMinimumCsvSingleDeckHasNoLocationColumn() {
		MinimumCsvExportDelegate min = new MinimumCsvExportDelegate();
		min.setReportType(ImportExportFactory.createReportType("test"));
		makeDeck();
		min.init(out, true, deck);
		// multiDeck defaults false after init
		try {
			min.run(null);
		} catch (Exception e) {
			fail(e.getMessage());
		}
		splitLines();
		assertTrue("single-deck Minimum CSV header unchanged: " + lines[0],
				lines[0].equals("NAME,SET,COUNT,SPECIAL,COMMENT,LANG,COLLNUM,GATHERERID,ID,OWNERSHIP"));
	}

	@Test
	public void testMc() {
		card3 = CardGenerator.generateCardWithValues();
		card1 = card2 = null;
		run(exporter);
		assertEquals(2, lines.length);
		assertTrue(lines[0].startsWith("ID,NAME"));
		assertTrue("Does not match " + lines[1],
				lines[1].startsWith(card3.getCardId() + "," + card3.getName()));
	}
}
