/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - COLOR/COLOR_IDENTITY/COLOR_IDENTITY_EXTENDED now
 *                         aggregate a Collector Name-group (several printings
 *                         of the same card) via ColorUnionAggregator, which
 *                         concatenates every printing's own cost-string
 *                         instead of colliding to "*" on any disagreement -
 *                         getColorName() already only looks at which W/U/B/
 *                         R/G symbols are PRESENT, not how many times, so no
 *                         change was needed here: it naturally reports the
 *                         union, e.g. "White-Blue" for a group mixing a
 *                         white printing and a blue one
 *     Rémi Dutil (2026) - getColorPresense(): removed the "oracle.charAt(0)
 *                         != '{'" gate around the land-type-name-to-color
 *                         substitution (e.g. "Plains or Island card" -> W/U)
 *                         - it was meant to skip this for a plain mana-cost
 *                         string like "{2}{W}{U}" (getColorAsCost()'s own
 *                         caller), but real oracle text for an activated
 *                         ability commonly starts with its own tap/mana
 *                         symbol too - every fetch land, for example
 *                         ("{T}, Pay 1 life, Sacrifice ...: Search your
 *                         library for a Plains or Island card..."). That
 *                         made Extended Color Identity report Costless for
 *                         Flooded Strand and every other fetch/tap land
 *                         instead of the colors of the basic land types they
 *                         search for. A plain cost string never contains
 *                         these literal phrases, so running the substitution
 *                         unconditionally is harmless for that caller too.
 *     Rémi Dutil (2026) - getFetchableColors() / getReminderTextColors() /
 *                         getExtendedIdentityCost(): the Extended Color Identity is now
 *                         Scryfall's color identity plus the colors the card can fetch and
 *                         those of the symbols in its reminder text, not the guess above
 */

package com.reflexit.magiccards.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.reflexit.magiccards.core.MagicLogger;

public class Colors implements ISearchableProperty {
	static Colors instance = new Colors();

	private static final ISearchableProperty WITHOUT_COLORLESS = new ColorsWithoutColorless();

	public static ISearchableProperty getInstanceWithoutColorless() {
		return WITHOUT_COLORLESS;
	}

	private LinkedHashMap<String, ManaColor> idmap;
	private HashMap<String, ManaColor> namemap;
	// Rework the color processing to include Colorless card
	// Note: The text must be process before this pattern is used to replace colorless with C letter
	private final static Pattern colorpattern = Pattern.compile("\\x{7b}([WUBRGC\\d])\\x{7d}");

	public static enum ManaColor {
		WHITE("W"), BLUE("U"), BLACK("B"), RED("R"), GREEN("G"), COLORLESS("C");

		String tag;
		String label;

		ManaColor(String tag) {
			this.tag = tag;
			this.label = name().substring(0, 1) + name().substring(1).toLowerCase(Locale.ENGLISH);
		}

		public String tag() {
			return tag;
		}

		public String getLabel() {
			return label;
		}

		public String getPrefId() {
			return FilterField.getPrefConstant(FilterField.COLOR.toString(), getLabel());
		}

		static ManaColor valueOfTag(String tag) {
			for (ManaColor c : values()) {
				if (c.tag.equals(tag))
					return c;
			}
			return null;
		}
	};

	private Colors() {
		this.idmap = new LinkedHashMap<String, ManaColor>();
		this.namemap = new HashMap<String, ManaColor>();
		for (ManaColor c : ManaColor.values()) {
			idmap.put(c.getPrefId(), c);
			namemap.put(c.getLabel(), c);
		}
	}

	public Collection<String> getColorIdentity(IMagicCard card) {
		Collection<String> res = new LinkedHashSet<String>();
		try {
			getColorPresense(card.getCost(), res);
			getColorPresense(card.getOracleText(), res);
			getColorPresense(getEncodeByName((String) card.get(MagicCardField.COLOR_INDICATOR)), res);
		} catch (Exception e) {
			MagicLogger.log("Cannot get identity for card: " + e);
		}
		return res;
	}

	/** "{" followed by a generic/numeric mana symbol (0-9, X, Y, Z): becomes {C. Compiled
	 *  once - getColorPresense() runs per card for every group-row color aggregate. The
	 *  other substitutions there are plain literals (String.replace, no regex). */
	private static final Pattern COLORLESS_SYMBOL = Pattern.compile("\\x{7b}([\\dXYZ])++");

	public static Collection<String> getColorPresense(String text) {
		return getColorPresense(text, new LinkedHashSet<String>());
	}

	public String getColorAsCost(IMagicCard card) {
		String text = card.getCost();
		if (text == null || text.isEmpty())
			return "";
		return toCost(getColorPresense(text, new LinkedHashSet<String>()));
	}

	public String getColorIdentityAsCost(IMagicCard card) {
		return toCost(getColorIdentity(card));
	}

	private static final String[][] LAND_TYPE_COLORS = { { "Plains", "W" }, { "Island", "U" }, { "Swamp", "B" },
			{ "Mountain", "R" }, { "Forest", "G" } };
	private static final Pattern SEARCH_SENTENCE = Pattern.compile("[Ss]earch your library for[^.]*\\.");
	private static final Pattern REMINDER_TEXT = Pattern.compile("\\([^)]*\\)");
	private static final Pattern OFFICIAL_COLOR = Pattern.compile("\\{([WUBRG])\\}");

	/**
	 * The colors of the basic land types a card can <em>fetch</em>: the types named in its
	 * "Search your library for ..." sentences (a fetch land, Farseek...) or in a
	 * {@code Plainscycling}-style ability. A fetch land makes no mana itself, but it finds a
	 * land that does, so it "touches" those colors. Reminder text is ignored (as Scryfall
	 * does for the color identity); "a basic land card" with no type names no color.
	 *
	 * @return color tags (W, U, B, R, G), possibly empty; never {@code null}
	 */
	public static Collection<String> getFetchableColors(String oracleText) {
		Collection<String> res = new LinkedHashSet<String>();
		if (oracleText == null || oracleText.isEmpty())
			return res;
		String text = REMINDER_TEXT.matcher(oracleText).replaceAll(" ");
		java.util.regex.Matcher m = SEARCH_SENTENCE.matcher(text);
		while (m.find()) {
			for (String[] t : LAND_TYPE_COLORS) {
				if (m.group().contains(t[0]))
					res.add(t[1]);
			}
		}
		for (String[] t : LAND_TYPE_COLORS) {
			if (text.contains(t[0] + "cycling"))
				res.add(t[1]);
		}
		return res;
	}

	private static final Pattern MANA_SYMBOL = Pattern.compile("\\{([^}]+)\\}");

	/**
	 * The colors of the mana symbols written in the card's <em>reminder text</em> (Extort's
	 * {@code {W/B}}, Firebending's {@code {R}}, Trinisphere's example...). Scryfall's color identity
	 * ignores reminder text, but those symbols say which colors the card is about.
	 * Hybrid ({@code {W/B}}), Phyrexian ({@code {G/P}}) and two-brid ({@code {2/W}}) symbols
	 * count for their colors; generic, colorless, snow, tap... symbols for none.
	 *
	 * @return color tags (W, U, B, R, G), possibly empty; never {@code null}
	 */
	public static Collection<String> getReminderTextColors(String oracleText) {
		Collection<String> res = new LinkedHashSet<String>();
		if (oracleText == null || oracleText.isEmpty())
			return res;
		java.util.regex.Matcher reminder = REMINDER_TEXT.matcher(oracleText);
		while (reminder.find()) {
			java.util.regex.Matcher symbol = MANA_SYMBOL.matcher(reminder.group());
			while (symbol.find()) {
				for (String part : symbol.group(1).split("/")) {
					if (part.length() == 1 && "WUBRG".contains(part))
						res.add(part);
					else if (part.length() == 2 && part.charAt(0) == 'H' && "WUBRG".indexOf(part.charAt(1)) >= 0)
						res.add(part.substring(1)); // half mana: {HW}
				}
			}
		}
		return res;
	}

	/**
	 * The Extended Color Identity: Scryfall's own color identity, plus the colors the card can
	 * fetch ({@link #getFetchableColors}), plus the colors of the mana symbols in its reminder
	 * text ({@link #getReminderTextColors}) - everything the card is "about" in color, so the
	 * cards that really work in a given deck can be found. Computed when the card database is
	 * updated.
	 *
	 * @param officialIdentityCost Scryfall's color identity as a cost string ({@code "{W}{U}"},
	 *        {@code "{C}"} for colorless), or {@code null} if it is not known
	 * @return the extended identity as a cost string ({@code "{C}"} when it has no color), or
	 *         {@code null} when {@code officialIdentityCost} is {@code null} (caller falls back)
	 */
	public static String getExtendedIdentityCost(String officialIdentityCost, String oracleText) {
		if (officialIdentityCost == null)
			return null;
		Collection<String> tags = new LinkedHashSet<String>();
		java.util.regex.Matcher m = OFFICIAL_COLOR.matcher(officialIdentityCost);
		while (m.find())
			tags.add(m.group(1));
		tags.addAll(getFetchableColors(oracleText));
		tags.addAll(getReminderTextColors(oracleText));
		return tags.isEmpty() ? "{C}" : toCost(tags);
	}

	public static Collection<String> getColorPresense(String text, Collection<String> res) {
		if (text == null || text.length() == 0)
			return res;

		String oracle = text;

		// Before doing the color pattern check, replace all the hybrid with separate colors
		oracle = oracle.replace("/", "}{");

		// Before doing the color pattern check, replace all colorless with C
		oracle = COLORLESS_SYMBOL.matcher(oracle).replaceAll("{C");

		// Before doing the color pattern check, remove all the "Pay life" tag
		oracle = oracle.replace("P}", "}");

		// Replace all the land types with a equivalent mana color to allow the pattern to find it
		// (used to be gated on the text not starting with '{', meant to skip this for a plain
		// mana-cost string like "{2}{W}{U}" - but oracle text for an activated ability commonly
		// starts with its own tap/mana symbol too, e.g. every fetch land ("{T}, Pay 1 life,
		// Sacrifice ... Search your library for a Plains or Island card..."), so that gate was
		// skipping the substitution for exactly the land type of card it's meant to catch. A
		// plain cost string never contains these literal phrases anyway, so always running this
		// is harmless for that caller and fixes it for oracle text.
		oracle = oracle.replace(", or Island card", " card {U}");
		oracle = oracle.replace(", or Plains card", " card {W}");
		oracle = oracle.replace(", or Mountain card", " card {R}");
		oracle = oracle.replace(", or Swamp card", " card {B}");
		oracle = oracle.replace(", or Forest card", " card {G}");

		// Replace all the land types with a equivalent mana color to allow the pattern to find it
		oracle = oracle.replace("or Island card", "card {U}");
		oracle = oracle.replace("or Plains card", "card {W}");
		oracle = oracle.replace("or Mountain card", "card {R}");
		oracle = oracle.replace("or Swamp card", "card {B}");
		oracle = oracle.replace("or Forest card", "card {G}");

		// Replace all the land types with a equivalent mana color to allow the pattern to find it
		oracle = oracle.replace(", Island card", " card {U}");
		oracle = oracle.replace(", Plains card", " card {W}");
		oracle = oracle.replace(", Mountain card", " card {R}");
		oracle = oracle.replace(", Swamp card", " card {B}");
		oracle = oracle.replace(", Forest card", " card {G}");

		// Replace all the land types with a equivalent mana color to allow the pattern to find it
		oracle = oracle.replace("Island card", "card {U}");
		oracle = oracle.replace("Plains card", "card {W}");
		oracle = oracle.replace("Mountain card", "card {R}");
		oracle = oracle.replace("Swamp card", "card {B}");
		oracle = oracle.replace("Forest card", "card {G}");

		Matcher matcher = colorpattern.matcher(oracle);
		while (matcher.find()) {
			res.add(matcher.group(1));
		}
		return res;
	}

	public static String getColorName(String cost) {
		if (cost == null)
			return "Unknown";
		if (cost.length() == 0)
			return "Costless"; // RD This could be land, tokens, arts, etc
		Collection<String> colorIdentity = sortTags(getColorPresense(cost));
		StringBuffer buf = new StringBuffer();
		for (String c : colorIdentity) {
			String name = ManaColor.valueOfTag(c).getLabel();
			if (buf.length() > 0) {
				buf.append('-');
			}
			buf.append(name);
		}
		String res = buf.toString();
		if (res.length() == 0)
			return "Colorless";
		return res;
	}

	public static int getColorSort(String cost) {
		if (cost == null)
			return 0;
		if (cost.length() == 0)
			return 0;
		if (cost.equals("*"))
			return 0b111111;
		int sum = 1;
		int times = 0;
		Collection<String> colorIdentity = getColorPresense(cost);
		for (ManaColor c : ManaColor.values()) {
			sum <<= 1;
			if (colorIdentity.contains(c.tag())) {
				times++;
				sum |= 0b1;
			}
		}
		if (sum == 0)
			return 1;
		if (times == 1)
			sum <<= 7;
		return -sum;
	}

	@Override
	public String getIdPrefix() {
		return getFilterField().toString();
	}

	@Override
	public FilterField getFilterField() {
		return FilterField.COLOR;
	}

	public static Colors getInstance() {
		return instance;
	}

	public Collection<ManaColor> getAllColors() {
		return idmap.values();
	}

	public Collection<ManaColor> getColorsWithoutColorless() {
		return idmap.values().stream().filter(c -> c != ManaColor.COLORLESS).toList(); // Java 16+ immutable list
	}

	@Override
	public Collection<String> getIds() {
		return new ArrayList<String>(this.idmap.keySet());
	}

	public String getPrefConstant(String name) {
		return FilterField.getPrefConstant(getIdPrefix(), name);
	}

	@Override
	public String getNameById(String id) {
		return this.idmap.get(id).getLabel();
	}

	public String getEncodeByName(String r) {
		try {
			if (r == null || r.charAt(0) == '*')
				return "";
			if (r.contains(",")) {
				String colors[] = r.split(", *");
				String res = "";
				for (String ecolor : colors) {
					if (res.length() > 0)
						res += "/";
					res += namemap.get(ecolor).tag();
				}
				return res;
			}
			return this.namemap.get(r).tag();
		} catch (NullPointerException e) {
			MagicLogger.log("Unknown color: " + r);
			return "";
		}
	}

	public String getCostByName(String r) {
		try {
			if (r == null || r.charAt(0) == '*' | r.equals("Costless"))
				return "";
			String colors[] = r.split("-");
			String res = "";
			for (String ecolor : colors) {
				res += "{" + namemap.get(ecolor).tag() + "}";
			}
			return res;
		} catch (NullPointerException e) {
			MagicLogger.log("Unknown color: " + r);
			return "";
		}
	}

	public static String getColorType(String cost) {
		String colorType = "";

		if (cost == null || cost.length() == 0)
			return "costless"; // RD But could be a token, arts, etc, no just land

		Collection<String> colors = getColorPresense(cost);

		if (colors.size() == 1 && colors.contains("C")) {
			return "colorless";
		}

		// RD Now, remove C for the next steps because we have already check for colorless "only" cards
		colors.remove("C");
		int diff = colors.size();
		if (diff == 1) {
			colorType += "mono";
		} else {
			colorType += "multi";
		}

		// RD Check for hybrid card, append the hybrid keyboard, increasing the search capability
		if (cost.contains("/") || cost.contains("P"))
			colorType += "-hybrid";

		return colorType;
	}

	public int getConvertedManaCost(String cost) {
		if (cost == null || cost.length() == 0)
			return 0;
		String[] manas = manasplit(cost);
		int res = 0;
		for (String x : manas) {
			if (x.equals("X") || x.equals("Y") || x.equals("Z")) {
				res += 0;
				continue;
			}
			if (Character.isDigit(x.charAt(0))) {
				try {
					int a = x.indexOf('/');
					if (a > 0) {
						res += Integer.parseInt(x.substring(0, a));
						continue;
					}
					res += Integer.parseInt(x);
					continue;
				} catch (NumberFormatException e) {
					// ignore
				}
			}
			res++;
		}
		return res;
	}

	public static String[] manasplit(String cost) {
		if (cost.contains(","))
			return new String[] { "1000000" };
		int k = 0;
		for (int i = 0; i < cost.length(); i++) {
			if (cost.charAt(i) == '{') {
				k++;
			}
		}
		String manas[] = new String[k];
		int ik = 0;
		for (int i = 0; i < cost.length(); i++) {
			if (cost.charAt(i) == '{') {
				int l = cost.indexOf('}', i);
				manas[ik++] = cost.substring(i + 1, l);
				i = l;
			}
		}
		return manas;
	}

	public static List<String> sortTags(Collection<String> tags) {
		ArrayList<String> res = new ArrayList<>();
		for (ManaColor c : ManaColor.values()) {
			if (tags.contains(c.tag()))
				res.add(c.tag());
		}
		return res;
	}

	public String toCost(ManaColor... colors) {
		String res = "";
		for (ManaColor c : ManaColor.values()) {
			for (ManaColor manaColor : colors) {
				if (manaColor == c) {
					res += "{" + c.tag() + "}";
					break;
				}
			}
		}
		return res;
	}

	public static String toCost(Collection<String> tags) {
		String res = "";
		for (ManaColor c : ManaColor.values()) {
			for (String manaColor : tags) {
				if (c.tag().equals(manaColor)) {
					res += "{" + manaColor + "}";
					break;
				}
			}
		}
		return res;
	}

	Collection<ManaColor> getAllManaColors() {
		return idmap.values();
	}

	ManaColor getColorById(String id) {
		return idmap.get(id);
	}
}

//private helper class to manage colorless special cases
final class ColorsWithoutColorless implements ISearchableProperty {

	private final Colors delegate = Colors.getInstance();

	@Override
	public String getIdPrefix() {
		return delegate.getIdPrefix();
	}

	@Override
	public FilterField getFilterField() {
		return delegate.getFilterField();
	}

	@Override
	public String getNameById(String id) {
		return delegate.getNameById(id);
	}

	@Override
	public Collection<String> getIds() {
		return delegate.getIds().stream().filter(id -> delegate.getColorById(id) != Colors.ManaColor.COLORLESS)
				.toList();
	}

	// Optional helper if you need the actual colors
	public Collection<Colors.ManaColor> getColors() {
		return delegate.getAllManaColors().stream().filter(c -> c != Colors.ManaColor.COLORLESS).toList();
	}
}
