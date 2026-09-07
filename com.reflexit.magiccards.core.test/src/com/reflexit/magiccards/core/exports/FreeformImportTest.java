/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk
 */
package com.reflexit.magiccards.core.exports;

import org.junit.Test;

import com.reflexit.magiccards.core.model.MagicCardPhysical;

public class FreeformImportTest extends AbstarctImportTest {
	private final FreeformImportDelegate importd = new FreeformImportDelegate();

	// the output of a custom "%d %s" ManaDesk exporter with the ID column
	@Test
	public void testCountNameJunkAndId() {
		resolve = false;
		line = "# deck1\n"
				+ "1 Wall of Bone Creature - Skeleton Wall 1 4 f19d80ce-284a-4d22-9ea8-f8d480377212\n"
				+ "3 Reliquary Tower Land   b1e3f701-4783-42ef-bfe5-20606ada49f7\n"
				+ "Sideboard\n"
				+ "1 Plains Basic Land - Plains   18a72e75-762b-4760-937a-05031cdec732\n";
		preview(importd);
		assertEquals(null, exception);
		assertEquals(3, resSize);
		// id extracted, junk ignored
		assertEquals("f19d80ce-284a-4d22-9ea8-f8d480377212", card1.getCardId());
		assertEquals(1, ((MagicCardPhysical) card1).getCount());
		assertEquals("b1e3f701-4783-42ef-bfe5-20606ada49f7", card2.getCardId());
		assertEquals(3, ((MagicCardPhysical) card2).getCount());
		// after "Sideboard" the location is the sideboard
		String loc = String.valueOf(((MagicCardPhysical) card3).getLocation());
		assertTrue("expected a sideboard location, got " + loc,
				loc.endsWith("-sideboard") || loc.equals("sideboard"));
	}

	@Test
	public void testClassicShapesWithoutId() {
		resolve = false;
		line = "4 Lightning Bolt\n"
				+ "2 Counterspell (Ice Age)\n"
				+ "Reliquary Tower x 1\n";
		preview(importd);
		assertEquals(null, exception);
		assertEquals(3, resSize);
		assertEquals("Lightning Bolt", card1.getName());
		assertEquals(4, ((MagicCardPhysical) card1).getCount());
		assertEquals("Counterspell", card2.getName());
		assertEquals("Ice Age", card2.getSet());
		assertEquals("Reliquary Tower", card3.getName());
		assertEquals(1, ((MagicCardPhysical) card3).getCount());
	}
}
