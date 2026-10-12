/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - refresh(): brings the list up to date with the deck -
 *                         adds what is missing (count 0) and removes the count-0
 *                         entries the deck no longer needs; counts above 0 are
 *                         never touched
 *     Rémi Dutil (2026) - entries are matched by token (name + power/toughness),
 *                         not by exact printing: 2 Beast tokens of another set
 *                         already cover a needed Beast - no duplicate line
 *******************************************************************************/
package com.reflexit.magiccards.core.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.abs.ICardCountable;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IDbCardStore;

/**
 * Fills a deck's accessories list (the {@code -extra} sibling) with every
 * token / emblem / player-marker card the deck needs at the table, so the user
 * has a ready-made list to adjust instead of a blank page.
 *
 * <p>
 * Only real cards are added - counters, dice and status markers are not cards and
 * stay derived live by the Accessories view. New entries are added at
 * <b>count&nbsp;0</b>; entries already in the list and any count the user has
 * set are never touched, so this is safe to re-run after the deck changes.
 */
public final class DeckAccessoriesPopulator {

	private DeckAccessoriesPopulator() {
	}

	/**
	 * @param deckLoc any list of the deck (main, sideboard or accessories) - it is
	 *                normalised to the main deck
	 * @return the number of card entries added
	 */
	public static int populate(Location deckLoc) {
		return update(deckLoc, false)[0];
	}

	/**
	 * Brings the accessories list up to date with the deck: adds every needed
	 * card that is missing (at count 0) and removes the count-0 entries the deck
	 * no longer needs. An entry with a count above 0 is never touched - the
	 * user has it, whatever the deck says.
	 *
	 * @param deckLoc any list of the deck (main, sideboard or accessories)
	 * @return {added, removed}
	 */
	public static int[] refresh(Location deckLoc) {
		return update(deckLoc, true);
	}

	private static int[] update(Location deckLoc, boolean removeUnneeded) {
		int[] res = { 0, 0 };
		if (deckLoc == null)
			return res;
		DataManager dm = DataManager.getInstance();
		Location mainLoc = deckLoc.toMainDeck();
		Location accLoc = mainLoc.toExtra();
		ICardStore<IMagicCard> accStore = dm.getCardStore(accLoc);
		if (accStore == null)
			return res;
		Map<String, IMagicCard> needed = neededCards(dm, mainLoc);

		// entries with a count above 0 first: they are never touched and they
		// cover their token
		Set<String> present = new HashSet<>();
		List<IMagicCard> zero = new ArrayList<>();
		for (IMagicCard c : accStore.getCards()) {
			int count = c instanceof ICardCountable ? ((ICardCountable) c).getCount() : 1;
			if (count > 0)
				present.add(key(c));
			else
				zero.add(c);
		}
		// a count-0 entry goes when the deck no longer needs it, or when the same
		// token is already listed (e.g. a count-0 line next to your 2 copies)
		List<IMagicCard> toRemove = new ArrayList<>();
		for (IMagicCard c : zero) {
			String key = key(c);
			if (removeUnneeded && (!needed.containsKey(key) || present.contains(key)))
				toRemove.add(c);
			else
				present.add(key);
		}

		boolean virtual = accStore.isVirtual();
		List<MagicCardPhysical> toAdd = new ArrayList<>();
		for (Map.Entry<String, IMagicCard> e : needed.entrySet()) {
			if (!present.add(e.getKey()))
				continue; // already listed
			MagicCardPhysical phi = new MagicCardPhysical(e.getValue(), accLoc, virtual);
			phi.setCount(0);
			toAdd.add(phi);
		}
		if (!toRemove.isEmpty())
			dm.remove(toRemove, accStore);
		if (!toAdd.isEmpty()) {
			accStore.setMergeOnAdd(false);
			dm.add(toAdd, accStore);
			accStore.setMergeOnAdd(!accStore.isUnsorted());
		}
		res[0] = toAdd.size();
		res[1] = toRemove.size();
		return res;
	}

	/**
	 * What makes two entries "the same accessory": the token's name and its
	 * power / toughness - any printing will do (a Beast 3/3 from another set
	 * covers a needed Beast 3/3, a Beast 4/4 does not).
	 */
	static String key(IMagicCard c) {
		String name = c.getName() == null ? "" : c.getName().trim().toLowerCase(java.util.Locale.ROOT);
		String p = c.getPower() == null ? "" : c.getPower().trim();
		String t = c.getToughness() == null ? "" : c.getToughness().trim();
		return name + "|" + p + "/" + t;
	}

	/** The token / emblem / player-marker cards the deck (main + sideboard) needs, by {@link #key}. */
	private static Map<String, IMagicCard> neededCards(DataManager dm, Location mainLoc) {
		IDbCardStore<IMagicCard> db = dm.getMagicDBStore();
		List<IMagicCard> deck = new ArrayList<>();
		ICardStore<IMagicCard> main = dm.getCardStore(mainLoc);
		if (main != null)
			deck.addAll(main.getCards());
		ICardStore<IMagicCard> side = dm.getCardStore(mainLoc.toSideboard());
		if (side != null)
			deck.addAll(side.getCards());

		DeckAccessories.Result r = DeckAccessories.compute(deck, db);
		List<DeckAccessories.Need> needs = new ArrayList<>();
		needs.addAll(r.tokens);
		needs.addAll(r.emblems);
		needs.addAll(r.playerMarkers);
		Map<String, IMagicCard> needed = new LinkedHashMap<>();
		for (DeckAccessories.Need n : needs) {
			if (n.card == null)
				continue;
			needed.putIfAbsent(key(n.card), n.card);
		}
		return needed;
	}
}
