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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The short facts about a card that Scryfall gives us - on the Reserved List, a Commander Game
 * Changer, a reprint, how it looks (showcase, borderless, full art, a stamp...), its size
 * (oversized), its watermark - put together as one list ("Reserved List, Showcase, Watermark:
 * Selesnya") so they take one column and one line of the card text instead of one each.
 * <p>
 * The first three come from their own fields; the rest are stored as labels in
 * {@link MagicCardField#EXTRA_TRAITS} when the card database is updated.
 */
public final class CardTraits {
	public static final String RESERVED_LIST = "Reserved List";
	public static final String GAME_CHANGER = "Game Changer";
	public static final String REPRINT = "Reprint";

	/** Separates the labels in {@link MagicCardField#EXTRA_TRAITS}. */
	public static final char EXTRA_SEPARATOR = '|';

	public static final String WATERMARK_PREFIX = "Watermark: ";
	/** Scryfall's promo_types (all lower-case, run together) -> the label shown. */
	private static final Map<String, String> PROMO = new LinkedHashMap<String, String>();
	static {
		String[][] p = { { "prerelease", "Prerelease" }, { "datestamped", "Datestamped" }, { "stamped", "Stamped" },
				{ "promopack", "Promo Pack" }, { "setpromo", "Set Promo" }, { "mediainsert", "Media Insert" },
				{ "playtest", "Playtest" }, { "sldbonus", "Secret Lair Bonus" }, { "poster", "Poster" },
				{ "instore", "In-Store" }, { "tourney", "Tournament" }, { "startercollection", "Starter Collection" },
				{ "serialized", "Serialized" }, { "boxtopper", "Box Topper" }, { "sourcematerial", "Source Material" },
				{ "fnm", "FNM" }, { "planeswalkerdeck", "Planeswalker Deck" }, { "starterdeck", "Starter Deck" },
				{ "judgegift", "Judge Gift" }, { "beginnerbox", "Beginner Box" }, { "japanshowcase", "Japan Showcase" },
				{ "firstplacefoil", "First Place Foil" }, { "bundle", "Bundle" }, { "buyabox", "Buy-a-Box" },
				{ "wizardsplaynetwork", "Wizards Play Network" }, { "playerrewards", "Player Rewards" },
				{ "gameday", "Game Day" }, { "arenaleague", "Arena League" }, { "stepandcompleat", "Step-and-Compleat" },
				{ "resale", "Resale" }, { "jpwalker", "Japanese Walker" }, { "release", "Release" },
				{ "convention", "Convention" }, { "dossier", "Dossier" }, { "schinesealtart", "Chinese Alt Art" },
				{ "storechampionship", "Store Championship" }, { "premiereshop", "Premiere Shop" },
				{ "setextension", "Set Extension" }, { "event", "Event" }, { "intropack", "Intro Pack" },
				{ "standardshowdown", "Standard Showdown" }, { "vault", "Vault" }, { "themepack", "Theme Pack" },
				{ "playpromo", "Play Promo" }, { "godzillaseries", "Godzilla Series" },
				{ "draculaseries", "Dracula Series" }, { "headliner", "Headliner" }, { "giftbox", "Gift Box" },
				{ "concept", "Concept" }, { "commanderparty", "Commander Party" }, { "duels", "Duels of the Planeswalkers" },
				{ "openhouse", "Open House" }, { "draftweekend", "Draft Weekend" }, { "alchemy", "Alchemy" },
				{ "bringafriend", "Bring a Friend" }, { "commanderpromo", "Commander Promo" }, { "league", "League" },
				{ "brawldeck", "Brawl Deck" }, { "universesbeyond", "Universes Beyond" },
				// foils and surfaces
				{ "surgefoil", "Surge Foil" }, { "galaxyfoil", "Galaxy Foil" }, { "silverfoil", "Silver Foil" },
				{ "ripplefoil", "Ripple Foil" }, { "doublerainbow", "Double Rainbow Foil" },
				{ "rainbowfoil", "Rainbow Foil" }, { "halofoil", "Halo Foil" }, { "raisedfoil", "Raised Foil" },
				{ "fracturefoil", "Fracture Foil" }, { "manafoil", "Mana Foil" }, { "confettifoil", "Confetti Foil" },
				{ "neonink", "Neon Ink" }, { "oilslick", "Oil Slick Foil" }, { "chocobotrackfoil", "Chocobo Track Foil" },
				{ "facetfoil", "Facet Foil" }, { "dazzlefoil", "Dazzle Foil" }, { "dragonscalefoil", "Dragonscale Foil" },
				{ "cosmicfoil", "Cosmic Foil" }, { "singularityfoil", "Singularity Foil" }, { "scroll", "Scroll" },
				{ "silverscroll", "Silver Scroll" }, { "embossed", "Embossed" }, { "thick", "Thick" },
				{ "textured", "Textured" }, { "gilded", "Gilded" }, { "gleaminggold", "Gleaming Gold" },
				{ "magnified", "Magnified" }, { "plastic", "Plastic" }, { "metal", "Metal" }, { "glossy", "Glossy" },
				{ "invisibleink", "Invisible Ink" }, { "doubleexposure", "Double Exposure" },
				{ "moonlitland", "Moonlit Land" }, { "upsidedown", "Upside Down" },
				{ "upsidedownback", "Upside-Down Back" } };
		for (String[] e : p)
			PROMO.put(e[0], e[1]);
	}
	private static final Pattern FINAL_FANTASY = Pattern.compile("ff([ivxl]+)");

	/**
	 * The label of one of Scryfall's {@code promo_types} - how a printing was handed out or what
	 * surface it has ("prerelease" gives "Prerelease", "surgefoil" gives "Surge Foil"). A type this
	 * does not know yet is shown capitalized, so a new one still appears.
	 *
	 * @return {@code null} for "boosterfun": it only marks the special treatments of booster
	 *         cards, which the other traits (Showcase, Borderless...) say better
	 */
	public static String promoLabel(String promoType) {
		if (promoType == null || promoType.isEmpty() || "boosterfun".equals(promoType))
			return null;
		String known = PROMO.get(promoType);
		if (known != null)
			return known;
		java.util.regex.Matcher ff = FINAL_FANTASY.matcher(promoType);
		if (ff.matches())
			return "Final Fantasy " + ff.group(1).toUpperCase(Locale.ROOT);
		return capitalize(promoType);
	}

	/** Every label the application can show, in the order they are listed (for suggestions and search). */
	private static final List<String> KNOWN;
	static {
		List<String> k = new ArrayList<String>(Arrays.asList(RESERVED_LIST, GAME_CHANGER, REPRINT, "1993 Frame",
				"1997 Frame", "2003 Frame", "Future Frame", "White Border", "Silver Border", "Gold Border", "Borderless",
				"Showcase", "Extended Art", "Inverted Frame", "Tombstone Frame", "Colorshifted", "Shattered Glass",
				"Full Art", "Textless", "Story Spotlight", "Oval Stamp", "Acorn Stamp", "Triangle Stamp",
				"Circle Stamp", "Arena Stamp", "Heart Stamp", "Variation", "Oversized", "Content Warning"));
		k.addAll(PROMO.values());
		KNOWN = Collections.unmodifiableList(k);
	}

	private CardTraits() {
	}

	/** The labels of the three own-field facts that are true, in a fixed order. */
	public static List<String> labels(boolean reserved, boolean gameChanger, boolean reprint) {
		List<String> res = new ArrayList<String>();
		if (reserved)
			res.add(RESERVED_LIST);
		if (gameChanger)
			res.add(GAME_CHANGER);
		if (reprint)
			res.add(REPRINT);
		return res;
	}

	/** @return every label of a card: the own-field facts, then the stored ones (treatments, size, watermark...) */
	public static List<String> allLabels(IMagicCard card) {
		List<String> res = new ArrayList<String>();
		if (card == null)
			return res;
		res.addAll(labels(isTrue(card.get(MagicCardField.RESERVED)), isTrue(card.get(MagicCardField.GAME_CHANGER)),
				isTrue(card.get(MagicCardField.REPRINT))));
		res.addAll(parseStored(card.get(MagicCardField.EXTRA_TRAITS)));
		return res;
	}

	/** @return {@code "Reserved List, Showcase"} for a card, or {@code null} when it has none of them */
	public static String describe(IMagicCard card) {
		List<String> l = allLabels(card);
		return l.isEmpty() ? null : join(l);
	}

	/** @param stored a {@link MagicCardField#EXTRA_TRAITS} value */
	static List<String> parseStored(Object stored) {
		List<String> res = new ArrayList<String>();
		if (stored == null || stored.toString().isEmpty())
			return res;
		for (String s : stored.toString().split(Pattern.quote(String.valueOf(EXTRA_SEPARATOR)))) {
			if (!s.trim().isEmpty())
				res.add(s.trim());
		}
		return res;
	}

	public static String join(List<String> labels) {
		StringBuilder sb = new StringBuilder();
		for (String s : labels) {
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(s);
		}
		return sb.toString();
	}

	/** @return {@code labels} the way {@link MagicCardField#EXTRA_TRAITS} stores them; {@code null} when empty */
	public static String toStored(List<String> labels) {
		if (labels == null || labels.isEmpty())
			return null;
		StringBuilder sb = new StringBuilder();
		for (String s : labels) {
			if (sb.length() > 0)
				sb.append(EXTRA_SEPARATOR);
			sb.append(s);
		}
		return sb.toString();
	}

	/** What to suggest after a "[" in the Text field: every label, plus the "First Printing" search. */
	public static List<String> suggestions() {
		List<String> res = new ArrayList<String>(KNOWN);
		res.add("First Printing");
		return res;
	}

	/**
	 * @param typed what was typed in brackets, any case ("showcase", "extended art", "watermark: selesnya")
	 * @return the label it names, spelled as the card shows it, or {@code null} if it is not a trait
	 */
	public static String canonicalLabel(String typed) {
		if (typed == null)
			return null;
		String t = typed.trim().replaceAll("\\s+", " ");
		for (String k : KNOWN) {
			if (k.equalsIgnoreCase(t))
				return k;
		}
		String lower = t.toLowerCase(Locale.ROOT);
		if (lower.startsWith("watermark:") && lower.length() > "watermark:".length()) {
			return WATERMARK_PREFIX + capitalize(t.substring("watermark:".length()).trim());
		}
		return null;
	}

	/** A pattern that matches a card's {@link MagicCardField#TRAITS} text when it lists {@code label}. */
	public static Pattern labelPattern(String label) {
		return Pattern.compile("(^|, )" + Pattern.quote(label) + "(,|$)", Pattern.CASE_INSENSITIVE);
	}

	public static String capitalize(String s) {
		if (s == null || s.isEmpty())
			return s;
		return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
	}

	private static boolean isTrue(Object v) {
		return "true".equals(v);
	}
}
