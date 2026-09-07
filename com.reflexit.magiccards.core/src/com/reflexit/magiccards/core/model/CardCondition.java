/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: first-class card condition (grading)
 */
package com.reflexit.magiccards.core.model;

/**
 * The physical condition (grade) of a card copy, on the 5-tier TCGplayer scale.
 * Declared best-to-worst so a plain {@link Comparable} sort ranks Near Mint
 * first. A copy with no grade is represented by {@code null} ("not graded").
 */
public enum CardCondition {
	NEAR_MINT("Near Mint", "NM"),
	LIGHTLY_PLAYED("Lightly Played", "LP"),
	MODERATELY_PLAYED("Moderately Played", "MP"),
	HEAVILY_PLAYED("Heavily Played", "HP"),
	DAMAGED("Damaged", "DMG");

	private final String label;
	private final String abbr;

	CardCondition(String label, String abbr) {
		this.label = label;
		this.abbr = abbr;
	}

	public String getLabel() {
		return label;
	}

	public String getAbbr() {
		return abbr;
	}

	/**
	 * The abbreviation ("NM", "LP", ...). This is the canonical serialized form:
	 * it is what {@code String.valueOf(condition)} writes to XML and to CSV
	 * exports, and what the filter matches on. {@link #resolve} reads it (and the
	 * label, and the enum name) back.
	 */
	@Override
	public String toString() {
		return abbr;
	}

	/**
	 * Parse a label ("Near Mint"), abbreviation ("NM"), enum name ("NEAR_MINT")
	 * or a compacted variant, case-insensitively. Blank / unknown -&gt;
	 * {@code null} (not graded).
	 */
	public static CardCondition resolve(String s) {
		if (s == null)
			return null;
		String t = s.trim();
		if (t.isEmpty() || t.equals("-") || t.equals("—"))
			return null;
		for (CardCondition c : values()) {
			if (t.equalsIgnoreCase(c.label) || t.equalsIgnoreCase(c.abbr) || t.equalsIgnoreCase(c.name()))
				return c;
		}
		switch (t.toLowerCase().replaceAll("[^a-z]", "")) {
		case "nm":
		case "nearmint":
		case "mint":
			return NEAR_MINT;
		case "lp":
		case "lightlyplayed":
			return LIGHTLY_PLAYED;
		case "mp":
		case "moderatelyplayed":
			return MODERATELY_PLAYED;
		case "hp":
		case "heavilyplayed":
			return HEAVILY_PLAYED;
		case "dmg":
		case "damaged":
			return DAMAGED;
		default:
			return null;
		}
	}

	/** Null-safe order: not-graded sorts last, otherwise best ({@link #NEAR_MINT}) first. */
	public static int compare(CardCondition a, CardCondition b) {
		if (a == null && b == null)
			return 0;
		if (a == null)
			return 1;
		if (b == null)
			return -1;
		return a.ordinal() - b.ordinal();
	}
}
