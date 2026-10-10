/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - the Proxier's pooled "what do these decks need"
 *                         calculation (Owned / Boxed / Available / Needed /
 *                         shortfall, per-list breakdown), extracted from
 *                         ProxierView so the Proxier and the Buyer view share
 *                         one implementation.
 *     Rémi Dutil (2026) - compute(..., byFinish): the finish is part of the row
 *                         (Owned / Boxed counted for that finish only) - the
 *                         Buyer needs foil and non-foil apart; the Proxier
 *                         keeps pooling finishes.
 *******************************************************************************/
package com.reflexit.magiccards.core.model.nav;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.storage.ICardStore;

/**
 * What a batch of decks / collections needs, pooled per card: one {@link Row}
 * per card name (or per exact printing - name + set + collector number - when
 * {@code exact}). Each row has:
 * <ul>
 * <li><b>Owned</b> - genuine + proxy copies owned, whole app (every printing of
 * the name, or that exact printing's own count when {@code exact});</li>
 * <li><b>Boxed</b> - copies claimed by already-boxed decks (unavailable). Every
 * slot of a boxed deck counts, materialized or still virtual;</li>
 * <li><b>Available</b> - Owned minus Boxed, each bucket floored at 0;</li>
 * <li><b>Wanted</b> - the raw demand across the given sources;</li>
 * <li>{@link Row#shortfall()} - Wanted minus Available (genuine + proxy): slots
 * with no copy at all (the Proxier's "To Print"), and
 * {@link Row#genuineShortfall()} - Wanted minus Available genuine copies only
 * (what to buy when proxies are to be replaced).</li>
 * </ul>
 * A source's Sideboard and Extra piles are included, reported as separate
 * lists in {@link Row#perList}.
 */
public final class DeckNeeds {

	private DeckNeeds() {
	}

	/** A Genuine/Proxy split - Owned, Boxed and Available all share this shape. */
	public static final class Split {
		public int genuine;
		public int proxy;

		public int total() {
			return genuine + proxy;
		}
	}

	/** One list's (main / Sideboard / Extra) own state for one card - no cross-list math. */
	public static final class ListInfo {
		public int real;
		public int proxy;
		public int needed;
		/** A representative copy from this specific list. */
		public MagicCardPhysical sample;

		public int total() {
			return real + proxy + needed;
		}
	}

	/** A source, qualified by which of its lists: "" (main), " (Sideboard)" or " (Extra)". */
	public static final class ListRef {
		public final CardCollection source;
		public final String suffix;

		ListRef(CardCollection source, String suffix) {
			this.source = source;
			this.suffix = suffix;
		}

		public String label() {
			return source.getName() + suffix;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof ListRef))
				return false;
			ListRef d = (ListRef) o;
			return source == d.source && suffix.equals(d.suffix);
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(source) * 31 + suffix.hashCode();
		}
	}

	/** One pooled row: a card (or exact printing) and its breakdown by list. */
	public static final class Row {
		public final String cardName;
		/** A real copy taken from one of the sources - its printing (and finish). */
		public MagicCardPhysical sample;
		/** The row's finish when computed {@code byFinish}, else {@code null} (finishes pooled). */
		public CardFinish finish;
		public final Split owned = new Split();
		public final Split boxed = new Split();
		/** Raw total demand across the sources. */
		public int wanted;
		public final Map<ListRef, ListInfo> perList = new LinkedHashMap<>();

		Row(String cardName) {
			this.cardName = cardName;
		}

		/** Owned stock not claimed by a boxed deck - each bucket floored at 0. */
		public Split available() {
			Split a = new Split();
			a.genuine = Math.max(0, owned.genuine - boxed.genuine);
			a.proxy = Math.max(0, owned.proxy - boxed.proxy);
			return a;
		}

		/** Slots with no copy at all, real or proxy: max(0, wanted - available). */
		public int shortfall() {
			return Math.max(0, wanted - available().total());
		}

		/** Slots without a genuine copy (proxies count as missing): max(0, wanted - available genuine). */
		public int genuineShortfall() {
			return Math.max(0, wanted - available().genuine);
		}
	}

	/**
	 * Pools every card of {@code sources} (and their Sideboard / Extra piles).
	 *
	 * @return rows sorted by card name, then set
	 */
	public static List<Row> compute(List<CardCollection> sources, boolean exact) {
		return compute(sources, exact, false);
	}

	/**
	 * @param byFinish one row per finish too (non-foil / foil / etched), with
	 *        Owned and Boxed counting only copies of that finish - a non-foil
	 *        copy does not cover a foil one
	 */
	public static List<Row> compute(List<CardCollection> sources, boolean exact, boolean byFinish) {
		Map<String, Row> rows = new LinkedHashMap<>();
		for (CardCollection cc : sources) {
			for (SourcedCard sc : cardsOfWithSuffix(cc)) {
				if (!(sc.card instanceof MagicCardPhysical))
					continue;
				MagicCardPhysical mcp = (MagicCardPhysical) sc.card;
				int count = mcp.getCount();
				if (count <= 0)
					continue;
				String key = rowKeyFor(mcp, exact, byFinish);
				Row row = rows.computeIfAbsent(key, k -> new Row(mcp.getName()));
				if (row.sample == null) {
					row.sample = mcp;
					if (byFinish) {
						row.finish = mcp.getFinish();
						ownedWithFinish(mcp, exact, row.finish, row.owned);
					} else if (exact) {
						// this exact printing's own aggregate - not pooled across other printings
						row.owned.genuine = mcp.getCard().getGenuineOwnCount();
						row.owned.proxy = mcp.getCard().getOwnCount() - row.owned.genuine;
					} else {
						row.owned.genuine = mcp.getGenuineOwnTotalAll();
						row.owned.proxy = mcp.getOwnTotalAll() - row.owned.genuine;
					}
				}
				ListInfo info = row.perList.computeIfAbsent(new ListRef(cc, sc.suffix), k -> new ListInfo());
				if (info.sample == null)
					info.sample = mcp;
				if (mcp.isProxy())
					info.proxy += count;
				else if (mcp.isOwn())
					info.real += count;
				else
					info.needed += count;
				row.wanted += count;
			}
		}

		Map<String, Split> boxedCounts = computeBoxedCounts(exact, byFinish);
		for (Map.Entry<String, Row> e : rows.entrySet()) {
			Split b = boxedCounts.get(e.getKey());
			if (b != null) {
				e.getValue().boxed.genuine = b.genuine;
				e.getValue().boxed.proxy = b.proxy;
			}
		}

		List<Row> sorted = new ArrayList<>(rows.values());
		sorted.sort(Comparator.<Row, String> comparing(r -> r.cardName, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(r -> r.sample == null ? "" : String.valueOf(r.sample.getSet()))
				.thenComparing(r -> r.finish == null ? 0 : r.finish.ordinal()));
		return sorted;
	}

	/**
	 * The row-grouping key: card name alone when {@code exact} is off (every
	 * printing pooled - matches Owned using getOwnTotalAll()); name + set +
	 * collector number when on (matches Owned using the per-printing
	 * getOwnCount()). Boxed counts use the same key so a boxed deck's claim
	 * lands on the right row.
	 */
	public static String rowKeyFor(MagicCardPhysical mcp, boolean exact) {
		if (!exact)
			return mcp.getName();
		return mcp.getName() + ' ' + mcp.getSet() + ' ' + mcp.getCard().getCollNumber();
	}

	static String rowKeyFor(MagicCardPhysical mcp, boolean exact, boolean byFinish) {
		String key = rowKeyFor(mcp, exact);
		return byFinish ? key + " |" + mcp.getFinish() : key;
	}

	/**
	 * Owned copies of {@code finish} only, genuine vs proxy: of {@code mcp}'s
	 * exact printing, or of every printing of the name.
	 */
	private static void ownedWithFinish(MagicCardPhysical mcp, boolean exact, CardFinish finish, Split into) {
		List<MagicCard> printings = new ArrayList<>();
		if (exact) {
			if (mcp.getCard() instanceof MagicCard)
				printings.add((MagicCard) mcp.getCard());
		} else {
			for (IMagicCard c : DataManager.getInstance().getMagicDBStore().getCandidates(mcp.getName()))
				if (c instanceof MagicCard)
					printings.add((MagicCard) c);
		}
		for (MagicCard printing : printings)
			for (MagicCardPhysical copy : printing.getPhysicalCards()) {
				if (!copy.isOwn() || copy.getFinish() != finish)
					continue;
				if (copy.isProxy())
					into.proxy += copy.getCount();
				else
					into.genuine += copy.getCount();
			}
	}

	/**
	 * Copies of each row key claimed by already-boxed decks, genuine vs proxy.
	 * One pass over every boxed deck app-wide - a boxed deck's cards are spoken
	 * for regardless of what is being planned.
	 */
	private static Map<String, Split> computeBoxedCounts(boolean exact, boolean byFinish) {
		Map<String, Split> boxed = new HashMap<>();
		for (CardCollection cc : DataManager.getInstance().getModelRoot().getDeckContainer().getAllElements()) {
			if (!cc.isDeck() || !cc.isBoxed())
				continue;
			for (SourcedCard sc : cardsOfWithSuffix(cc)) {
				if (!(sc.card instanceof MagicCardPhysical))
					continue;
				MagicCardPhysical mcp = (MagicCardPhysical) sc.card;
				int count = mcp.getCount();
				if (count <= 0)
					continue;
				Split split = boxed.computeIfAbsent(rowKeyFor(mcp, exact, byFinish), k -> new Split());
				if (mcp.isProxy())
					split.proxy += count;
				else
					split.genuine += count;
			}
		}
		return boxed;
	}

	/** One card paired with which of a source's lists it came from. */
	private static final class SourcedCard {
		final String suffix;
		final IMagicCard card;

		SourcedCard(String suffix, IMagicCard card) {
			this.suffix = suffix;
			this.card = card;
		}
	}

	/**
	 * A source's own cards plus its Sideboard and Extra piles, if it has them,
	 * each tagged with its list ("", " (Sideboard)", " (Extra)"). Resolved
	 * directly by {@link Location} via {@code DataManager.getCardStore}, checking
	 * the returned store for null - not via {@code CardElement.getRelatedElements()},
	 * which depends on the piles already being loaded in the navigator tree.
	 */
	private static List<SourcedCard> cardsOfWithSuffix(CardCollection source) {
		List<SourcedCard> all = new ArrayList<>();
		for (IMagicCard c : source.getStore().getCards())
			all.add(new SourcedCard("", c));
		Location main = source.getLocation().toMainDeck();
		addSuffixed(all, main.toSideboard(), " (Sideboard)");
		addSuffixed(all, main.toExtra(), " (Extra)");
		return all;
	}

	private static void addSuffixed(List<SourcedCard> all, Location loc, String suffix) {
		try {
			ICardStore<IMagicCard> store = DataManager.getInstance().getCardStore(loc);
			if (store != null)
				for (IMagicCard c : store.getCards())
					all.add(new SourcedCard(suffix, c));
		} catch (RuntimeException e) {
			// no sideboard/extra for this source
		}
	}
}
