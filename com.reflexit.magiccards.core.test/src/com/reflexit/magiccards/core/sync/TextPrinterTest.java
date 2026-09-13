

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - removed the now-uncompilable card.setRating() call
 *                         (community rating is not a concept this app tracks
 *                         anymore)
 */

package com.reflexit.magiccards.core.sync;

import org.junit.Test;

import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.test.assist.AbstractMagicTest;
import com.reflexit.unittesting.CardGenerator;

public class TextPrinterTest extends AbstractMagicTest {
	@Test
	public void testMCHeader() {
		String string = TextPrinter.getHeader();
		assertEquals(
				"ID|NAME|COST|TYPE|POWER|TOUGHNESS|ORACLE|SET|RARITY|LANG|ARTIST|COLLNUM|TEXT|GATHERERID|TCGID|PROPERTIES",
				string);
	}

	@Test
	public void testMC() {
		MagicCard card = CardGenerator.genMagicCard(39);
		String string = TextPrinter.getString(card);
		assertEquals("-39|name 39|{4}|type 39|4|*|bla 39|set19|Common|Russian|Elena 39|39a|bla <br> bla 39|0|0|",
				string);
	}

	@Test
	public void testMCImageURL() {
		MagicCard card = CardGenerator.genMagicCard(39);
		card.set(MagicCardField.IMAGE_URL, "some");
		String string = TextPrinter.getString(card);
		assertEquals(
				"-39|name 39|{4}|type 39|4|*|bla 39|set19|Common|Russian|Elena 39|39a|bla <br> bla 39|0|0|{IMAGE_URL=some}",
				string);
	}
}
