/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk
 */
package com.reflexit.magiccards.core.exports;

import org.junit.Test;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;

public class MagicAssistantCsvImportTest extends AbstarctImportTest {
	private final MagicAssistantCsvImportDelegate importd = new MagicAssistantCsvImportDelegate();

	// a Magic Assistant collection export is often just the Scryfall id column
	@Test
	public void testIdOnly() {
		resolve = false;
		line = "id\nce711943-c1a1-43a0-8b89-8d169cfb8e11\n0c4eaecf-dd4c-45ab-9b50-2abe987d35d4\n";
		preview(importd);
		assertEquals(null, exception);
		assertEquals(2, resSize);
		assertEquals("ce711943-c1a1-43a0-8b89-8d169cfb8e11", card1.getCardId());
		assertEquals("0c4eaecf-dd4c-45ab-9b50-2abe987d35d4", card2.getCardId());
	}

	@Test
	public void testScryPrefixAndGathererId() {
		resolve = false;
		line = "id\nscry_ce711943-c1a1-43a0-8b89-8d169cfb8e11\n209\n";
		preview(importd);
		assertEquals(null, exception);
		assertEquals(2, resSize);
		// scry_ prefix stripped -> Scryfall card id
		assertEquals("ce711943-c1a1-43a0-8b89-8d169cfb8e11", card1.getCardId());
		// plain number -> Gatherer id, not a card id
		assertEquals(null, card2.getCardId());
		assertEquals("209", String.valueOf(((MagicCardPhysical) card2).get(MagicCardField.GATHERERID)));
	}

	@Test
	public void testExampleParsesBack() {
		resolve = false;
		line = importd.getExample();
		preview(importd);
		assertEquals(null, exception);
		assertEquals(2, resSize);
	}

	// --- must not clash with ManaDesk CSV --------------------------------

	/** An all-caps ManaDesk-shaped header is left to the ManaDesk importer. */
	@Test(expected = MagicException.class)
	public void rejectsManaDeskShapedHeader() throws Exception {
		resolve = true;
		addLine("NAME,SET,COUNT,ID");
		addLine("Llanowar Elves,Dominaria,4,ce711943-c1a1-43a0-8b89-8d169cfb8e11");
		parseonly(importd);
	}

	@Test(expected = MagicException.class)
	public void rejectsSetSlashEditionAbbrHeader() throws Exception {
		resolve = true;
		addLine("NAME,SET/EDITION_ABBR,COUNT");
		addLine("Llanowar Elves,Dominaria,4");
		parseonly(importd);
	}

	/** Mixed-case / spaced headers with a lower-case id are Magic Assistant. */
	@Test
	public void acceptsMixedCaseHeaderWithLowercaseId() {
		resolve = false;
		line = "id,Name,Set,Card Number,Count\nce711943-c1a1-43a0-8b89-8d169cfb8e11,Lightning Bolt,Alpha,161,3\n";
		preview(importd);
		assertEquals(null, exception);
		assertEquals(1, resSize);
		assertEquals("ce711943-c1a1-43a0-8b89-8d169cfb8e11", card1.getCardId());
	}
}
