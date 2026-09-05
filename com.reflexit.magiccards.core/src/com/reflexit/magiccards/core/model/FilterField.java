
/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.regex.Pattern;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.expr.BinaryExpr;
import com.reflexit.magiccards.core.model.expr.CardFieldExpr;
import com.reflexit.magiccards.core.model.expr.Expr;
import com.reflexit.magiccards.core.model.expr.Operation;
import com.reflexit.magiccards.core.model.expr.TextValue;
import com.reflexit.magiccards.core.model.expr.Value;

public enum FilterField {
	COLOR(MagicCardField.COST, "colors", Postfix.ENUM_POSTFIX),
	CARD_TYPE(MagicCardField.TYPE, "types", Postfix.ENUM_POSTFIX),
	GROUP_FIELD(null, "group_field", Postfix.TEXT_POSTFIX), TYPE_LINE(MagicCardField.TYPE, Postfix.TEXT_POSTFIX),
	TEXT_LINE(MagicCardField.ORACLE, Postfix.TEXT_POSTFIX), NAME_LINE(MagicCardField.NAME, Postfix.TEXT_POSTFIX),
	POWER(MagicCardField.POWER, Postfix.NUMERIC_POSTFIX), TOUGHNESS(MagicCardField.TOUGHNESS, Postfix.NUMERIC_POSTFIX),
	CCC(MagicCardField.CMC, Postfix.NUMERIC_POSTFIX), EDITION(MagicCardField.SET, Postfix.ENUM_POSTFIX),
	RARITY(MagicCardField.RARITY, Postfix.ENUM_POSTFIX), LOCATION(MagicCardField.LOCATION, Postfix.ENUM_POSTFIX),
	PRICE(MagicCardField.PRICE, Postfix.NUMERIC_POSTFIX), DBPRICE(MagicCardField.DBPRICE, Postfix.NUMERIC_POSTFIX),
	COMMUNITYRATING(MagicCardField.RATING, Postfix.NUMERIC_POSTFIX),
	ARTIST(MagicCardField.ARTIST, Postfix.TEXT_POSTFIX), COUNT(MagicCardField.COUNT, Postfix.NUMERIC_POSTFIX),
	COMMENT(MagicCardField.COMMENT, Postfix.TEXT_POSTFIX), OWNERSHIP(MagicCardField.OWNERSHIP, Postfix.TEXT_POSTFIX),
	LANG(MagicCardField.LANG, Postfix.TEXT_POSTFIX),
	TEXT_LINE_2(MagicCardField.ORACLE, TEXT_LINE + "_2", Postfix.TEXT_POSTFIX),
	TEXT_LINE_3(MagicCardField.ORACLE, TEXT_LINE + "_3", Postfix.TEXT_POSTFIX),
	TEXT_NOT_1(MagicCardField.ORACLE, TEXT_LINE + "_exclude_1", Postfix.TEXT_POSTFIX),
	TEXT_NOT_2(MagicCardField.ORACLE, TEXT_LINE + "_exclude_2", Postfix.TEXT_POSTFIX),
	TEXT_NOT_3(MagicCardField.ORACLE, TEXT_LINE + "_exclude_3", Postfix.TEXT_POSTFIX),
	COLLNUM(MagicCardField.COLLNUM, Postfix.NUMERIC_POSTFIX), SPECIAL(MagicCardField.SPECIAL, Postfix.TEXT_POSTFIX),
	FORTRADECOUNT(MagicCardField.FORTRADECOUNT, Postfix.NUMERIC_POSTFIX),
	FORMAT(MagicCardField.LEGALITY, Postfix.TEXT_POSTFIX),
	FORMAT_TEXT(MagicCardField.LEGALITY_FILTER, Postfix.TEXT_POSTFIX),
	COLOR_IDENTITY(MagicCardField.COST, "identity", Postfix.ENUM_POSTFIX),;

	// fields
	private ICardField field;
	private String id;
	private String postfix;
	private static final String PREFIX = DataManager.ID;

	static class Postfix {
		public static final String TEXT_POSTFIX = "text";
		public static final String NUMERIC_POSTFIX = "numeric";
		public static final String ENUM_POSTFIX = "";
	}

	private FilterField(ICardField field, String postfix) {
		this(field, field.name(), postfix);
	}

	private FilterField(ICardField field, String s, String postfix) {
		this.field = field;
		this.id = s;
		this.postfix = postfix;
	}

	public String getPrefConstant() {
		return PREFIX + ".filter." + toString() + "." + postfix;
	}

	@Override
	public String toString() {
		return id;
	}

	public static String escapeProperty(String string) {
		String res = string.toLowerCase();
		res = res.replaceAll("[^\\w-./]", "_");
		return res;
	}

	public static String getPrefConstant(String sub, String name) {
		return PREFIX + ".filter." + sub + "." + escapeProperty(name);
	}

	public static String getPrefConstant(FilterField sub, String name) {
		return PREFIX + ".filter." + sub.toString() + "." + escapeProperty(name);
	}

	public static Collection<String> getAllIds() {
		ArrayList<String> ids = new ArrayList<String>();
		ids.addAll(Colors.getInstance().getIds());
		ids.addAll(ColorTypes.getInstance().getIds());
		ids.addAll(CardTypes.getInstance().getIds());
		ids.addAll(Editions.getInstance().getIds());
		ids.addAll(Rarity.getInstance().getIds());
		ids.addAll(Locations.getInstance().getIds());
		ids.add(TEXT_LINE.getPrefConstant());
		ids.add(TYPE_LINE.getPrefConstant());
		ids.add(NAME_LINE.getPrefConstant());
		ids.add(POWER.getPrefConstant());
		ids.add(TOUGHNESS.getPrefConstant());
		ids.add(CCC.getPrefConstant());
		ids.add(COUNT.getPrefConstant());
		ids.add(PRICE.getPrefConstant());
		ids.add(DBPRICE.getPrefConstant());
		ids.add(COMMUNITYRATING.getPrefConstant());
		ids.add(COLLNUM.getPrefConstant());
		ids.add(ARTIST.getPrefConstant());
		ids.add(COMMENT.getPrefConstant());
		ids.add(OWNERSHIP.getPrefConstant());
		ids.add(TEXT_LINE_2.getPrefConstant());
		ids.add(TEXT_LINE_3.getPrefConstant());
		ids.add(TEXT_NOT_1.getPrefConstant());
		ids.add(TEXT_NOT_2.getPrefConstant());
		ids.add(TEXT_NOT_3.getPrefConstant());
		ids.add(FORTRADECOUNT.getPrefConstant());
		ids.add(SPECIAL.getPrefConstant());
		ids.add(LANG.getPrefConstant());
		ids.add(FORMAT_TEXT.getPrefConstant());
		// TODO add the rest
		return ids;
	}

	public String getPostfix() {
		return postfix;
	}

	public ICardField getField() {
		return field;
	}

	public String valueFrom(HashMap<String, String> map) {
		return map.get(getPrefConstant());
	}

	public Expr valueExpr(HashMap<String, String> map) {
		return valueExpr(valueFrom(map));
	}

	public Expr valueExpr(String value) {
		if (value != null && value.length() > 0) {
			FilterField ff = this;
			switch (ff) {
			case RARITY:
			case LOCATION:
			case EDITION:
				return BinaryExpr.fieldEquals(ff.getField(), value);
			case NAME_LINE:
				if (value.indexOf('*') >= 0) {
					// '*' never appears in a card name, so it's free to use as an
					// explicit wildcard standing for exactly one arbitrary character
					// (e.g. "Jace*" matches a 5-letter name starting with "Jace") -
					// bypasses the normal word tokenizer, which would otherwise treat
					// the whole value as a literal string containing a literal '*'.
					// QuickFilterControl always wraps its stored value in a literal
					// "\"...\"" pair (a signal the tokenizer would normally strip to mean
					// "treat as one literal phrase"), unlike the filter dialog's
					// StringFieldEditor, which stores the typed text bare - strip that
					// pair here too, or the wildcard pattern ends up requiring literal
					// quote characters around the match and never finds anything.
					String unquoted = value;
					if (unquoted.length() >= 2 && unquoted.startsWith("\"") && unquoted.endsWith("\""))
						unquoted = unquoted.substring(1, unquoted.length() - 1);
					return wildcardNameExpr(ff.getField(), unquoted)
							.or(wildcardNameExpr(MagicCardField.ENGLISH_NAME, unquoted));
				}
				return BinaryExpr.textSearch(ff.getField(), value)
						.or(BinaryExpr.textSearch(MagicCardField.ENGLISH_NAME, value));
			case CARD_TYPE:
				return BinaryExpr.textSearch(ff.getField(), value);
			case TYPE_LINE:
				return BinaryExpr.textSearch(MagicCardField.TYPE_COMBINED, value);
			case ARTIST:
			case COMMENT:
			case SPECIAL:
				return BinaryExpr.textSearch(ff.getField(), value);
			case FORMAT: {
				TextValue tvalue = new TextValue(value, true, true, false);
				return new BinaryExpr(new CardFieldExpr(ff.getField()), Operation.MATCHES, tvalue);
			}
			case FORMAT_TEXT: {
				if (value == null || value.isEmpty())
					return Expr.TRUE;

				// LEGALITY_FILTER contains list of legal format.
				return BinaryExpr.textSearch(MagicCardField.LEGALITY_FILTER, value);
			}
			case CCC: {
				Expr front = BinaryExpr.fieldInt(ff.getField(), value);

				// flip != null  =>  NOT (flip == null)
				Expr flipNotNull = BinaryExpr.fieldEquals(MagicCardField.CMC_FLIP, null).not();

				// flip comparison guarded by flipNotNull
				Expr flip = BinaryExpr.fieldInt(MagicCardField.CMC_FLIP, value).and(flipNotNull);

				return front.or(flip);
			}

			case POWER: {
				Expr front = BinaryExpr.fieldInt(MagicCardField.POWER, value);

				// flip != null  =>  NOT (flip == null)
				Expr flipNotNull = BinaryExpr.fieldEquals(MagicCardField.POWER_FLIP, null).not();

				// flip comparison guarded by flipNotNull
				Expr flip = BinaryExpr.fieldInt(MagicCardField.POWER_FLIP, value).and(flipNotNull);

				return front.or(flip);
			}

			case TOUGHNESS: {
				Expr front = BinaryExpr.fieldInt(MagicCardField.TOUGHNESS, value);

				// flip != null  =>  NOT (flip == null)
				Expr flipNotNull = BinaryExpr.fieldEquals(MagicCardField.TOUGHNESS_FLIP, null).not();

				// flip comparison guarded by flipNotNull
				Expr flip = BinaryExpr.fieldInt(MagicCardField.TOUGHNESS_FLIP, value).and(flipNotNull);

				return front.or(flip);
			}

			case COUNT:
			case FORTRADECOUNT:
			case COMMUNITYRATING:
			case COLLNUM:
				return BinaryExpr.fieldInt(ff.getField(), value);
			case COLOR: {
				String en;
				// RD Review the logic to support correctly colorless, hybrid and variations 
				if (value.equals("Multi-Color")) {
					return fieldEquals(MagicCardField.CTYPE, "multi")
							.or(fieldEquals(MagicCardField.CTYPE, "multi-hybrid"))
							.or(fieldEquals(MagicCardField.CTYPE_FLIP, "multi"))
							.or(fieldEquals(MagicCardField.CTYPE_FLIP, "multi-hybrid"));
				} else if (value.equals("Mono-Color")) {
					return fieldEquals(MagicCardField.CTYPE, "colorless").or(fieldEquals(MagicCardField.CTYPE, "mono"))
							.or(fieldEquals(MagicCardField.CTYPE, "mono-hybrid")
									.or(fieldEquals(MagicCardField.CTYPE_FLIP, "colorless"))
									.or(fieldEquals(MagicCardField.CTYPE_FLIP, "mono"))
									.or(fieldEquals(MagicCardField.CTYPE_FLIP, "mono-hybrid")));
				} else if ((value.equals("Hybrid"))) {
					return fieldEquals(MagicCardField.CTYPE, "multi-hybrid")
							.or(fieldEquals(MagicCardField.CTYPE, "mono-hybrid"))
							.or(fieldEquals(MagicCardField.CTYPE_FLIP, "multi-hybrid"))
							.or(fieldEquals(MagicCardField.CTYPE_FLIP, "mono-hybrid"));

				} else if ((en = Colors.getInstance().getEncodeByName(value)) != null) {
					return BinaryExpr.fieldMatches(MagicCardField.COLOR_COMBINED, en);
				}
				break;
			}
			case COLOR_IDENTITY: {
				String en;
				// Filter the Identity using directly the IDENTITY value
				if ((en = Colors.getInstance().getEncodeByName(value)) != null) {
					return BinaryExpr.fieldMatches(MagicCardField.COLOR_IDENTITY_COMBINED, en);
				}
				break;
			}
			case DBPRICE: {
				return new BinaryExpr(new CardFieldExpr(MagicCardField.DBPRICE), Operation.EQ, new Value("0"))
						.and(fieldInt(MagicCardField.PRICE, value)).or(fieldInt(MagicCardField.DBPRICE, value));
			}
			case PRICE: {
				return new BinaryExpr(new CardFieldExpr(MagicCardField.PRICE), Operation.EQ, new Value("0"))
						.and(fieldInt(MagicCardField.DBPRICE, value)).or(fieldInt(MagicCardField.PRICE, value));
			}
			case OWNERSHIP: {
				BinaryExpr b1 = fieldEquals(MagicCardField.OWNERSHIP, value);
				Expr b2;
				if ("true".equals(value))
					b2 = fieldInt(MagicCardField.OWN_COUNT, ">=1");
				else
					b2 = fieldInt(MagicCardField.OWN_COUNT, "==0");
				return b1.or(b2);
			}
			case LANG: {
				if (value.equals("")) {
					return Expr.TRUE;
				} else if (value.equals(Languages.Language.ENGLISH.getLang())) {
					return fieldEquals(MagicCardField.LANG, null).or(fieldEquals(MagicCardField.LANG, value));
				} else {
					return fieldEquals(MagicCardField.LANG, value);
				}
			}
			case TEXT_LINE:
			case TEXT_LINE_2:
			case TEXT_LINE_3:
			case TEXT_NOT_1:
			case TEXT_NOT_2:
			case TEXT_NOT_3:
				return BinaryExpr.textSearch(MagicCardField.ORACLE_COMBINED, value);
			case GROUP_FIELD:
				return Expr.EMPTY;
			default:
				break;
			}
			throw new IllegalArgumentException();
		}
		return Expr.EMPTY;
	}

	/**
	 * Builds a NAME_LINE search expression where each {@code '*'} in
	 * {@code value} stands for exactly one arbitrary character and every other
	 * character must match literally - e.g. "Jace*" matches a 5-letter name
	 * starting with "Jace". Case-insensitive, substring match (same as the
	 * normal text-search path), but bypasses the word tokenizer entirely, so
	 * only single-word/no-tokenizer semantics apply once '*' is used.
	 */
	private static Expr wildcardNameExpr(ICardField field, String value) {
		StringBuilder pattern = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '*') {
				pattern.append('.');
			} else {
				pattern.append(Pattern.quote(String.valueOf(c)));
			}
		}
		TextValue tvalue = new TextValue(Pattern.compile(pattern.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
		return new BinaryExpr(new CardFieldExpr(field), Operation.MATCHES, tvalue);
	}

	public static BinaryExpr fieldEquals(ICardField field, String value) {
		return BinaryExpr.fieldEquals(field, value);
	}

	public static BinaryExpr fieldInt(ICardField field, String value) {
		return BinaryExpr.fieldInt(field, value);
	}
}
