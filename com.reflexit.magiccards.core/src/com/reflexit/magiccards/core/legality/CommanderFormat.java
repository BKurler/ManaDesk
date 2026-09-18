/*
 * Contributors:
 *     Rémi Dutil (2026) - generalized to a configurable main-deck/"commander
 *                         zone" size instead of the hardcoded 99+1, so the
 *                         same shape (singleton, N main + M commander-zone
 *                         cards in the sideboard slot) covers every real
 *                         commander-family format: Commander itself (99+1),
 *                         Duel Commander / Pauper Commander / PreDH (also
 *                         99+1, different legal card pool - handled entirely
 *                         by Scryfall's own per-format legality, nothing
 *                         extra needed here), Brawl / Standard Brawl (59+1 =
 *                         60 total), and Oathbreaker (58 main + 2: the
 *                         Oathbreaker planeswalker and its signature spell)
 */
package com.reflexit.magiccards.core.legality;

/**
 * Singleton main deck + a small fixed-size "commander zone" (the deck's
 * sideboard slot): Commander/Duel Commander/Pauper Commander/PreDH (99 + 1),
 * Brawl/Standard Brawl (59 + 1), Oathbreaker (58 + 2).
 */
public class CommanderFormat extends Format {
	private final int mainDeckCount;
	private final int commanderZoneCount;

	public CommanderFormat() {
		this("Commander", 4, 99, 1);
	}

	/** 99 main + 1 commander-zone card, like Commander itself - for formats
	 *  that only differ from it in legal card pool (Duel Commander, Pauper
	 *  Commander, PreDH), which Scryfall's own per-format legality already
	 *  accounts for. */
	public CommanderFormat(String name, int ord) {
		this(name, ord, 99, 1);
	}

	public CommanderFormat(String name, int ord, int mainDeckCount, int commanderZoneCount) {
		super(name, ord);
		this.mainDeckCount = mainDeckCount;
		this.commanderZoneCount = commanderZoneCount;
	}

	@Override
	public int getMainDeckCount() {
		return mainDeckCount;
	}

	@Override
	public int getSideboardCount() {
		return commanderZoneCount;
	}

	@Override
	public String validateDeckCount(int count) {
		if (count == mainDeckCount)
			return null;
		return "Deck card count is " + count + " expected to be exactly " + mainDeckCount
				+ (commanderZoneCount == 1 ? ". Commander should be in sideboard."
						: ". Commander zone cards should be in sideboard.");
	}

	@Override
	public String validateSideboardCount(int count) {
		if (count == commanderZoneCount)
			return null;
		return "Sideboard should contain exactly " + commanderZoneCount + " commander zone card"
				+ (commanderZoneCount == 1 ? "" : "s");
	}

	@Override
	public String validateCardCount(int count) {
		if (count <= 1)
			return null;
		return "Singleton. Only one card with this name allowed";
	}
}
