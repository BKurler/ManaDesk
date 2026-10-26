/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - owned (non-virtual) decks: only owned cards moved in from a collection, nothing created
 *     Rémi Dutil (2026) - owned decks: no count change and no deletion, cards only move out
 *     Rémi Dutil (2026) - typeChangeVeto(): a read-only collection keeps its type; owned <-> virtual only while empty
 *******************************************************************************/
package com.reflexit.magiccards.core.model;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IStorage;
import com.reflexit.magiccards.core.model.storage.IStorageContainer;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;

/**
 * Ownership rules tied to the deck / collection a card is in. A card's
 * ownership follows its list: Own in a non-virtual one (Standard / For Trade,
 * an owned deck), virtual in a virtual one (Wishlist/To Print, the default deck).
 * A card whose stored ownership does not match (data from before these rules)
 * keeps it, but can only be changed to the value that matches its list - never
 * away from it. Moving / copying a card is how it changes ownership now (a
 * wished card moved to a Standard collection becomes Own), not the Own column.
 * Move and copy follow the card's own ownership against the destination's
 * (virtual or not), for decks and collections alike:
 * <ul>
 * <li>move: owned to non-virtual, virtual to virtual, and virtual to
 * non-virtual - the card then becomes Own (bought / printed; the UI asks for
 * confirmation). Never owned to virtual;</li>
 * <li>copy: owned to virtual, virtual to non-virtual (bought / printed) and
 * virtual to virtual - never owned to non-virtual. The copy always takes the
 * destination's ownership.</li>
 * </ul>
 * An owned (non-virtual) deck is stricter: it only receives owned cards moved in
 * from elsewhere, and cards only leave it by being moved out: copying, pasting,
 * importing, moving a virtual card in, changing a count or deleting cards would
 * create or destroy owned cards - see {@link #ownedDeckVeto},
 * {@link #countChangeVeto} and {@link #removeVeto}.
 */
public final class OwnershipRules {
	/** Move: an owned card only goes to a non-virtual deck / collection. */
	public static final String NO_MOVE_OWNED_TO_VIRTUAL = "Owned cards can only be moved to a non-virtual deck or collection."
			+ " Copy them instead.";
	/** Copy: an owned card copied into a non-virtual list would be counted twice. */
	public static final String NO_COPY_OWNED_TO_OWNED = "Owned cards cannot be copied into a non-virtual deck or collection"
			+ " (they would be counted twice). Move them instead.";
	public static final String NO_SET_OWNERSHIP = "A card's ownership follows its deck / collection:"
			+ " Own in a non-virtual one, virtual in a virtual one.";

	/** An owned (non-virtual) deck only receives owned cards moved from a non-virtual collection or deck. */
	public static final String NO_ADD_TO_OWNED_DECK = "This deck is not virtual: it only holds cards you own."
			+ " Only owned cards moved in from a non-virtual collection or deck can go into it."
			+ " Adding, copying, pasting or importing cards, or moving virtual cards (a Wishlist/To Print"
			+ " collection or a virtual deck), would create owned cards that do not exist.";

	/** The number of copies of an owned card is only changed in a collection. */
	public static final String NO_COUNT_CHANGE_IN_OWNED_DECK = "This deck is not virtual: the number of copies of an"
			+ " owned card can only be changed in a collection. To take cards out of the deck, move them to a"
			+ " collection.";
	/** Deleting would remove cards you own: they are moved out instead. */
	public static final String NO_REMOVE_FROM_OWNED_DECK = "This deck is not virtual: cards cannot be deleted from it,"
			+ " only moved out to a collection (deleting would remove cards you own).";

	private OwnershipRules() {
	}

	/** True when {@code store} is a deck that is not virtual (its cards are the ones you own). */
	public static boolean isOwnedDeck(ICardStore<?> store) {
		if (store == null)
			return false;
		IStorageInfo info = getStorageInfo(store);
		return info != null && IStorageInfo.DECK_TYPE.equals(info.getType()) && !store.isVirtual();
	}

	/**
	 * Why {@code card} cannot go into {@code dest} when {@code dest} is an owned
	 * deck, or {@code null}. Only an owned card moved in from elsewhere is fine -
	 * anything else (a database card, a virtual card, a copy) would create owned cards.
	 */
	public static String ownedDeckVeto(Object card, ICardStore<?> dest, boolean move) {
		if (!isOwnedDeck(dest))
			return null;
		// a database card (MagicCard) is not a copy in a list, even when you own the card
		if (move && card instanceof MagicCardPhysical && ((MagicCardPhysical) card).isOwn())
			return null;
		return NO_ADD_TO_OWNED_DECK;
	}

	/** Why the count of a card in {@code store} cannot go from {@code oldCount} to {@code newCount}, or {@code null}. */
	public static String countChangeVeto(ICardStore<?> store, int oldCount, int newCount) {
		if (newCount != oldCount && isOwnedDeck(store))
			return NO_COUNT_CHANGE_IN_OWNED_DECK;
		return null;
	}

	/** Why cards cannot be deleted from {@code store}, or {@code null}. */
	public static String removeVeto(ICardStore<?> store) {
		return isOwnedDeck(store) ? NO_REMOVE_FROM_OWNED_DECK : null;
	}

	/** The storage info of {@code store}, or {@code null}. */
	public static IStorageInfo getStorageInfo(ICardStore<?> store) {
		if (store instanceof IStorageContainer) {
			IStorage<?> storage = ((IStorageContainer<?>) store).getStorage();
			if (storage instanceof IStorageInfo)
				return (IStorageInfo) storage;
		}
		return null;
	}

	/** The ownership a card in the list at {@code loc} should have - true
	 *  (Own) for a non-virtual list, false for a virtual one - or {@code null}
	 *  when the list is unknown. */
	public static Boolean listOwnership(Location loc) {
		if (loc == null)
			return null;
		try {
			ICardStore<?> store = DataManager.getInstance().getCardStore(loc);
			return store == null ? null : Boolean.valueOf(!store.isVirtual());
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Why {@code card} cannot be moved into {@code dest}, or {@code null}. */
	public static String moveVeto(IMagicCardPhysical card, ICardStore<?> dest) {
		if (card == null || dest == null)
			return null;
		if (card.isOwn() && dest.isVirtual())
			return NO_MOVE_OWNED_TO_VIRTUAL;
		return null;
	}

	/** True when moving {@code card} into {@code dest} turns it Own (virtual
	 *  card into a non-virtual list) - the UI confirms this first. */
	public static boolean becomesOwnOnMove(IMagicCardPhysical card, ICardStore<?> dest) {
		return card != null && dest != null && !card.isOwn() && !dest.isVirtual();
	}

	/**
	 * The main collection (the default library) is always a sorted, writable
	 * Standard collection - repairs {@code info} if anything else was stored.
	 * Only writes what actually differs.
	 */
	public static void enforceMainCollection(IStorageInfo info) {
		if (info == null)
			return;
		if (info.isReadOnly())
			info.setReadOnly(false);
		if (!IStorageInfo.COLLECTION_TYPE.equals(info.getType()))
			info.setType(IStorageInfo.COLLECTION_TYPE);
		if (info.isVirtual() || info.getCollectionType() != CollectionType.STANDARD)
			info.setCollectionType(CollectionType.STANDARD);
		if (info.isUnsorted())
			info.setUnsorted(false);
	}

	/** True when {@code info} is the main collection's (the default library). */
	public static boolean isMainCollection(IStorageInfo info) {
		if (info == null)
			return false;
		try {
			return info == DataManager.getInstance().getModelRoot().getDefaultLib().getStorageInfo();
		} catch (RuntimeException e) {
			return false;
		}
	}

	/**
	 * Why the deck described by {@code info} cannot be made virtual / owned ({@code toVirtual}), or {@code null}:
	 * never while it is read only, and only while it - and its sideboard and extra list ({@code hasCards} covers all
	 * three) - holds no card.
	 */
	public static String deckVirtualChangeVeto(IStorageInfo info, boolean hasCards, boolean toVirtual) {
		if (info == null || info.isVirtual() == toVirtual)
			return null;
		if (info.isReadOnly())
			return "is read only (unlock it first)";
		if (hasCards)
			return "has cards: only an empty deck can become " + (toVirtual ? "virtual" : "owned");
		return null;
	}

	/**
	 * Why the collection described by {@code info}, holding {@code cards}, cannot change to type {@code to}, or
	 * {@code null}. A read-only collection keeps its type. Standard and For Trade switch between each other freely;
	 * becoming Wishlist/To Print (virtual) or leaving it (owned) is only for an empty collection - {@code cards}
	 * {@code null} means the cards are not known, so not allowed.
	 */
	public static String typeChangeVeto(IStorageInfo info, Iterable<?> cards, CollectionType to) {
		if (info == null || to == null || to == info.getCollectionType())
			return null;
		if (info.isReadOnly())
			return "is read only (unlock it first)";
		if (to.isVirtual() != info.isVirtual() && (cards == null || cards.iterator().hasNext()))
			return "has cards: only an empty collection can become " + (to.isVirtual() ? "virtual" : "owned");
		return null;
	}

	/**
	 * Why the collection holding {@code cards} cannot become type {@code to},
	 * or {@code null} when it can. Standard and For Trade are interchangeable;
	 * becoming Wishlist needs no owned card in it, and leaving it needs no
	 * virtual card in it.
	 */
	public static String collectionTypeVeto(Iterable<?> cards, CollectionType to) {
		if (cards == null || to == null)
			return null;
		int owned = 0;
		int virtual = 0;
		for (Object o : cards) {
			if (o instanceof IMagicCardPhysical) {
				if (((IMagicCardPhysical) o).isOwn())
					owned++;
				else
					virtual++;
			}
		}
		if (to.isVirtual() && owned > 0)
			return "contains " + owned + " owned card entr" + (owned == 1 ? "y" : "ies")
					+ " - a Wishlist/To Print collection only holds cards you do not own";
		if (!to.isVirtual() && virtual > 0)
			return "contains " + virtual + " virtual card entr" + (virtual == 1 ? "y" : "ies")
					+ " - move them to a Standard collection once bought";
		return null;
	}

	/**
	 * Whether {@code card} may be given ownership {@code own}: keeping its
	 * current value is always fine, a change is only allowed towards the
	 * ownership of its list (see the class comment).
	 */
	public static boolean canSetOwn(IMagicCardPhysical card, boolean own) {
		if (card == null || own == card.isOwn())
			return true;
		Boolean expected = listOwnership(card.getLocation());
		return expected == null || expected.booleanValue() == own;
	}
}
