
/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - PROXY filter field (Genuine / Proxy)
 *     Rémi Dutil (2026) - removed COMMUNITYRATING (community rating is not a
 *                         concept this app tracks anymore)
 *     Rémi Dutil (2026) - POWER/TOUGHNESS/CCC/DBPRICE/COLLNUM now build a
 *                         Min/Max range expression instead of a single
 *                         comparison
 *     Rémi Dutil (2026) - FINISH filter field (Nonfoil / Foil / Etched)
 *     Rémi Dutil (2026) - getAllIds() now also carries CardFinishes.AND_ID -
 *                         AbstractMagicCardsListControl#storeToMap() only
 *                         copies a preference key into the filter's map when
 *                         it's in this list, so the checkbox's value was
 *                         silently dropped before MagicCardFilter ever saw
 *                         it, and checking it did nothing at all
 *     Rémi Dutil (2026) - FINISH now matches a whole tag inside the field's
 *                         comma-joined value instead of an exact string
 *                         equals - needed once the field could also hold a
 *                         printing's several supported finishes at once
 *     Rémi Dutil (2026) - COLOR_IDENTITY now matches Scryfall's own
 *                         authoritative color_identity by default - "Identity"
 *                         means this. New COLOR_IDENTITY_EXTENDED filter
 *                         field: the app's own oracle-text heuristic
 *                         (formerly what COLOR_IDENTITY itself matched),
 *                         picked instead when the new ColorTypes.EXTENDED_ID
 *                         checkbox is also checked - see
 *                         MagicCardFilter#createColorGroup(). getAllIds()
 *                         needs no extra entry for it - unlike
 *                         CardFinishes.AND_ID, ColorTypes' own checkbox ids
 *                         are already all included via
 *                         ColorTypes.getInstance().getIds()
 *     Rémi Dutil (2026) - wildcardNameExpr()/NAME_LINE: '*' now means "zero or
 *                         more characters" (the conventional glob meaning)
 *                         instead of "exactly one" - the old behavior made
 *                         "Jace*" fail to match "Jace, the Mind Sculptor"
 *                         (needs 20+ trailing chars, not one), which isn't
 *                         what anyone typing a wildcard expects. Added '?'
 *                         for "exactly one character", the conventional glob
 *                         meaning '*' used to have, so that use case (fixed-
 *                         length gap) is still expressible. Also triggers the
 *                         wildcard path on '?' alone, not just '*'.
 *     Rémi Dutil (2026) - COUNT/FORTRADECOUNT/PRICE (User Price) were still
 *                         calling fieldInt() - the OLD single-comparison
 *                         parser (">=5", "<=5", "==5", or a bare number for
 *                         "="), from before these fields switched to
 *                         RangeComparisonFieldEditor. That editor always
 *                         stores a "<min>:<max>" string now (see its own
 *                         header) - regardless of operator, since even ">=5"
 *                         encodes as "5:" and "<=5" as ":5" - which
 *                         fieldInt() doesn't understand at all: none of its
 *                         prefix checks match a colon-containing string, so
 *                         every one of them fell through to its last case,
 *                         an EXACT match against the literal string "5:" (or
 *                         "3:8", etc) - which no real card's field ever
 *                         equals, silently matching nothing no matter what
 *                         operator was actually chosen. Switched all three to
 *                         fieldRange(), the "<min>:<max>"-aware parser CCC/
 *                         POWER/TOUGHNESS/COLLNUM/DBPRICE already correctly
 *                         used - same fix DBPRICE's own case already models
 *                         for the "field is 0, fall back to the other price"
 *                         pattern PRICE mirrors.
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
	ARTIST(MagicCardField.ARTIST, Postfix.TEXT_POSTFIX), COUNT(MagicCardField.COUNT, Postfix.NUMERIC_POSTFIX),
	COMMENT(MagicCardField.COMMENT, Postfix.TEXT_POSTFIX), OWNERSHIP(MagicCardField.OWNERSHIP, Postfix.TEXT_POSTFIX),
	LANG(MagicCardField.LANG, Postfix.TEXT_POSTFIX),
	TEXT_LINE_2(MagicCardField.ORACLE, TEXT_LINE + "_2", Postfix.TEXT_POSTFIX),
	TEXT_LINE_3(MagicCardField.ORACLE, TEXT_LINE + "_3", Postfix.TEXT_POSTFIX),
	TEXT_NOT_1(MagicCardField.ORACLE, TEXT_LINE + "_exclude_1", Postfix.TEXT_POSTFIX),
	TEXT_NOT_2(MagicCardField.ORACLE, TEXT_LINE + "_exclude_2", Postfix.TEXT_POSTFIX),
	TEXT_NOT_3(MagicCardField.ORACLE, TEXT_LINE + "_exclude_3", Postfix.TEXT_POSTFIX),
	COLLNUM(MagicCardField.COLLNUM, Postfix.NUMERIC_POSTFIX), SPECIAL(MagicCardField.SPECIAL, Postfix.TEXT_POSTFIX),
	CONDITION(MagicCardField.CONDITION, Postfix.ENUM_POSTFIX),
	FINISH(MagicCardField.FINISH, Postfix.ENUM_POSTFIX),
	PROXY(MagicCardField.PROXY, Postfix.TEXT_POSTFIX),
	FORTRADECOUNT(MagicCardField.FORTRADECOUNT, Postfix.NUMERIC_POSTFIX),
	FORMAT(MagicCardField.LEGALITY, Postfix.TEXT_POSTFIX),
	FORMAT_TEXT(MagicCardField.LEGALITY_FILTER, Postfix.TEXT_POSTFIX),
	COLOR_IDENTITY(MagicCardField.COST, "identity", Postfix.ENUM_POSTFIX),
	COLOR_IDENTITY_EXTENDED(MagicCardField.COST, "identityExtended", Postfix.ENUM_POSTFIX),;

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
		ids.addAll(CardConditions.getInstance().getIds());
		ids.addAll(CardFinishes.getInstance().getIds());
		ids.add(CardFinishes.AND_ID);
		ids.addAll(Proxies.getInstance().getIds());
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
			case CONDITION: {
				if (CardConditions.NOT_GRADED.equals(value))
					return BinaryExpr.fieldEquals(MagicCardField.CONDITION, null);
				// the checkbox label ("Near Mint") or an abbr both work - match on
				// the canonical serialized form the field actually holds
				CardCondition cc = CardCondition.resolve(value);
				return BinaryExpr.fieldEquals(MagicCardField.CONDITION, cc == null ? value : cc.toString());
			}
			case FINISH: {
				// MagicCardField.FINISH.get() returns a comma-joined tag list - one
				// tag for an owned copy ("foil"), several for a printing being
				// browsed with no specific copy in play ("nonfoil,foil"). Match the
				// tag as a whole item (comma or string-boundary delimited), not a
				// plain substring, so "foil" doesn't also match inside "nonfoil".
				CardFinish cf = CardFinish.resolve(value);
				String tag = cf == null ? value : cf.toString();
				return BinaryExpr.fieldMatches(MagicCardField.FINISH,
						"(^|,)" + java.util.regex.Pattern.quote(tag) + "($|,)");
			}
			case PROXY: {
				if (Proxies.PROXY.equalsIgnoreCase(value) || "true".equalsIgnoreCase(value))
					return BinaryExpr.fieldEquals(MagicCardField.PROXY, "true");
				// "Genuine" / anything else: the copy is not flagged as a proxy
				return BinaryExpr.fieldEquals(MagicCardField.PROXY, null)
						.or(BinaryExpr.fieldEquals(MagicCardField.PROXY, "false"));
			}
			case NAME_LINE:
				if (value.indexOf('*') >= 0 || value.indexOf('?') >= 0) {
					// neither '*' nor '?' ever appears in a card name, so they're free
					// to use as explicit wildcards - '*' for zero or more arbitrary
					// characters (e.g. "Jace*" matches every Jace), '?' for exactly one
					// (e.g. "Bo?k" matches "Book" or "Bork") - bypasses the normal word
					// tokenizer, which would otherwise treat the whole value as a
					// literal string containing literal '*'/'?' characters.
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
				Expr front = BinaryExpr.fieldRange(ff.getField(), value);

				// flip != null  =>  NOT (flip == null)
				Expr flipNotNull = BinaryExpr.fieldEquals(MagicCardField.CMC_FLIP, null).not();

				// flip comparison guarded by flipNotNull
				Expr flip = BinaryExpr.fieldRange(MagicCardField.CMC_FLIP, value).and(flipNotNull);

				return front.or(flip);
			}

			case POWER: {
				Expr front = BinaryExpr.fieldRange(MagicCardField.POWER, value);

				// flip != null  =>  NOT (flip == null)
				Expr flipNotNull = BinaryExpr.fieldEquals(MagicCardField.POWER_FLIP, null).not();

				// flip comparison guarded by flipNotNull
				Expr flip = BinaryExpr.fieldRange(MagicCardField.POWER_FLIP, value).and(flipNotNull);

				return front.or(flip);
			}

			case TOUGHNESS: {
				Expr front = BinaryExpr.fieldRange(MagicCardField.TOUGHNESS, value);

				// flip != null  =>  NOT (flip == null)
				Expr flipNotNull = BinaryExpr.fieldEquals(MagicCardField.TOUGHNESS_FLIP, null).not();

				// flip comparison guarded by flipNotNull
				Expr flip = BinaryExpr.fieldRange(MagicCardField.TOUGHNESS_FLIP, value).and(flipNotNull);

				return front.or(flip);
			}

			case COLLNUM:
			case COUNT:
			case FORTRADECOUNT:
				return BinaryExpr.fieldRange(ff.getField(), value);
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
				// Scryfall's own color_identity, already the whole card's
				// (both faces combined) - no COMBINED-style DFC lookup needed
				if ((en = Colors.getInstance().getEncodeByName(value)) != null) {
					return BinaryExpr.fieldMatches(MagicCardField.COLOR_IDENTITY, en);
				}
				break;
			}
			case COLOR_IDENTITY_EXTENDED: {
				String en;
				// Filter using the app's own oracle-text heuristic instead
				if ((en = Colors.getInstance().getEncodeByName(value)) != null) {
					return BinaryExpr.fieldMatches(MagicCardField.COLOR_IDENTITY_EXTENDED_COMBINED, en);
				}
				break;
			}
			case DBPRICE: {
				return new BinaryExpr(new CardFieldExpr(MagicCardField.DBPRICE), Operation.EQ, new Value("0"))
						.and(BinaryExpr.fieldRange(MagicCardField.PRICE, value))
						.or(BinaryExpr.fieldRange(MagicCardField.DBPRICE, value));
			}
			case PRICE: {
				return new BinaryExpr(new CardFieldExpr(MagicCardField.PRICE), Operation.EQ, new Value("0"))
						.and(BinaryExpr.fieldRange(MagicCardField.DBPRICE, value))
						.or(BinaryExpr.fieldRange(MagicCardField.PRICE, value));
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
	 * Builds a NAME_LINE search expression with conventional glob wildcards:
	 * {@code '*'} stands for zero or more arbitrary characters (e.g. "Jace*"
	 * matches every Jace), {@code '?'} for exactly one (e.g. "Bo?k" matches
	 * "Book" or "Bork"), every other character matches literally.
	 * Case-insensitive, substring match (same as the normal text-search path),
	 * but bypasses the word tokenizer entirely, so only single-word/no-tokenizer
	 * semantics apply once a wildcard is used.
	 */
	private static Expr wildcardNameExpr(ICardField field, String value) {
		StringBuilder pattern = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '*') {
				pattern.append(".*");
			} else if (c == '?') {
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
