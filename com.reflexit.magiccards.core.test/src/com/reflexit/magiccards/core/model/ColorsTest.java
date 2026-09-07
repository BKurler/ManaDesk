

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import org.junit.Test;

import com.reflexit.magiccards.core.model.Colors.ManaColor;
import com.reflexit.unittesting.CardGenerator;

import static org.junit.Assert.*;

public class ColorsTest {
	public MagicCardPhysical mcp() {
		return CardGenerator.generatePhysicalCardWithValues();
	}

	public MagicCardPhysical mcpCost(ManaColor... colors) {
		MagicCardPhysical b = mcp();
		b.getCard().setCost(Colors.getInstance().toCost(colors));
		return b;
	}

	public MagicCardPhysical mcpCost(String cost) {
		MagicCardPhysical b = mcp();
		b.getCard().setCost(cost);
		return b;
	}

	private void checkIdentity(IMagicCard card, String... args) {
		Collection<String> colorIdentity = Colors.getInstance().getColorIdentity(card);
		assertTrue(colorIdentity.size() > 0);
		assertEquals(set(args), colorIdentity);
	}

	private HashSet<String> set(String... args) {
		return new HashSet<>(Arrays.asList(args));
	}

	@Test
	public void testColorBlackAndRedIdentity() {
		MagicCardPhysical b = mcpCost(ManaColor.BLACK);
		MagicCardPhysical r = mcpCost(ManaColor.RED);
		MagicCardPhysical w = mcpCost(ManaColor.WHITE);
		MagicCardPhysical wb = mcpCost(ManaColor.WHITE, ManaColor.BLACK);
		MagicCardPhysical br = mcpCost(ManaColor.BLACK, ManaColor.RED);
		MagicCardPhysical brh = mcpCost("{B/R}");
		MagicCardPhysical bc = mcpCost(ManaColor.BLACK, ManaColor.COLORLESS);		
		checkIdentity(b, "B");
		checkIdentity(r, "R");
		checkIdentity(w, "W");
		checkIdentity(wb, "W", "B");
		checkIdentity(br, "R", "B");
		checkIdentity(brh, "R", "B");
		checkIdentity(bc, "B", "C");		
		br.set(MagicCardField.ORACLE, "{W} - do something"); // white in text
		checkIdentity(br, "B", "R", "W");
		br.set(MagicCardField.ORACLE, "{R} - do something"); // red
		checkIdentity(br, "B", "R");
		br.set(MagicCardField.ORACLE, "{W/R} - do something"); // combined cost
		checkIdentity(br, "B", "R", "W");
		br.set(MagicCardField.ORACLE, "Win"); // W but not cost
		checkIdentity(br, "B", "R");
		br.set(MagicCardField.ORACLE, "something {WP} - do something"); // W or paylife
		checkIdentity(br, "B", "R", "W");
		br.set(MagicCardField.ORACLE, "{2/R} - do something"); // Colorless or Red
		checkIdentity(br, "B", "R", "C");
		brh.set(MagicCardField.ORACLE, "{2} - do something"); // Colorless 
		checkIdentity(br, "B", "R", "C");
	}

	@Test
	public void testNoIdentity() {
		MagicCardPhysical a = mcpCost("");
		Collection<String> colorIdentity = Colors.getInstance().getColorIdentity(a);
		assertTrue(colorIdentity.size() == 0);
	}

	@Test
	public void testColorType() {
		assertEquals("costless", Colors.getColorType(""));
		assertEquals("colorless", Colors.getColorType("{10}"));
		assertEquals("multi-hybrid", Colors.getColorType("{B/R}"));
		assertEquals("mono", Colors.getColorType("{B}"));
		assertEquals("mono", Colors.getColorType("{B}{1}"));
		assertEquals("mono", Colors.getColorType("{B}{X}"));
		assertEquals("mono", Colors.getColorType("{B}{B}{X}"));
		assertEquals("multi", Colors.getColorType("{B}{R}"));
		assertEquals("mono-hybrid", Colors.getColorType("{RP}"));
		assertEquals("mono-hybrid", Colors.getColorType("{2/R}"));		
		assertEquals("mono-hybrid", Colors.getColorType("{2}{2/R}"));
		assertEquals("multi-hybrid", Colors.getColorType("{2/W}{2/R}"));
		assertEquals("multi", Colors.getColorType("{W}{U}{B}{R}{G}{1}"));
	}

	@Test
	public void testCCC() {
		Colors cs = Colors.getInstance();
		assertEquals(0, cs.getConvertedManaCost(""));
		assertEquals(10, cs.getConvertedManaCost("{10}"));
		assertEquals(1, cs.getConvertedManaCost("{B/R}"));
		assertEquals(1, cs.getConvertedManaCost("{B}"));
		assertEquals(2, cs.getConvertedManaCost("{B}{1}"));
		assertEquals(1, cs.getConvertedManaCost("{B}{X}"));
		assertEquals(2, cs.getConvertedManaCost("{B}{B}{X}"));
		assertEquals(2, cs.getConvertedManaCost("{B}{R}"));
		assertEquals(1, cs.getConvertedManaCost("{RP}"));
		assertEquals(2, cs.getConvertedManaCost("{2/R}"));
		assertEquals(4, cs.getConvertedManaCost("{2}{2/R}"));
		assertEquals(4, cs.getConvertedManaCost("{2/W}{2/R}"));
		assertEquals(6, cs.getConvertedManaCost("{W}{U}{B}{R}{G}{1}"));		
	}

	@Test
	public void testColorName() {
		Colors cs = Colors.getInstance();
		assertEquals("Costless", cs.getColorName(""));
		assertEquals("Colorless", cs.getColorName("{10}"));
		assertEquals("Black-Red", cs.getColorName("{B/R}"));
		assertEquals("Black", cs.getColorName("{B}"));
		assertEquals("Black-Colorless", cs.getColorName("{B}{1}"));
		assertEquals("Black-Colorless", cs.getColorName("{B}{X}"));
		assertEquals("Black-Colorless", cs.getColorName("{B}{B}{X}"));
		assertEquals("Black-Red", cs.getColorName("{B}{R}"));
		assertEquals("Red", cs.getColorName("{RP}"));
		assertEquals("Red-Colorless", cs.getColorName("{2/R}"));
		assertEquals("Red-Colorless", cs.getColorName("{2}{2/R}"));
		assertEquals("White-Red-Colorless", cs.getColorName("{2/W}{2/R}"));
		assertEquals("White-Blue-Black", cs.getColorName("{W}{B}{U}"));
		assertEquals("White-Blue-Black", cs.getColorName("{W}{U}{B}"));
		assertEquals("White-Blue-Black", cs.getColorName("{W/B}{U}"));
		assertEquals("White-Blue-Black-Colorless", cs.getColorName("{W/B}{U}{1}"));
		assertEquals("White-Blue-Black-Red-Green-Colorless", cs.getColorName("{1}{G}{R}{B}{U}{W}"));
	}

	@Test
	public void testMonoColorListedBeforeMultiColor() {
		java.util.List<String> names = new java.util.ArrayList<>();
		for (String id : ColorTypes.getInstance().getIds())
			names.add(ColorTypes.getInstance().getNameById(id));
		int mono = names.indexOf("Mono-Color");
		int multi = names.indexOf("Multi-Color");
		assertTrue("both present: " + names, mono >= 0 && multi >= 0);
		assertTrue("Mono-Color must come before Multi-Color in the filter dialog: " + names, mono < multi);
	}
}
