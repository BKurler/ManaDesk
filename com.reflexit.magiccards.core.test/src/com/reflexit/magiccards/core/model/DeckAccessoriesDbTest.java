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
package com.reflexit.magiccards.core.model;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.reflexit.magiccards.core.model.DeckAccessories.Kind;
import com.reflexit.magiccards.core.model.DeckAccessories.Need;
import com.reflexit.magiccards.core.model.DeckAccessories.Result;
import com.reflexit.magiccards.core.model.storage.IDbCardStore;

/**
 * {@link DeckAccessories} with a card database (a stub): resolving token ids, collapsing the
 * same token from several printings, emblems, helper cards, picking the English printing,
 * the cards stocked in the deck's extra list (owned / useless), the order of the result, and
 * the identity text. {@link DeckAccessoriesTest} covers the database-free paths. Pure logic;
 * works as plain JUnit.
 */
public class DeckAccessoriesDbTest {

	@SuppressWarnings("unchecked")
	private final IDbCardStore<IMagicCard> db = mock(IDbCardStore.class);

	private static MagicCard token(String name, String type, String power, String toughness, String set) {
		MagicCard c = new MagicCard();
		c.setName(name);
		c.setType(type);
		c.setPower(power);
		c.setToughness(toughness);
		c.setOracleText("");
		c.setSet(set);
		return c;
	}

	private static MagicCardPhysical deckCard(String name, int count, String oracle, String accessories, String set) {
		MagicCard base = new MagicCard();
		base.setName(name);
		base.setType("Creature");
		base.setOracleText(oracle);
		base.setSet(set);
		if (accessories != null)
			base.set(MagicCardField.ACCESSORIES, accessories);
		MagicCardPhysical p = new MagicCardPhysical(base, null);
		p.setCount(count);
		return p;
	}

	private static MagicCardPhysical stocked(MagicCard base, int count) {
		MagicCardPhysical p = new MagicCardPhysical(base, null);
		p.setCount(count);
		return p;
	}

	private static List<String> labels(List<Need> needs) {
		List<String> l = new ArrayList<String>();
		for (Need n : needs)
			l.add(n.label);
		return l;
	}

	// ---- tokens and emblems

	@Test
	public void aTokenIdIsResolvedAgainstTheDatabase() {
		MagicCard soldier = token("Soldier", "Token Creature - Soldier", "1", "1", "Some Tokens");
		when(db.getCard("id-soldier")).thenReturn(soldier);
		Result r = DeckAccessories.compute(Arrays.asList(deckCard("Call", 2, "", "tid-soldier", "Some Set")), db);
		Assert.assertEquals(1, r.tokens.size());
		Need n = r.tokens.get(0);
		Assert.assertEquals(Kind.TOKEN, n.kind);
		Assert.assertEquals("Soldier", n.label);
		Assert.assertSame(soldier, n.card);
		Assert.assertEquals(2, n.copies);
		Assert.assertTrue(r.incomplete.isEmpty());
	}

	@Test
	public void theSameTokenFromTwoPrintingsIsOneEntry() {
		when(db.getCard("id-a")).thenReturn(token("Soldier", "Token Creature - Soldier", "1", "1", "Tokens One"));
		when(db.getCard("id-b")).thenReturn(token("Soldier", "Token Creature - Soldier", "1", "1", "Tokens Two"));
		List<IMagicCard> deck = new ArrayList<IMagicCard>();
		deck.add(deckCard("Call A", 3, "", "tid-a", "Set A"));
		deck.add(deckCard("Call B", 1, "", "tid-b", "Set B"));
		Result r = DeckAccessories.compute(deck, db);
		Assert.assertEquals(1, r.tokens.size());
		Assert.assertEquals(2, r.tokens.get(0).getDeckCards());
		Assert.assertEquals(4, r.tokens.get(0).copies);
	}

	@Test
	public void twoDifferentTokensStaySeparateAndAreListedAlphabetically() {
		when(db.getCard("id-beast")).thenReturn(token("beast", "Token Creature - Beast", "3", "3", "T"));
		when(db.getCard("id-angel")).thenReturn(token("Angel", "Token Creature - Angel", "4", "4", "T"));
		when(db.getCard("id-angel2")).thenReturn(token("Angel", "Token Creature - Angel", "1", "1", "T"));
		List<IMagicCard> deck = new ArrayList<IMagicCard>();
		deck.add(deckCard("A", 1, "", "tid-beast;tid-angel;tid-angel2", "S"));
		Result r = DeckAccessories.compute(deck, db);
		Assert.assertEquals(3, r.tokens.size());
		// by name ignoring case: the two Angels, then the beast
		Assert.assertEquals("Angel", r.tokens.get(0).label);
		Assert.assertEquals("Angel", r.tokens.get(1).label);
		Assert.assertEquals("beast", r.tokens.get(2).label);
	}

	@Test
	public void anEmblemIsListedAsAnEmblem() {
		when(db.getCard("id-emb")).thenReturn(token("Elspeth Emblem", "Emblem", null, null, "T"));
		Result r = DeckAccessories.compute(Arrays.asList(deckCard("Elspeth", 1, "", "tid-emb", "S")), db);
		Assert.assertTrue(r.tokens.isEmpty());
		Assert.assertEquals(1, r.emblems.size());
		Assert.assertEquals(Kind.EMBLEM, r.emblems.get(0).kind);
		Assert.assertEquals("Elspeth Emblem", r.emblems.get(0).label);
	}

	@Test
	public void aTokenMissingFromTheDatabaseIsABadgeAndTheDeckCardIsFlagged() {
		when(db.getCard("id-gone")).thenReturn(null);
		Result r = DeckAccessories.compute(Arrays.asList(deckCard("Mystery", 1, "", "tid-gone", "S")), db);
		Assert.assertEquals(1, r.tokens.size());
		Assert.assertEquals("Token", r.tokens.get(0).label);
		Assert.assertNull(r.tokens.get(0).card);
		Assert.assertTrue(r.incomplete.contains("Mystery"));
	}

	@Test
	public void aNonEnglishTokenIsReplacedByItsEnglishPrinting() {
		MagicCard japanese = token("Soldier", "Token Creature - Soldier", "1", "1", "Jp Tokens");
		japanese.setLanguage("Japanese");
		MagicCard english = token("Soldier", "Token Creature - Soldier", "1", "1", "En Tokens");
		when(db.getCard("id-jp")).thenReturn(japanese);
		when(db.getCandidates("Soldier")).thenReturn(Arrays.<IMagicCard> asList(japanese, english));
		Result r = DeckAccessories.compute(Arrays.asList(deckCard("Call", 1, "", "tid-jp", "Other Set")), db);
		Assert.assertSame(english, r.tokens.get(0).card);
	}

	@Test
	public void theTokenOfTheCardsOwnSetIsPreferred() {
		MagicCard generic = token("Soldier", "Token Creature - Soldier", "1", "1", "Generic Tokens");
		MagicCard fromSet = token("Soldier", "Token Creature - Soldier", "1", "1", "Dominaria Tokens");
		when(db.getCard("id-gen")).thenReturn(generic);
		when(db.getCandidates("Soldier")).thenReturn(Arrays.<IMagicCard> asList(generic, fromSet));
		Result r = DeckAccessories.compute(Arrays.asList(deckCard("Call", 1, "", "tid-gen", "Dominaria")), db);
		Assert.assertSame("the printing from the token set of Dominaria", fromSet, r.tokens.get(0).card);
	}

	// ---- helper cards

	@Test
	public void aMarkerShowsTheEnglishHelperCard() {
		MagicCard japanese = new MagicCard();
		japanese.setName("The Monarch");
		japanese.setType("Card");
		japanese.setLanguage("Japanese");
		MagicCard english = new MagicCard();
		english.setName("The Monarch");
		english.setType("Card");
		when(db.getCandidates("The Monarch")).thenReturn(Arrays.<IMagicCard> asList(japanese, english));
		Result r = DeckAccessories.compute(Arrays.asList(deckCard("Queen", 1, "You become the monarch.", null, "S")), db);
		Assert.assertEquals(1, r.playerMarkers.size());
		Need n = r.playerMarkers.get(0);
		Assert.assertEquals("The Monarch", n.label);
		Assert.assertSame(english, n.card);
	}

	@Test
	public void aMarkerWithOnlyForeignHelperCardsIsABadge() {
		MagicCard japanese = new MagicCard();
		japanese.setName("The Initiative");
		japanese.setLanguage("Japanese");
		when(db.getCandidates("The Initiative")).thenReturn(Collections.<IMagicCard> singletonList(japanese));
		when(db.getPrime("The Initiative")).thenReturn(japanese);
		Result r = DeckAccessories.compute(Arrays.asList(deckCard("Hero", 1, "You take the initiative.", null, "S")), db);
		Assert.assertEquals("The Initiative", r.playerMarkers.get(0).label);
		Assert.assertNull(r.playerMarkers.get(0).card);
	}

	@Test
	public void markerAndDiceLabels() {
		Result r = DeckAccessories.compute(Arrays.asList(deckCard("Dice", 1,
				"Roll a d20. Flip a coin. The Ring tempts you. It becomes night. You get the city's blessing.", null, "S")), null);
		Assert.assertEquals(Arrays.asList("City's Blessing", "Day / Night", "The Ring"), labels(r.playerMarkers));
		Assert.assertEquals(Arrays.asList("Coin flip", "d20"), labels(r.dice));
	}

	@Test
	public void aCounterShowsItsHelperCardWhenTheDatabaseHasOne() {
		MagicCard energy = new MagicCard();
		energy.setName("Energy Reserve");
		energy.setType("Card");
		when(db.getCandidates("Energy Reserve")).thenReturn(Collections.<IMagicCard> singletonList(energy));
		Result r = DeckAccessories.compute(Arrays.asList(deckCard("Hub", 4, "When this enters, you get {E}{E}.", null, "S")), db);
		Assert.assertEquals("Energy", r.playerMarkers.get(0).label);
		Assert.assertSame(energy, r.playerMarkers.get(0).card);
	}

	// ---- the deck's extra list

	@Test
	public void anOwnedTokenIsShownWithHowManyAreStocked() {
		MagicCard needed = token("Soldier", "Token Creature - Soldier", "1", "1", "Generic Tokens");
		MagicCard mine = token("Soldier", "Token Creature - Soldier", "1", "1", "My Tokens");
		when(db.getCard("id-s")).thenReturn(needed);
		List<IMagicCard> extra = new ArrayList<IMagicCard>();
		extra.add(stocked(mine, 3));
		extra.add(stocked(mine, 2));
		Result r = DeckAccessories.compute(Arrays.asList(deckCard("Call", 1, "", "tid-s", "S")), extra, db);
		Need n = r.tokens.get(0);
		Assert.assertTrue(n.ownedByUser);
		Assert.assertSame("the printing the user stocked", mine, n.card);
		Assert.assertEquals(5, n.haveCount);
		Assert.assertTrue(r.useless.isEmpty());
	}

	@Test
	public void aStockedCardNothingNeedsIsUseless() {
		MagicCard needed = token("Soldier", "Token Creature - Soldier", "1", "1", "T");
		MagicCard unrelated = token("Goblin", "Token Creature - Goblin", "1", "1", "T");
		when(db.getCard("id-s")).thenReturn(needed);
		List<IMagicCard> extra = new ArrayList<IMagicCard>();
		extra.add(stocked(unrelated, 2));
		extra.add(null);
		extra.add(stocked(token("Grizzly Bears", "Creature - Bear", "2", "2", "S"), 4)); // not an accessory: ignored
		Result r = DeckAccessories.compute(Arrays.asList(deckCard("Call", 1, "", "tid-s", "S")), extra, db);
		Assert.assertEquals(1, r.useless.size());
		Need u = r.useless.get(0);
		Assert.assertEquals(Kind.USELESS, u.kind);
		Assert.assertEquals("Goblin", u.label);
		Assert.assertEquals(2, u.haveCount);
		Assert.assertTrue(u.sources.isEmpty());
		Assert.assertFalse(r.tokens.get(0).ownedByUser);
	}

	// ---- the result

	@Test
	public void theResultListsUselessCardsFirstThenEachKindAndFindsEntriesByKey() {
		MagicCard soldier = token("Soldier", "Token Creature - Soldier", "1", "1", "T");
		MagicCard emblem = token("Emblem", "Emblem", null, null, "T");
		MagicCard goblin = token("Goblin", "Token Creature - Goblin", "1", "1", "T");
		when(db.getCard("id-s")).thenReturn(soldier);
		when(db.getCard("id-e")).thenReturn(emblem);
		List<IMagicCard> deck = new ArrayList<IMagicCard>();
		deck.add(deckCard("Everything", 1,
				"Put a +1/+1 counter. Put a stun counter. You become the monarch. Roll a d6.", "tid-s;tid-e", "S"));
		Result r = DeckAccessories.compute(deck, Arrays.<IMagicCard> asList(stocked(goblin, 1)), db);
		List<Need> all = r.all();
		Assert.assertEquals(Kind.USELESS, all.get(0).kind);
		Assert.assertEquals(Kind.TOKEN, all.get(1).kind);
		Assert.assertEquals(Kind.EMBLEM, all.get(2).kind);
		Assert.assertEquals(Kind.PLAYER_MARKER, all.get(3).kind);
		Assert.assertEquals(Kind.COUNTER, all.get(4).kind);
		Assert.assertEquals(Kind.KEYWORD, all.get(5).kind);
		Assert.assertEquals(Kind.DIE, all.get(6).kind);
		Assert.assertFalse(r.isEmpty());
		for (Need n : all)
			Assert.assertSame(n, r.find(n.key));
		Assert.assertNull(r.find("no such key"));
	}

	@Test
	public void noDeckOrNullCardsGiveAnEmptyResult() {
		Assert.assertTrue(DeckAccessories.compute(null, null).isEmpty());
		Assert.assertTrue(DeckAccessories.compute(new ArrayList<IMagicCard>(), db).isEmpty());
		List<IMagicCard> withNull = new ArrayList<IMagicCard>();
		withNull.add(null);
		Assert.assertTrue(DeckAccessories.compute(withNull, db).isEmpty());
	}

	@Test
	public void theBackOfADoubleFacedCardCountsToo() {
		MagicCard front = new MagicCard();
		front.setName("Front");
		front.setType("Creature");
		front.setOracleText("Transform.");
		front.set(MagicCardField.FLIPID, "back-id");
		MagicCard back = new MagicCard();
		back.setName("Back");
		back.setOracleText("When this becomes the back, you become the monarch.");
		when(db.getCard("back-id")).thenReturn(back);
		Result r = DeckAccessories.compute(Arrays.<IMagicCard> asList(front), db);
		Assert.assertEquals(Arrays.asList("The Monarch"), labels(r.playerMarkers));
	}

	// ---- identity text

	@Test
	public void identityIsNamePowerToughnessAndTraits() {
		MagicCard c = token("Angel", "Token Creature - Angel", "4", "4", "T");
		c.setOracleText("Flying, vigilance");
		Assert.assertEquals("angel|4/4|flying;vigilance;", DeckAccessories.identity(c));
		Assert.assertEquals("a card without name, power or toughness", "|/|", DeckAccessories.identity(new MagicCard()));
	}

	@Test
	public void traitsIgnoreMarkupLinksAndReminderText() {
		Assert.assertEquals("", DeckAccessories.tokenTraits(null));
		Assert.assertEquals("", DeckAccessories.tokenTraits(""));
		Assert.assertEquals("flying;", DeckAccessories.tokenTraits("Flying (This can't be blocked except by trample or reach.)"));
		Assert.assertEquals("", DeckAccessories.tokenTraits("<a href=\"http://x.io/flying\">Rulings</a> http://y.io/haste"));
	}
}
