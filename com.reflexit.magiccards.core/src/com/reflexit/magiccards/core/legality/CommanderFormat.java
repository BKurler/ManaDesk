/*
 * Contributors:
 *     Rémi Dutil (2026) - generalized to a configurable main-deck/"commander
 *                         zone" size instead of the hardcoded 99+1, so the
 *                         same shape (singleton, N main + M commander-zone
 *                         cards in the sideboard slot) covers every real
 *                         commander-family format: Commander itself (99+1),
 *                         Duel Commander / Pauper Commander / PreDH (also
 *                         99+1, different legal card pool - handled entirely
 *                         by Scryfall's own per-format legality, nothing
 *                         extra needed here), Brawl / Standard Brawl (59+1 =
 *                         60 total), and Oathbreaker (58 main + 2: the
 *                         Oathbreaker planeswalker and its signature spell)
 *     Rémi Dutil (2026) - color identity rule: every main-deck card's color
 *                         identity (Scryfall's, strict) must fit within the
 *                         commander zone's (validateColorIdentity); for
 *                         Oathbreaker the planeswalker sets the colors and the
 *                         signature spell must fit them too. The extended
 *                         identity is a search aid only, never used here.
 *     Rémi Dutil (2026) - checkColorIdentity(): the same rule card by card
 *                         (IdentityCheck: allowed colors + cards outside), for
 *                         the deck Legality tab.
 */
package com.reflexit.magiccards.core.legality;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.utils.CardStoreUtils;

/**
 * Singleton main deck + a small fixed-size "commander zone" (the deck's
 * sideboard slot): Commander/Duel Commander/Pauper Commander/PreDH (99 + 1),
 * Brawl/Standard Brawl (59 + 1), Oathbreaker (58 + 2).
 */
public class CommanderFormat extends Format {
	private final int mainDeckCount;
	private final int commanderZoneCount;

	public CommanderFormat() {
		this("Commander", 4, 99, 1);
	}

	/** 99 main + 1 commander-zone card, like Commander itself - for formats
	 *  that only differ from it in legal card pool (Duel Commander, Pauper
	 *  Commander, PreDH), which Scryfall's own per-format legality already
	 *  accounts for. */
	public CommanderFormat(String name, int ord) {
		this(name, ord, 99, 1);
	}

	public CommanderFormat(String name, int ord, int mainDeckCount, int commanderZoneCount) {
		super(name, ord);
		this.mainDeckCount = mainDeckCount;
		this.commanderZoneCount = commanderZoneCount;
	}

	@Override
	public int getMainDeckCount() {
		return mainDeckCount;
	}

	@Override
	public int getSideboardCount() {
		return commanderZoneCount;
	}

	@Override
	public String validateDeckCount(int count) {
		if (count == mainDeckCount)
			return null;
		return "Deck card count is " + count + " expected to be exactly " + mainDeckCount
				+ (commanderZoneCount == 1 ? ". Commander should be in sideboard."
						: ". Commander zone cards should be in sideboard.");
	}

	@Override
	public String validateSideboardCount(int count) {
		if (count == commanderZoneCount)
			return null;
		return "Sideboard should contain exactly " + commanderZoneCount + " commander zone card"
				+ (commanderZoneCount == 1 ? "" : "s");
	}

	/**
	 * The counts and card pool first ({@link Format#validateLegality}), then the
	 * color identity of the main deck against the commander zone (the deck's
	 * sideboard). No commander zone: the count rule already reports it.
	 */
	@Override
	public String validateLegality(ICardStore<IMagicCard> store, CardStoreUtils.CardStats stats) {
		String err = super.validateLegality(store, stats);
		if (err != null || store == null || store.getLocation() == null)
			return err;
		Location loc = store.getLocation();
		ICardStore<IMagicCard> main = DataManager.getInstance().getCardStore(loc.toMainDeck());
		ICardStore<IMagicCard> zone = DataManager.getInstance().getCardStore(loc.toSideboard());
		if (zone == null)
			return null;
		return validateColorIdentity(zone, main != null ? main : store);
	}

	/**
	 * The color identity rule: the commander zone sets the deck's colors (the
	 * union of its cards' identities; for Oathbreaker, of its planeswalker(s)
	 * only - the signature spell must then fit too), and every main-deck card's
	 * identity must fit within them. Uses Scryfall's strict identity
	 * ({@link MagicCardField#COLOR_IDENTITY}); a card without one (never
	 * synced) is not checked, and without any known commander identity
	 * nothing is.
	 *
	 * @return {@code null} when legal, else an error naming the cards outside
	 *         the commander's colors
	 */
	public static String validateColorIdentity(Iterable<? extends IMagicCard> commanderZone,
			Iterable<? extends IMagicCard> mainDeck) {
		IdentityCheck check = checkColorIdentity(commanderZone, mainDeck);
		if (check == null || check.outside.isEmpty())
			return null;
		List<String> outside = new ArrayList<>();
		for (IMagicCard c : check.outside)
			if (!outside.contains(c.getName()))
				outside.add(c.getName());
		Set<Character> allowed = check.allowed;
		StringBuilder names = new StringBuilder();
		int shown = Math.min(outside.size(), 5);
		for (int i = 0; i < shown; i++) {
			if (i > 0)
				names.append(", ");
			names.append(outside.get(i));
		}
		if (outside.size() > shown)
			names.append(" and ").append(outside.size() - shown).append(" more");
		return "Color identity: " + names + (outside.size() == 1 ? " is" : " are")
				+ " outside the commander's colors (" + colorNames(allowed) + ")";
	}

	/** The result of the color identity rule: the commander's colors and the cards outside them. */
	public static final class IdentityCheck {
		/** The colors the commander zone allows (empty = colorless). */
		public final Set<Character> allowed;
		/** The cards (main deck, or a non-leading commander-zone card) outside those colors. */
		public final List<IMagicCard> outside;

		IdentityCheck(Set<Character> allowed, List<IMagicCard> outside) {
			this.allowed = allowed;
			this.outside = outside;
		}

		/** "White, Blue" / "colorless". */
		public String colorNames() {
			return CommanderFormat.colorNames(allowed);
		}
	}

	/**
	 * The color identity rule, card by card (see {@link #validateColorIdentity}).
	 *
	 * @return {@code null} when no commander identity is known (nothing to
	 *         check against), else the allowed colors and the cards outside
	 */
	public static IdentityCheck checkColorIdentity(Iterable<? extends IMagicCard> commanderZone,
			Iterable<? extends IMagicCard> mainDeck) {
		List<IMagicCard> zone = new ArrayList<>();
		for (IMagicCard c : commanderZone)
			zone.add(c);
		List<IMagicCard> leaders = new ArrayList<>();
		for (IMagicCard c : zone)
			if (isPlaneswalker(c))
				leaders.add(c);
		// Oathbreaker: the planeswalker leads, when the zone also holds other cards
		if (leaders.isEmpty() || leaders.size() == zone.size())
			leaders = zone;
		Set<Character> allowed = new LinkedHashSet<>();
		boolean known = false;
		for (IMagicCard c : leaders) {
			Set<Character> id = identityOf(c);
			if (id != null) {
				known = true;
				allowed.addAll(id);
			}
		}
		if (!known)
			return null; // no commander identity known: nothing to check against
		List<IMagicCard> outside = new ArrayList<>();
		for (IMagicCard c : zone)
			if (!leaders.contains(c) && !fits(c, allowed))
				outside.add(c);
		for (IMagicCard c : mainDeck)
			if (!fits(c, allowed))
				outside.add(c);
		return new IdentityCheck(allowed, outside);
	}

	private static final Pattern COLOR = Pattern.compile("\\{([WUBRG])\\}");

	/** The card's strict identity as color letters (empty = colorless), {@code null} if unknown. */
	static Set<Character> identityOf(IMagicCard card) {
		Object v = card.get(MagicCardField.COLOR_IDENTITY);
		if (v == null || v.toString().isEmpty())
			return null;
		Set<Character> colors = new LinkedHashSet<>();
		Matcher m = COLOR.matcher(v.toString());
		while (m.find())
			colors.add(m.group(1).charAt(0));
		return colors;
	}

	private static boolean fits(IMagicCard card, Set<Character> allowed) {
		Set<Character> id = identityOf(card);
		return id == null || allowed.containsAll(id);
	}

	private static boolean isPlaneswalker(IMagicCard card) {
		String type = card.getType();
		return type != null && type.contains("Planeswalker");
	}

	private static String colorNames(Set<Character> colors) {
		if (colors.isEmpty())
			return "colorless";
		StringBuilder sb = new StringBuilder();
		for (char c : new char[] { 'W', 'U', 'B', 'R', 'G' }) {
			if (!colors.contains(c))
				continue;
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(c == 'W' ? "White" : c == 'U' ? "Blue" : c == 'B' ? "Black" : c == 'R' ? "Red" : "Green");
		}
		return sb.toString();
	}

	@Override
	public String validateCardCount(int count) {
		if (count <= 1)
			return null;
		return "Singleton. Only one card with this name allowed";
	}
}
