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

import java.util.Map;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;

/**
 * What "For Trade" means. A copy is for trade when its Special field has the {@value #TAG} tag: the whole pile
 * (the For Trade column only shows it - {@link MagicCardPhysical#getForTrade()}). The tag is free in a Standard
 * collection and in an owned deck: the user adds and removes it like any other tag. In a collection of the type
 * {@link CollectionType#FOR_TRADE} it is part of the rules:
 * <ul>
 * <li>every copy that goes in (copy, move, paste, import) gets the tag, and so does every copy already in the
 * collection when it becomes a For Trade collection ({@link #tagAll});</li>
 * <li>the tag cannot be removed from a copy there ({@link #specialVeto}, {@link #fieldVeto});</li>
 * <li>a proxy can never be traded: it is refused ({@link #addVeto}, {@link #fieldVeto},
 * {@link #proxiesVeto}). In another list, the tag on a proxy is just ignored (its For Trade count is 0).</li>
 * </ul>
 * Moving a copy out to a Standard collection keeps its tag. Decks and Wishlist/To Print collections have no
 * rule of their own: they behave like a Standard collection.
 */
public final class ForTradeRules {
	/** The Special tag of a copy offered for trade. */
	public static final String TAG = "fortrade";

	public static final String NO_PROXY_FOR_TRADE = "A proxy cannot be traded: a For Trade collection only holds genuine cards.";
	public static final String NO_REMOVE_TAG = "In a For Trade collection every card is for trade: the \"" + TAG
			+ "\" tag cannot be removed. Move the card to a Standard collection first.";

	private ForTradeRules() {
	}

	/** True when {@code info} describes a collection of the type For Trade (a deck has no type). */
	public static boolean isForTradeList(IStorageInfo info) {
		return info != null && info.getCollectionType() == CollectionType.FOR_TRADE;
	}

	/** True when {@code store} is a For Trade collection. */
	public static boolean isForTradeList(ICardStore<?> store) {
		return isForTradeList(OwnershipRules.getStorageInfo(store));
	}

	/** True when the collection at {@code loc} is a For Trade one; false for another or an unknown list. */
	public static boolean isForTradeList(Location loc) {
		if (loc == null)
			return false;
		try {
			return isForTradeList(DataManager.getInstance().getCardStore(loc));
		} catch (RuntimeException e) {
			return false;
		}
	}

	/**
	 * Whether {@code card} lands in a For Trade collection when added to {@code dest}. A single collection knows its
	 * type; the library store holds every list (an import goes through it), so there it is the type of the list the
	 * copy belongs to.
	 */
	static boolean goesToForTrade(Object card, ICardStore<?> dest) {
		if (dest == null)
			return false;
		IStorageInfo info = OwnershipRules.getStorageInfo(dest);
		if (info != null)
			return isForTradeList(info);
		return card instanceof MagicCardPhysical && isForTradeList(((MagicCardPhysical) card).getLocation());
	}

	/** Why {@code card} cannot be added to {@code dest}, or {@code null}: a proxy never goes to a For Trade collection. */
	public static String addVeto(Object card, ICardStore<?> dest) {
		if (card instanceof MagicCardPhysical && ((MagicCardPhysical) card).isProxy() && goesToForTrade(card, dest))
			return NO_PROXY_FOR_TRADE;
		return null;
	}

	/** Gives {@code card} the tag when it is added to a For Trade collection ({@code dest}); a proxy is left alone. */
	public static void prepare(Object card, ICardStore<?> dest) {
		if (card instanceof MagicCardPhysical && goesToForTrade(card, dest))
			tag((MagicCardPhysical) card);
	}

	/** Adds the tag to {@code card} (nothing for a proxy). Returns true when the card changed. */
	public static boolean tag(MagicCardPhysical card) {
		if (card.isProxy() || hasTag(card.getSpecial()))
			return false;
		card.setSpecialTag(TAG);
		return true;
	}

	/** Tags every copy of {@code cards} (a collection that just became For Trade). Returns how many changed. */
	public static int tagAll(Iterable<?> cards) {
		int n = 0;
		if (cards == null)
			return n;
		for (Object o : cards) {
			if (o instanceof MagicCardPhysical && tag((MagicCardPhysical) o))
				n++;
		}
		return n;
	}

	/**
	 * Why a collection holding {@code cards} cannot become a For Trade collection, or {@code null}: it must hold no
	 * proxy. {@code cards} {@code null} (not known) is no veto.
	 */
	public static String proxiesVeto(Iterable<?> cards) {
		if (cards == null)
			return null;
		int proxies = 0;
		for (Object o : cards) {
			if (o instanceof MagicCardPhysical && ((MagicCardPhysical) o).isProxy())
				proxies++;
		}
		if (proxies == 0)
			return null;
		return "contains " + proxies + " proxy entr" + (proxies == 1 ? "y" : "ies")
				+ " - a For Trade collection only holds genuine cards";
	}

	/** True when the Special text {@code special} has the tag. */
	public static boolean hasTag(String special) {
		return special != null && "true".equalsIgnoreCase(SpecialTags.getInstance().getSpecialValue(special, TAG));
	}

	/**
	 * Why the Special tags of {@code card} cannot change as {@code modification} says (the text of the Edit dialog:
	 * tags to add, "-tag" to remove), or {@code null}. In a For Trade collection the result must keep the tag.
	 */
	public static String specialVeto(MagicCardPhysical card, String modification) {
		if (card == null || modification == null || !isForTradeList(card.getLocation()))
			return null;
		if (modification.trim().isEmpty())
			return null; // an empty text changes nothing in the dialog
		Map<String, String> tags = new java.util.LinkedHashMap<>(SpecialTags.getInstance().toMap(card.getSpecial()));
		tags.putAll(SpecialTags.getInstance().toMap(modification));
		return "true".equalsIgnoreCase(tags.get(TAG)) ? null : NO_REMOVE_TAG;
	}

	/**
	 * Why {@code field} cannot be set to {@code value} on a copy in {@code store}, or {@code null}: in a For Trade
	 * collection the Special text keeps the tag (it replaces the whole text), and the copy does not become a proxy.
	 */
	public static String fieldVeto(ICardStore<?> store, ICardField field, Object value) {
		if (store == null || !isForTradeList(store))
			return null;
		if (field == MagicCardField.SPECIAL && !hasTag(value == null ? null : value.toString()))
			return NO_REMOVE_TAG;
		if (field == MagicCardField.PROXY
				&& (Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value))))
			return NO_PROXY_FOR_TRADE;
		return null;
	}
}
