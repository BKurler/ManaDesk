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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.reflexit.magiccards.core.DataManager;
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
		if (deckLoc == null)
			return 0;
		DataManager dm = DataManager.getInstance();
		Location mainLoc = deckLoc.toMainDeck();
		Location accLoc = mainLoc.toExtra();
		ICardStore<IMagicCard> accStore = dm.getCardStore(accLoc);
		if (accStore == null)
			return 0;
		IDbCardStore<IMagicCard> db = dm.getMagicDBStore();

		List<IMagicCard> deck = new ArrayList<>();
		ICardStore<IMagicCard> main = dm.getCardStore(mainLoc);
		if (main != null)
			deck.addAll(main.getCards());
		ICardStore<IMagicCard> side = dm.getCardStore(mainLoc.toSideboard());
		if (side != null)
			deck.addAll(side.getCards());

		DeckAccessories.Result r = DeckAccessories.compute(deck, db);

		Set<String> present = new HashSet<>();
		for (IMagicCard c : accStore.getCards())
			if (c.getCardId() != null)
				present.add(c.getCardId());

		List<DeckAccessories.Need> needs = new ArrayList<>();
		needs.addAll(r.tokens);
		needs.addAll(r.emblems);
		needs.addAll(r.playerMarkers);

		boolean virtual = accStore.isVirtual();
		List<MagicCardPhysical> toAdd = new ArrayList<>();
		for (DeckAccessories.Need n : needs) {
			if (n.card == null)
				continue;
			String id = n.card.getCardId();
			if (id != null && !present.add(id))
				continue; // already listed
			MagicCardPhysical phi = new MagicCardPhysical(n.card, accLoc, virtual);
			phi.setCount(0);
			toAdd.add(phi);
		}
		if (!toAdd.isEmpty()) {
			accStore.setMergeOnAdd(false);
			dm.add(toAdd, accStore);
			accStore.setMergeOnAdd(!accStore.isUnsorted());
		}
		return toAdd.size();
	}
}
