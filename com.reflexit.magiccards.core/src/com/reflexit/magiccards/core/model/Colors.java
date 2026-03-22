/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
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

	public static Collection<String> getColorPresense(String text, Collection<String> res) {
		if (text == null || text.length() == 0)
			return res;

		String oracle = text;

		// Before doing the color pattern check, replace all the hybrid with separate colors
		oracle = oracle.replaceAll("/", "}{");

		// Before doing the color pattern check, replace all colorless with C
		oracle = oracle.replaceAll("\\x{7b}([\\dXYZ])++", "{C");

		// Before doing the color pattern check, remove all the "Pay life" tag
		oracle = oracle.replaceAll("P}", "}");

		// For oracle, search deeper, including reference to "named cards", improving the search capability
		if (oracle.charAt(0) != '{') {
			// Replace all the land types with a equivalent mana color to allow the pattern to find it
			oracle = oracle.replaceAll(", or Island card", " card {U}");
			oracle = oracle.replaceAll(", or Plains card", " card {W}");
			oracle = oracle.replaceAll(", or Mountain card", " card {R}");
			oracle = oracle.replaceAll(", or Swamp card", " card {B}");
			oracle = oracle.replaceAll(", or Forest card", " card {G}");

			// Replace all the land types with a equivalent mana color to allow the pattern to find it
			oracle = oracle.replaceAll("or Island card", "card {U}");
			oracle = oracle.replaceAll("or Plains card", "card {W}");
			oracle = oracle.replaceAll("or Mountain card", "card {R}");
			oracle = oracle.replaceAll("or Swamp card", "card {B}");
			oracle = oracle.replaceAll("or Forest card", "card {G}");

			// Replace all the land types with a equivalent mana color to allow the pattern to find it
			oracle = oracle.replaceAll(", Island card", " card {U}");
			oracle = oracle.replaceAll(", Plains card", " card {W}");
			oracle = oracle.replaceAll(", Mountain card", " card {R}");
			oracle = oracle.replaceAll(", Swamp card", " card {B}");
			oracle = oracle.replaceAll(", Forest card", " card {G}");

			// Replace all the land types with a equivalent mana color to allow the pattern to find it
			oracle = oracle.replaceAll("Island card", "card {U}");
			oracle = oracle.replaceAll("Plains card", "card {W}");
			oracle = oracle.replaceAll("Mountain card", "card {R}");
			oracle = oracle.replaceAll("Swamp card", "card {B}");
			oracle = oracle.replaceAll("Forest card", "card {G}");

		}

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
			if (r == null || r.charAt(0) == '*' | r.equals("costless"))
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
