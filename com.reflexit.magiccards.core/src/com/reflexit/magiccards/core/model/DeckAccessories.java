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
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.reflexit.magiccards.core.model.abs.ICardCountable;
import com.reflexit.magiccards.core.model.storage.IDbCardStore;

/**
 * Turns the compact {@code ACCESSORIES} string stored on each card (see
 * {@code AccessoryExtractor}) into a de-duplicated, per-deck list of the physical
 * things the deck needs at the table: token and emblem cards, counters, dice and
 * status markers - each with the deck cards that call for it.
 *
 * <p>
 * The encoding, one entry per {@code ;}-separated chunk:
 *
 * <pre>
 *   t&lt;scryfall-id&gt;   token or emblem card
 *   c&lt;name&gt;          counter        e.g. c+1/+1, cstun, cloyalty, cenergy
 *   d&lt;die&gt;           die / coin     e.g. dd20, dd6, dcoin
 *   m&lt;name&gt;          status marker  e.g. mmonarch, mring, mdaynight
 * </pre>
 *
 * <p>
 * Tokens and emblems are de-duplicated by <em>identity</em> (name + type + P/T +
 * rules text): five deck cards that each make "their" 1/1 white Soldier collapse
 * to one entry. Two genuinely different tokens - a 4/4 Angel and a 1/1 flying
 * Angel - stay separate. Counters / dice / markers de-duplicate by their code.
 */
public final class DeckAccessories {

	public enum Kind {
		/** a token card */
		TOKEN,
		/** an emblem card */
		EMBLEM,
		/** a player-wide status represented by a real helper card: monarch, initiative, the ring... */
		PLAYER_MARKER,
		/** an accumulating counter, usually tracked with a die: +1/+1, lore, charge, loyalty... */
		COUNTER,
		/** an on/off counter, tracked with a marker not a die: stun, shield, flying, first strike... */
		KEYWORD,
		/** a die or a coin flip */
		DIE
	}

	/** One decoded entry, before any deck aggregation. */
	public static final class Ref {
		public final Kind kind;
		/** payload after the type letter: a scryfall id, {@code +1/+1}, {@code d20}, {@code monarch}... */
		public final String payload;
		/** the original chunk, {@code t<id>} / {@code c+1/+1} ... */
		public final String raw;

		Ref(Kind kind, String payload, String raw) {
			this.kind = kind;
			this.payload = payload;
			this.raw = raw;
		}
	}

	/** One thing the deck needs, with the cards that need it. */
	public static final class Need {
		public final Kind kind;
		/** stable identifier for this entry within a {@link Result} (for UI selection). */
		public final String key;
		public String label;
		/** a card to show the picture of, or {@code null} when the UI should draw a badge. */
		public IMagicCard card;
		/** the distinct deck cards that call for this accessory. */
		public final List<IMagicCard> sources = new ArrayList<>();
		/** sum of the copies of those deck cards. */
		public int copies;

		Need(Kind kind, String key) {
			this.kind = kind;
			this.key = key;
		}

		/** number of distinct deck cards that need it. */
		public int getDeckCards() {
			return sources.size();
		}

		void addSource(IMagicCard card, int copies) {
			for (IMagicCard s : sources)
				if (eq(s.getName(), card.getName()))
					return; // same card, another printing - count it once
			sources.add(card);
			this.copies += copies;
		}
	}

	public static final class Result {
		public final List<Need> tokens = new ArrayList<>();
		public final List<Need> emblems = new ArrayList<>();
		/** monarch, the initiative, the ring... - real helper cards. */
		public final List<Need> playerMarkers = new ArrayList<>();
		/** accumulating counters, usually tracked with a die. */
		public final List<Need> counters = new ArrayList<>();
		/** on/off counters, tracked with a marker: stun, shield, flying... */
		public final List<Need> keywords = new ArrayList<>();
		/** dice and coin flips. */
		public final List<Need> dice = new ArrayList<>();

		public boolean isEmpty() {
			return all().isEmpty();
		}

		/** every entry, in display order. */
		public List<Need> all() {
			List<Need> l = new ArrayList<>();
			l.addAll(tokens);
			l.addAll(emblems);
			l.addAll(playerMarkers);
			l.addAll(counters);
			l.addAll(keywords);
			l.addAll(dice);
			return l;
		}

		/** @return the entry with this {@link Need#key}, or {@code null}. */
		public Need find(String key) {
			for (Need n : all())
				if (n.key.equals(key))
					return n;
			return null;
		}

		void add(Need n) {
			switch (n.kind) {
			case TOKEN:
				tokens.add(n);
				break;
			case EMBLEM:
				emblems.add(n);
				break;
			case PLAYER_MARKER:
				playerMarkers.add(n);
				break;
			case COUNTER:
				counters.add(n);
				break;
			case KEYWORD:
				keywords.add(n);
				break;
			case DIE:
				dice.add(n);
				break;
			}
		}
	}

	/** payload -> a real helper card that publicly represents a marker / player counter. */
	private static final Map<String, String> HELPER_CARDS = new LinkedHashMap<>();
	static {
		HELPER_CARDS.put("monarch", "The Monarch");
		HELPER_CARDS.put("initiative", "The Initiative");
		HELPER_CARDS.put("ring", "The Ring");
		HELPER_CARDS.put("daynight", "Day // Night");
		HELPER_CARDS.put("citysblessing", "City's Blessing");
		HELPER_CARDS.put("energy", "Energy Reserve");
		HELPER_CARDS.put("experience", "Experience");
		HELPER_CARDS.put("poison", "Poison Counter");
	}

	/** counters that track a player rather than a permanent - shown under Player Markers. */
	private static final Map<String, String> PLAYER_COUNTERS = new LinkedHashMap<>();
	static {
		PLAYER_COUNTERS.put("energy", "Energy");
		PLAYER_COUNTERS.put("experience", "Experience");
		PLAYER_COUNTERS.put("poison", "Poison");
	}

	private DeckAccessories() {
	}

	/**
	 * Decode one stored {@code ACCESSORIES} string - no database needed.
	 * Counter codes are re-validated against {@link CounterTypes} here too, so a
	 * stale value written by an older extractor ({@code chad}, {@code cinstead})
	 * is dropped on read even before the database is re-split.
	 */
	public static List<Ref> decode(String enc) {
		List<Ref> out = new ArrayList<>();
		if (enc == null)
			return out;
		for (String chunk : enc.split(";")) {
			chunk = chunk.trim();
			if (chunk.length() < 2)
				continue;
			char t = chunk.charAt(0);
			String payload = chunk.substring(1);
			Kind kind;
			switch (t) {
			case 't':
				kind = Kind.TOKEN; // TOKEN vs EMBLEM is settled later from the resolved card
				break;
			case 'c':
				if (!CounterTypes.isCounter(payload))
					continue; // stale / bogus counter code
				kind = Kind.COUNTER;
				break;
			case 'd':
				kind = Kind.DIE;
				break;
			case 'm':
				kind = Kind.PLAYER_MARKER;
				break;
			default:
				continue;
			}
			out.add(new Ref(kind, payload, chunk));
		}
		return out;
	}

	/**
	 * The accessory refs a deck card needs. Token / emblem ids come from the
	 * stored {@code ACCESSORIES} value (they need Scryfall {@code all_parts}, which
	 * isn't in the card). Counters / dice / markers are re-derived <em>live</em>
	 * from the card's own rules text, so a stale or wrong stored value (an old
	 * "cstrike", a face-fusion "ctrample") can never leak into the view.
	 */
	private static List<Ref> refsFor(IMagicCard c, IDbCardStore<IMagicCard> db) {
		List<Ref> out = new ArrayList<>();
		String enc = c.getAccessories();
		if (enc != null && !enc.isEmpty())
			for (Ref r : decode(enc))
				if (r.kind == Kind.TOKEN)
					out.add(r);
		java.util.LinkedHashSet<String> live = new java.util.LinkedHashSet<>();
		com.reflexit.magiccards.core.sync.AccessoryExtractor.fromText(live,
				fullOracle(c, db).toLowerCase(Locale.ROOT), nz(c.getType()).toLowerCase(Locale.ROOT));
		for (String code : live)
			out.addAll(decode(code));
		return out;
	}

	private static String fullOracle(IMagicCard c, IDbCardStore<IMagicCard> db) {
		String o = nz(c.getOracleText());
		MagicCard mc = c instanceof MagicCard ? (MagicCard) c
				: c instanceof MagicCardPhysical ? ((MagicCardPhysical) c).getCard() : null;
		if (mc != null && db != null) {
			String flip = mc.getFlipId();
			if (flip != null && !flip.isEmpty()) {
				IMagicCard back = db.getCard(flip);
				if (back != null)
					o = o + " . " + nz(back.getOracleText());
			}
		}
		return o;
	}

	/**
	 * @param deck the deck's cards (main + sideboard already merged by the caller);
	 *             {@link ICardCountable} copies are respected
	 * @param db   the card database, for resolving token ids and helper cards
	 */
	public static Result compute(Collection<? extends IMagicCard> deck, IDbCardStore<IMagicCard> db) {
		Map<String, Need> byKey = new LinkedHashMap<>();
		if (deck != null) {
			for (IMagicCard c : deck) {
				if (c == null || isAccessoryCard(c))
					continue; // a token / helper card in the deck must not point at itself
				int copies = (c instanceof ICardCountable) ? Math.max(1, ((ICardCountable) c).getCount()) : 1;
				for (Ref ref : refsFor(c, db)) {
					Need seed = resolve(ref, db);
					Need need = byKey.get(seed.key);
					if (need == null) {
						need = seed;
						byKey.put(need.key, need);
					}
					need.addSource(c, copies);
				}
			}
		}
		// second pass: pick the printing that best matches the card that needs it
		for (Need n : byKey.values()) {
			if (n.card == null || n.sources.isEmpty())
				continue;
			String srcSet = n.sources.get(0).getSet();
			if (n.kind == Kind.PLAYER_MARKER) {
				IMagicCard better = bestPrinting(n.card.getName(), srcSet, db);
				if (better != null)
					n.card = better;
			} else if (n.kind == Kind.TOKEN || n.kind == Kind.EMBLEM) {
				IMagicCard better = tokenFromSet(n.card, srcSet, db);
				if (better != null)
					n.card = better;
			}
		}
		Result r = new Result();
		for (Need n : byKey.values())
			r.add(n);
		sort(r.tokens);
		sort(r.emblems);
		sort(r.playerMarkers);
		sort(r.counters);
		sort(r.keywords);
		sort(r.dice);
		return r;
	}

	/**
	 * The English printing of {@code name} to show a picture of: preferring the
	 * one from {@code preferSet} (the card that needs it), then the newest. A
	 * non-English printing is never returned - the Scryfall bulk carries Japanese
	 * promos of cards like "The Monarch" and {@code getPrime} may land on one.
	 */
	private static IMagicCard bestPrinting(String name, String preferSet, IDbCardStore<IMagicCard> db) {
		if (db == null || name == null)
			return null;
		Collection<IMagicCard> cands = db.getCandidates(name);
		if (cands == null || cands.isEmpty()) {
			IMagicCard p = db.getPrime(name);
			return isEnglish(p) ? p : null;
		}
		IMagicCard best = null;
		for (IMagicCard c : cands) {
			if (!isEnglish(c))
				continue;
			if (preferSet != null && preferSet.equals(c.getSet()))
				return c;
			if (best == null || after(releaseDate(c), releaseDate(best)))
				best = c;
		}
		return best;
	}

	private static boolean isEnglish(IMagicCard c) {
		if (c == null)
			return false;
		String l = c.getLanguage();
		return l == null || l.isEmpty() || l.equalsIgnoreCase("en") || l.equalsIgnoreCase("english");
	}

	/**
	 * A same-identity English printing of the token whose set is the token set for
	 * {@code srcSet} (Scryfall's {@code all_parts} often links a card to a token
	 * from a dedicated token set rather than one from the card's own set). Falls
	 * back to {@code tok} when no such printing exists.
	 */
	private static IMagicCard tokenFromSet(IMagicCard tok, String srcSet, IDbCardStore<IMagicCard> db) {
		if (db == null || tok == null || srcSet == null || srcSet.isEmpty() || matchesSet(tok, srcSet))
			return tok;
		String id = identity(tok);
		Collection<IMagicCard> cands = db.getCandidates(tok.getName());
		if (cands != null)
			for (IMagicCard c : cands)
				if (isEnglish(c) && matchesSet(c, srcSet) && identity(c).equals(id))
					return c;
		return tok;
	}

	/** e.g. token set "Commander Anthology Tokens" belongs to card set "Commander Anthology". */
	private static boolean matchesSet(IMagicCard c, String srcSet) {
		String s = c.getSet();
		return s != null && (s.equals(srcSet) || s.equals(srcSet + " Tokens"));
	}

	/** The English printing with the same identity as {@code tok}, if {@code tok} itself isn't English. */
	private static IMagicCard englishToken(IMagicCard tok, IDbCardStore<IMagicCard> db) {
		if (db == null || isEnglish(tok))
			return tok;
		Collection<IMagicCard> cands = db.getCandidates(tok.getName());
		if (cands != null) {
			String id = identity(tok);
			for (IMagicCard c : cands)
				if (isEnglish(c) && identity(c).equals(id))
					return c;
		}
		return tok;
	}

	private static java.util.Date releaseDate(IMagicCard c) {
		Edition ed = c == null ? null : c.getEdition();
		return ed == null ? null : ed.getReleaseDate();
	}

	private static boolean after(java.util.Date a, java.util.Date b) {
		return b == null ? a != null : (a != null && a.after(b));
	}

	private static void sort(List<Need> l) {
		l.sort(Comparator.comparing((Need n) -> n.label == null ? "" : n.label.toLowerCase(Locale.ROOT)));
	}

	private static Need resolve(Ref ref, IDbCardStore<IMagicCard> db) {
		switch (ref.kind) {
		case TOKEN: {
			IMagicCard tok = db == null ? null : db.getCard(ref.payload);
			if (tok != null) {
				tok = englishToken(tok, db);
				boolean emblem = String.valueOf(tok.getType()).toLowerCase(Locale.ROOT).contains("emblem");
				// key on identity, not the printing id, so the same token from
				// different sets collapses to one entry.
				Need n = new Need(emblem ? Kind.EMBLEM : Kind.TOKEN, "tok:" + identity(tok));
				n.label = nz(tok.getName()).isEmpty() ? "Token" : tok.getName();
				n.card = tok; // the printing from this card's own set (Scryfall all_parts)
				return n;
			}
			Need n = new Need(Kind.TOKEN, ref.raw);
			n.label = "Token";
			return n;
		}
		case COUNTER: {
			if (CounterTypes.isKeyword(ref.payload)) {
				Need n = new Need(Kind.KEYWORD, ref.raw);
				n.label = titleCase(ref.payload) + " counter";
				return n;
			}
			if (PLAYER_COUNTERS.containsKey(ref.payload)) {
				// energy / experience / poison track the player - show with Player Markers
				Need n = new Need(Kind.PLAYER_MARKER, ref.raw);
				n.label = PLAYER_COUNTERS.get(ref.payload);
				n.card = helperCard(ref.payload, db);
				return n;
			}
			Need n = new Need(Kind.COUNTER, ref.raw);
			n.label = counterLabel(ref.payload);
			n.card = helperCard(ref.payload, db);
			return n;
		}
		case DIE: {
			Need n = new Need(Kind.DIE, ref.raw);
			n.label = ref.payload.equals("coin") ? "Coin flip" : ref.payload; // dd20 -> "d20"
			return n;
		}
		case PLAYER_MARKER:
		default: {
			Need n = new Need(Kind.PLAYER_MARKER, ref.raw);
			n.label = markerLabel(ref.payload);
			n.card = helperCard(ref.payload, db);
			return n;
		}
		}
	}

	private static String titleCase(String s) {
		if (s == null || s.isEmpty())
			return "";
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/**
	 * A card that is itself an accessory - a token, an emblem, or one of the
	 * "Card"-type helper cards (The Monarch, City's Blessing, Experience, Poison
	 * Counter...). When one is in the deck list it must not generate accessories
	 * that point back at itself.
	 */
	private static boolean isAccessoryCard(IMagicCard c) {
		String t = String.valueOf(c.getType()).toLowerCase(Locale.ROOT).trim();
		return t.contains("token") || t.contains("emblem") || t.equals("card");
	}

	/** Ability keywords / restriction clauses that make two same-name same-P/T tokens genuinely different. */
	private static final String[] TOKEN_TRAITS = { "flying", "first strike", "double strike", "deathtouch", "defender",
			"haste", "hexproof", "indestructible", "lifelink", "menace", "reach", "trample", "vigilance", "ward",
			"shroud", "fear", "intimidate", "flanking", "horsemanship", "shadow", "skulk", "decayed", "changeling",
			"exalted", "annihilator", "can't block", "can't attack", "attacks each combat if able",
			"sacrifice this creature", "sacrifice this artifact", "sacrifice this land" };

	/**
	 * Identity of a token / emblem for de-duplication: its name, its P/T and which
	 * ability keywords / restriction clauses it has. Free rules text is <em>not</em>
	 * used - some token records in the DB carry a stray "Rulings" HTML link in
	 * their oracle text with the printing's own id in it, which would otherwise
	 * make every printing look unique. Two vanilla 4/4 Beasts collapse to one
	 * entry; a 3/3 deathtouch Beast stays separate from a 3/3 vanilla Beast.
	 */
	static String identity(IMagicCard c) {
		return nz(c.getName()).toLowerCase(Locale.ROOT) + "|" + nz(c.getPower()) + "/" + nz(c.getToughness()) + "|"
				+ tokenTraits(c.getOracleText());
	}

	static String tokenTraits(String oracle) {
		if (oracle == null || oracle.isEmpty())
			return "";
		String s = oracle.toLowerCase(Locale.ROOT).replaceAll("<[^>]*>", " ") // html tags
				.replaceAll("https?://\\S+", " ") // bare urls
				.replaceAll("\\([^)]*\\)", " "); // reminder text
		StringBuilder sb = new StringBuilder();
		for (String t : TOKEN_TRAITS)
			if (s.contains(t))
				sb.append(t).append(';');
		return sb.toString();
	}

	private static String counterLabel(String payload) {
		if (payload.equals("loyalty"))
			return "Loyalty counter";
		if (payload.equals("energy"))
			return "Energy";
		return payload + " counter";
	}

	private static String markerLabel(String payload) {
		switch (payload) {
		case "monarch":
			return "The Monarch";
		case "initiative":
			return "The Initiative";
		case "ring":
			return "The Ring";
		case "daynight":
			return "Day / Night";
		case "citysblessing":
			return "City's Blessing";
		default:
			return payload;
		}
	}

	private static IMagicCard helperCard(String payload, IDbCardStore<IMagicCard> db) {
		if (db == null)
			return null;
		String name = HELPER_CARDS.get(payload);
		if (name == null)
			return null;
		IMagicCard c = bestPrinting(name, null, db);
		if (c == null && name.contains(" // "))
			c = bestPrinting(name.substring(0, name.indexOf(" // ")), null, db);
		return c;
	}

	private static boolean eq(String a, String b) {
		return a == null ? b == null : a.equals(b);
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
