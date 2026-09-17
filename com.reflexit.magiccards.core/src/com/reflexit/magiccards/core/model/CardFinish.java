/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: first-class card finish
 *                         (Regular / Foil / Etched)
 *     Rémi Dutil (2026) - joinTags()/joinLabels(): format a set of finishes
 *                         (a printing's supported finishes, or a single
 *                         owned copy's, degenerately) the same way in every
 *                         place that needs to - the field dispatch used by
 *                         filtering/generic display, and the Finish column's
 *                         own printing-row rendering
 *     Rémi Dutil (2026) - label reverted from "Regular" to "Nonfoil" -
 *                         Scryfall's own vocabulary ("nonfoil"/"foil"/
 *                         "etched"), which ManaDesk should just match
 *                         everywhere (views/filter/import/export), not
 *                         invent its own wording for
 */
package com.reflexit.magiccards.core.model;

import java.util.Set;

/**
 * The physical finish of a card copy: a plain (nonfoil) print, a foil print,
 * or an etched-foil print. Unlike {@link CardCondition} this never comes back
 * {@code null} for a real copy - {@link MagicCardPhysical#getFinish()} always
 * resolves to one of these three, deriving a value when none was explicitly
 * set (see there for the derivation rule).
 */
public enum CardFinish {
	NONFOIL("Nonfoil", "nonfoil"),
	FOIL("Foil", "foil"),
	ETCHED("Etched", "etched");

	private final String label;
	private final String tag;

	CardFinish(String label, String tag) {
		this.label = label;
		this.tag = tag;
	}

	public String getLabel() {
		return label;
	}

	/**
	 * The canonical serialized form ({@code "nonfoil"} / {@code "foil"} /
	 * {@code "etched"} - the same spelling Scryfall itself uses). This is what
	 * gets written to XML/CSV and what the filter matches on.
	 */
	@Override
	public String toString() {
		return tag;
	}

	/**
	 * Parse a label ("Foil"), the canonical tag ("foil"), an enum name
	 * ("FOIL"), or a couple of common synonyms - case-insensitively. Blank /
	 * unknown -&gt; {@code null}.
	 */
	public static CardFinish resolve(String s) {
		if (s == null)
			return null;
		String t = s.trim();
		if (t.isEmpty())
			return null;
		for (CardFinish f : values()) {
			if (t.equalsIgnoreCase(f.label) || t.equalsIgnoreCase(f.tag) || t.equalsIgnoreCase(f.name()))
				return f;
		}
		switch (t.toLowerCase().replaceAll("[^a-z]", "")) {
		case "nonfoil":
		case "regular":
		case "normal":
		case "nf":
			return NONFOIL;
		case "foil":
		case "premium": // MTGO's own name for foil
		case "f":
			return FOIL;
		case "etched":
		case "etchedfoil":
		case "e":
			return ETCHED;
		default:
			return null;
		}
	}

	/** {@code finishes}, comma-joined by canonical tag, in enum order - e.g.
	 *  {@code "nonfoil,foil"}. Degenerates to a single tag for a one-element
	 *  set (an owned copy's own finish). This is what field dispatch
	 *  (filtering, generic column text) works with. */
	public static String joinTags(Set<CardFinish> finishes) {
		StringBuilder sb = new StringBuilder();
		for (CardFinish f : values())
			if (finishes.contains(f)) {
				if (sb.length() > 0)
					sb.append(",");
				sb.append(f.tag);
			}
		return sb.toString();
	}

	/** {@code finishes}, comma-and-space-joined by label, in enum order - e.g.
	 *  {@code "Nonfoil, Foil"}. What the Finish column shows for a printing
	 *  row (several finishes at once - an owned copy's own single finish uses
	 *  its label directly, never this). */
	public static String joinLabels(Set<CardFinish> finishes) {
		StringBuilder sb = new StringBuilder();
		for (CardFinish f : values())
			if (finishes.contains(f)) {
				if (sb.length() > 0)
					sb.append(", ");
				sb.append(f.label);
			}
		return sb.toString();
	}
}
