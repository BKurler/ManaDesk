/*
 * Contributors:
 *     Rémi Dutil (2026) - created for Gladiator: singleton constructed, no
 *                         commander, configurable main deck / sideboard size
 */
package com.reflexit.magiccards.core.legality;

/**
 * Singleton main deck (max 1 copy per name, unlimited basic lands - see
 * {@link Format#validateCardCount(com.reflexit.magiccards.core.model.IMagicCard)}),
 * no commander zone. Used for Gladiator (100 cards, no sideboard).
 */
public class SingletonFormat extends Format {
	private final int mainDeckCount;
	private final int sideboardCount;

	public SingletonFormat(String name, int ord, int mainDeckCount, int sideboardCount) {
		super(name, ord);
		this.mainDeckCount = mainDeckCount;
		this.sideboardCount = sideboardCount;
	}

	@Override
	public int getMainDeckCount() {
		return mainDeckCount;
	}

	@Override
	public int getSideboardCount() {
		return sideboardCount;
	}

	@Override
	public String validateDeckCount(int count) {
		if (count == mainDeckCount)
			return null;
		return "Deck card count is " + count + " expected to be exactly " + mainDeckCount;
	}

	@Override
	public String validateCardCount(int count) {
		if (count <= 1)
			return null;
		return "Singleton. Only one card with this name allowed";
	}
}
