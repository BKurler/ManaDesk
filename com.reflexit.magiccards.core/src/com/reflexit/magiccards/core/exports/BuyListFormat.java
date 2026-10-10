/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - a list of cards to buy, as text each store's bulk
 *                         entry box accepts (pasted by the user), asking for
 *                         the exact printing where the store supports it;
 *                         groups(): TCGplayer gets one list per finish (its
 *                         Mass Entry only has a page-wide Normal/Foil choice).
 *     Rémi Dutil (2026) - TCGplayer: "qty-productId" lines when the product
 *                         id is known (verified), merging rebuilt through
 *                         the formatter; Group.getLines().
 *     Rémi Dutil (2026) - FACE_TO_FACE (Face to Face Games deck builder,
 *                         names only).
 *     Rémi Dutil (2026) - splitting by finish is now the standard for every
 *                         store (groups(lines, exact, combineFinishes)): a
 *                         Non-foil and a Foil list, each with the store's
 *                         hint (finishHint); combineFinishes = one list.
 *     Rémi Dutil (2026) - finish follows the same rule as the set: any finish
 *                         without exact printing (one list), exact finish with
 *                         it (two lists unless combined).
 *     Rémi Dutil (2026) - CARDMARKET: printing support off until its set-name
 *                         syntax is verified - only TCGplayer takes an exact
 *                         printing for now.
 *     Rémi Dutil (2026) - MYTHIC_STORE and IMAGINAIRE (Quebec stores,
 *                         "qty name", English names - verified). *******************************************************************************/
package com.reflexit.magiccards.core.exports;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.reflexit.magiccards.core.model.CardFinish;

/**
 * Text for a store's bulk-entry box, one card per line. With
 * {@code exactPrinting} each format asks for the most precise printing the
 * store understands; identical output lines are merged (quantities added).
 * <p>
 * What each store understands (2026):
 * <ul>
 * <li>TCGplayer Mass Entry: {@code 1-563218} - quantity + TCGplayer product id
 * = the exact product, whatever TCGplayer names it (e.g. "Manabond (Future
 * Sight)"); verified, in the box and in the {@code c=} link. Without an id:
 * {@code 1 Lightning Bolt [SLD] 84} (set code in brackets + collector
 * number). The finish
 * has no per-line syntax: it's the page-wide "Printing: Normal / Foil"
 * checkboxes - hence one list per finish ({@link #groups}).</li>
 * <li>Cardmarket Wants import: {@code 1 Lightning Bolt (Secret Lair Drop)} -
 * set by its expansion NAME (Cardmarket's own naming, which can differ from
 * Scryfall's). No collector number, no finish.</li>
 * <li>Card Kingdom Deck Builder: {@code 1 Lightning Bolt} - names only; it
 * picks the printing itself ("Match card style"). No set syntax (verified).</li>
 * <li>Face to Face Games deck builder: {@code 1 Lightning Bolt} ({@code 1x}
 * or names alone also accepted) - names only.</li>
 * <li>Plain: {@code 1 Lightning Bolt (SLD) 84 *F*} - the common decklist
 * convention (Moxfield / Archidekt / Arena-like), for any other store.</li>
 * </ul>
 */
public enum BuyListFormat {
	TCGPLAYER("TCGplayer", true, false) {
		@Override
		String line(BuyListLine l, boolean exact) {
			if (exact && l.getTcgProductId() > 0)
				return l.getQuantity() + "-" + l.getTcgProductId(); // exact product, no name guessing
			StringBuilder sb = new StringBuilder().append(l.getQuantity()).append(' ').append(l.getName());
			if (exact && l.getSetCode() != null) {
				sb.append(" [").append(l.getSetCode()).append(']');
				if (l.getCollectorNumber() != null)
					sb.append(' ').append(l.getCollectorNumber());
			}
			return sb.toString();
		}

		/** Mass Entry: Normal / Foil is a page-wide "Printing" checkbox. */
		@Override
		String finishHint(boolean foil) {
			return "On TCGplayer Mass Entry, check only Printing: " + (foil ? "Foil" : "Normal");
		}
	},
	// set-name syntax not verified (no account outside Europe): not offered for an exact printing
	CARDMARKET("Cardmarket", false, true) {
		@Override
		String line(BuyListLine l, boolean exact) {
			StringBuilder sb = new StringBuilder().append(l.getQuantity()).append(' ').append(l.getName());
			if (exact && l.getSetName() != null)
				sb.append(" (").append(l.getSetName()).append(')');
			return sb.toString();
		}
	},
	CARD_KINGDOM("Card Kingdom", false, false) {
		@Override
		String line(BuyListLine l, boolean exact) {
			return l.getQuantity() + " " + l.getName();
		}
	},
	FACE_TO_FACE("Face to Face Games", false, false) {
		@Override
		String line(BuyListLine l, boolean exact) {
			return l.getQuantity() + " " + l.getName();
		}
	},
	/** The Mythic Store multi card search: {@code 3 Beanstalk Giant} (MTGGoldfish format, verified), names only. */
	MYTHIC_STORE("The Mythic Store", false, true) {
		@Override
		String line(BuyListLine l, boolean exact) {
			return l.getQuantity() + " " + l.getName();
		}

	},
	/** Imaginaire deck builder: {@code 1 Lightning Bolt}, English names (verified), names only. */
	IMAGINAIRE("Imaginaire", false, true) {
		@Override
		String line(BuyListLine l, boolean exact) {
			return l.getQuantity() + " " + l.getName();
		}

	},
	PLAIN("Plain list", true, true) {
		@Override
		String line(BuyListLine l, boolean exact) {
			StringBuilder sb = new StringBuilder().append(l.getQuantity()).append(' ').append(l.getName());
			if (exact && l.getSetCode() != null) {
				sb.append(" (").append(l.getSetCode()).append(')');
				if (l.getCollectorNumber() != null)
					sb.append(' ').append(l.getCollectorNumber());
			}
			if (exact && l.getFinish() == CardFinish.FOIL)
				sb.append(" *F*");
			else if (exact && l.getFinish() == CardFinish.ETCHED)
				sb.append(" *E*");
			return sb.toString();
		}

		@Override
		String finishHint(boolean foil) {
			return foil ? "Foil and etched cards (marked *F* / *E*)" : "Non-foil cards";
		}
	};

	private final String label;
	private final boolean printing;
	private final boolean finish;

	BuyListFormat(String label, boolean printing, boolean finish) {
		this.label = label;
		this.printing = printing;
		this.finish = finish;
	}

	public String getLabel() {
		return label;
	}

	/** Can this store be told which printing (set) to pick? */
	public boolean supportsPrinting() {
		return printing;
	}

	/**
	 * Can this store be told the finish (foil / etched) - per line, or by one
	 * list per finish pasted with the store's own foil option? Stores that
	 * cannot (Card Kingdom, Face to Face) are not offered with Exact Match.
	 */
	public boolean supportsFinish() {
		return finish;
	}

	abstract String line(BuyListLine l, boolean exact);

	/** One list to paste, with what the user must set on the store's page for it. */
	public static final class Group {
		private final String title;
		private final String hint;
		private final List<String> lines;

		Group(String title, String hint, List<String> lines) {
			this.title = title;
			this.hint = hint;
			this.lines = java.util.Collections.unmodifiableList(lines);
		}

		public String getTitle() {
			return title;
		}

		/** Store-page setting to use with this list, or "" when none. */
		public String getHint() {
			return hint;
		}

		/** The whole list, one line per card. */
		public String getText() {
			return String.join("\n", lines);
		}

		/** The lines one by one (e.g. to build a store link). */
		public List<String> getLines() {
			return lines;
		}
	}

	/** {@link #groups(Collection, boolean, boolean)} - split by finish. */
	public List<Group> groups(Collection<BuyListLine> lines, boolean exactPrinting) {
		return groups(lines, exactPrinting, false);
	}

	/**
	 * The list as the store needs it pasted. Like the set, the finish only
	 * matters with {@code exactPrinting}: then one "Non-foil" and one "Foil"
	 * list (no store takes the finish per line - they choose foil / non-foil
	 * once for the whole paste; etched goes with foil), unless
	 * {@code combineFinishes}. Without exact printing: one list, any finish.
	 * Empty lists are left out.
	 */
	public List<Group> groups(Collection<BuyListLine> lines, boolean exactPrinting, boolean combineFinishes) {
		if (!exactPrinting || combineFinishes) {
			List<Group> res = new ArrayList<>();
			addGroup(res, getLabel(), "", lines, exactPrinting);
			return res;
		}
		return splitByFinish(lines, exactPrinting, finishHint(false), finishHint(true));
	}

	/** What to set on the store's page for the foil (or non-foil) list. */
	String finishHint(boolean foil) {
		return "On " + getLabel() + ", choose " + (foil ? "Foil" : "Non-foil");
	}

	/** A "Non-foil" and a "Foil" list (etched goes with foil). */
	List<Group> splitByFinish(Collection<BuyListLine> lines, boolean exactPrinting, String normalHint,
			String foilHint) {
		List<BuyListLine> normal = new ArrayList<>();
		List<BuyListLine> foil = new ArrayList<>();
		for (BuyListLine l : lines)
			if (l != null)
				(exactPrinting && l.getFinish() != CardFinish.NONFOIL ? foil : normal).add(l);
		List<Group> res = new ArrayList<>();
		addGroup(res, "Non-foil", normalHint, normal, exactPrinting);
		addGroup(res, "Foil", foilHint, foil, exactPrinting);
		return res;
	}

	void addGroup(List<Group> res, String title, String hint, Collection<BuyListLine> lines, boolean exact) {
		List<String> formatted = formatLines(lines, exact);
		if (!formatted.isEmpty())
			res.add(new Group(title, hint, formatted));
	}

	/**
	 * @param exactPrinting ask for each line's own printing when the store
	 *        supports it; otherwise names only (any printing)
	 * @return one line per card ({@code \n}-separated, no trailing newline);
	 *         lines with a quantity below 1 or no name are skipped
	 */
	public String format(Collection<BuyListLine> lines, boolean exactPrinting) {
		return String.join("\n", formatLines(lines, exactPrinting));
	}

	/** {@link #format} as separate lines. */
	public List<String> formatLines(Collection<BuyListLine> lines, boolean exactPrinting) {
		// identical output (ignoring the quantity) = same product: merged, quantities added
		Map<String, BuyListLine> merged = new LinkedHashMap<>();
		for (BuyListLine l : lines) {
			if (l == null || l.getQuantity() < 1 || l.getName().isEmpty())
				continue;
			String key = line(l.withQuantity(1), exactPrinting);
			BuyListLine prev = merged.get(key);
			merged.put(key, prev == null ? l : prev.withQuantity(prev.getQuantity() + l.getQuantity()));
		}
		List<String> out = new ArrayList<>();
		for (BuyListLine l : merged.values())
			out.add(line(l, exactPrinting));
		return out;
	}
}
