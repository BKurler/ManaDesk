/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - CONDITION field (per-copy card grade)
 *     Rémi Dutil (2026) - PROXY field + genuine-only own-count / progress fields
 *     Rémi Dutil (2026) - FINISH (per-copy: Nonfoil/Foil/Etched) + FINISHES
 *                         (which finishes a PRINTING supports, Scryfall-derived)
 *     Rémi Dutil (2026) - FINISH.getM(MagicCard): browsing a printing itself
 *                         (Printings/DB view) now shows what IT supports,
 *                         not an aggregate of the user's own copies (the
 *                         inherited physical-field default, which read
 *                         getRealCards() - so editing your own copy's finish
 *                         in a deck was visibly "leaking" into the DB view)
 *     Rémi Dutil (2026) - removed RATING (community rating is not a concept
 *                         this app tracks anymore)
 *     Rémi Dutil (2026) - COLOR now caches its value at import time instead
 *                         of recomputing the oracle-text heuristic on every
 *                         get() - falls back to the live computation only
 *                         for a DB entry that predates this (see
 *                         ParseScryFallChecklist). COLOR_IDENTITY is now
 *                         Scryfall's own authoritative color_identity array
 *                         (also cached at import), for legality (Commander
 *                         color-identity rule) and as what "Identity" means
 *                         everywhere in the app by default. The app's own
 *                         oracle-text heuristic (what COLOR_IDENTITY used to
 *                         compute) moved to the new COLOR_IDENTITY_EXTENDED -
 *                         still there, unchanged, useful for search (e.g.
 *                         finding a colorless fetch land that still
 *                         "touches" green) but not accurate enough to gate
 *                         legality on, and no longer the default
 *     Rémi Dutil (2026) - UNIQUE_COUNT_BY_FINISH/OWN_UNIQUE_BY_FINISH/
 *                         GENUINE_OWN_UNIQUE_BY_FINISH/PERCENT_COMPLETE_BY_FINISH/
 *                         PERCENT_COMPLETE_GENUINE_BY_FINISH: back Collector's
 *                         new "Count Finishes Separately" completion mode -
 *                         same shape as UNIQUE_COUNT/OWN_UNIQUE/
 *                         GENUINE_OWN_UNIQUE/PERCENT_COMPLETE/
 *                         PERCENT_COMPLETE_GENUINE, just keyed on
 *                         (printing, finish) instead of printing alone
 *     Rémi Dutil (2026) - COUNT4_BY_FINISH/PERCENT4_COMPLETE_BY_FINISH: the
 *                         Progress4 ("own a full playset") counterpart to
 *                         the pair above - a printing's target scales with
 *                         how many finishes it supports (etched-only needs
 *                         4; nonfoil+foil needs 4 of EACH, i.e. 8) instead
 *                         of a flat 4 per printing regardless of finish
 *     Rémi Dutil (2026) - GENUINE_COUNT4/GENUINE_COUNT4_BY_FINISH/
 *                         PERCENT4_COMPLETE_GENUINE/
 *                         PERCENT4_COMPLETE_GENUINE_BY_FINISH: Progress4's own
 *                         "Count Proxies" split (same relationship COUNT4/
 *                         PERCENT4_COMPLETE has to these) - without it,
 *                         Progress4's group-level number always included
 *                         proxy copies regardless of that toggle, while the
 *                         per-card leaf text was already proxy-aware
 *     Rémi Dutil (2026) - COLOR/COLOR_IDENTITY/COLOR_IDENTITY_EXTENDED now
 *                         use the new ColorUnionAggregator instead of the
 *                         default StringAggregator - a Collector Name-group
 *                         spanning printings with different colors now shows
 *                         the combined colors (e.g. "White-Blue") instead of
 *                         colliding to a bare "*"
 *     Rémi Dutil (2026) - UNIQUE_COUNT_BY_FINISH.get(IMagicCard): was
 *                         delegating to card.getUniqueCount() (always 1,
 *                         finish-oblivious), which disagreed with its own
 *                         aggregator (FieldUniqueByFinishAggregator, one
 *                         slot per finish the printing supports) for a
 *                         single-card group - CardGroupTest#testContractOne
 *                         caught this as a leaf-vs-1-member-group mismatch
 *     Rémi Dutil (2026) - OWN_UNIQUE_BY_FINISH.getM(MagicCardPhysical): same
 *                         class of bug as UNIQUE_COUNT_BY_FINISH above, and
 *                         caught by the same test - getM() was also
 *                         delegating to card.getUniqueCount(), completely
 *                         ignoring ownership despite the field's own name;
 *                         now checks isOwn() directly, matching its sibling
 *                         GENUINE_OWN_UNIQUE_BY_FINISH's leaf. The three
 *                         "*_BY_FINISH"/Progress4-by-finish aggregators that
 *                         unconditionally scanned a printing's globally
 *                         registered getPhysicalCards() instead of checking
 *                         the visited node directly when it's already a
 *                         MagicCardPhysical - FieldOwnUniqueByFinishAggregator,
 *                         FieldCount4ByFinishAggregator,
 *                         FieldGenuineCount4ByFinishAggregator - were fixed
 *                         the same way FieldGenuineCount4Aggregator already
 *                         did it (see each class' own header)
 */
package com.reflexit.magiccards.core.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.abs.ICardVisitor;
import com.reflexit.magiccards.core.model.aggr.AbstractFloatCountAggregator;
import com.reflexit.magiccards.core.model.aggr.AbstractIntTransAggregator;
import com.reflexit.magiccards.core.model.aggr.AbstractPowerAggregator;
import com.reflexit.magiccards.core.model.aggr.CollisionAggregator;
import com.reflexit.magiccards.core.model.aggr.ColorUnionAggregator;
import com.reflexit.magiccards.core.model.aggr.DateAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldCount4Aggregator;
import com.reflexit.magiccards.core.model.aggr.FieldCount4ByFinishAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldCreatureCountAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldGenuineCount4Aggregator;
import com.reflexit.magiccards.core.model.aggr.FieldGenuineCount4ByFinishAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldGenuineOwnCountAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldGenuineOwnUniqueAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldGenuineOwnUniqueByFinishAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldGenuineProggress4Aggregator;
import com.reflexit.magiccards.core.model.aggr.FieldGenuineProggress4ByFinishAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldGenuineProggressAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldGenuineProggressByFinishAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldLegalityMapAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldOwnCountAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldOwnTotalCountAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldOwnUniqueAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldOwnUniqueByFinishAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldProggress4Aggregator;
import com.reflexit.magiccards.core.model.aggr.FieldProggress4ByFinishAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldProggressAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldProggressByFinishAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldSizeAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldUniqueAggregator;
import com.reflexit.magiccards.core.model.aggr.FieldUniqueByFinishAggregator;
import com.reflexit.magiccards.core.model.aggr.StringAggregator;
import com.reflexit.magiccards.core.model.storage.ICardStore;

public enum MagicCardField implements ICardField {
	ID {
		@Override
		public void setM(MagicCard card, Object value) {
			card.setCardId(String.valueOf(value));
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getCardId();
		};
	},
	NAME {
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setName(value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getName();
		};
	},
	COST {
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setCost(value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getCost();
		};
	},
	TYPE {
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setType(value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getType();
		};
	},
	POWER {
		@Override
		public ICardVisitor getAggregator() {
			return new AbstractPowerAggregator(this);
		}

		@Override
		protected void setStr(MagicCard card, String value) {
			if (value != null && value.trim().isEmpty())
				value = "";
			card.setPower(value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getPower();
		};
	},
	TOUGHNESS {
		@Override
		public ICardVisitor getAggregator() {
			return new AbstractPowerAggregator(this);
		}

		@Override
		protected void setStr(MagicCard card, String value) {
			if (value != null && value.trim().isEmpty())
				value = "";
			card.setToughness(value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getToughness();
		};
	},
	ORACLE("oracleText") {
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setOracleText(value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getOracleText();
		};
	},
	SET("edition") {
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setSet(value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getSet();
		};
	},
	RARITY {
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setRarity(value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getRarity();
		};
	},
	CTYPE(null) {
		@Override
		public Object get(IMagicCard card) {
			return card.getColorType();
		};
	},
	CMC(null) {
		// @Override
		// public Object aggregateValueOf(ICard card) {
		// Colors cl = Colors.getInstance();
		// return cl.getConvertedManaCost(((IMagicCard) card).getCost());
		// }
		@Override
		public ICardVisitor getAggregator() {
			return new CollisionAggregator(this, "*");
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getCmc();
		};
	},
	DBPRICE() {
		@Override
		public ICardVisitor getAggregator() {
			return new AbstractFloatCountAggregator(this);
		}

		@Override
		public void setM(MagicCard card, Object value) {
			card.setDbPrice(castToFloat(value));
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getDbPrice();
		};
	},
	LANG {
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setLanguage(value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getLanguage();
		};
	},
	EDITION_ABBR(null) {
		@Override
		public Object get(IMagicCard card) {
			return card.getEdition().getMainAbbreviation();
		};
	},
	ARTIST {
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setArtist(value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getArtist();
		};
	},
	COLLNUM("num") { // collector number value.e. 5/234

		@Override
		protected void setStr(MagicCard card, String value) {
			card.setCollNumber(value);
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getCollNumber();
		};

	},
	RULINGS {
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setRulings(value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getRulings();
		};
	},
	TEXT {
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setText(value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getText();
		};
	},
	ENID("enId") {
		@Override
		public ICardVisitor getAggregator() {
			return new CollisionAggregator(this, 0);
		}

		@Override
		public void setM(MagicCard card, Object value) {
			card.setEnglishCardId(String.valueOf(value));
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getEnglishCardId();
		};

	},
	GATHERERID("gathererId") {

		@Override
		public ICardVisitor getAggregator() {
			return new CollisionAggregator(this, 0);
		}

		@Override
		public void setM(MagicCard card, Object value) {
			card.setGathererCardId(value.toString());
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getGathererId();
		};

		@Override
		public boolean isTransient() {
			return false;
		}

	},
	TCGID("tcgId") {

		@Override
		public ICardVisitor getAggregator() {
			return new CollisionAggregator(this, 0);
		}

		@Override
		public void setM(MagicCard card, Object value) {
			card.setTcgCardId(value.toString());
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getTcgId();
		};

		@Override
		public boolean isTransient() {
			return false;
		}
	},
	NOUPDATE(null) {
		@Override
		public void setM(MagicCard card, Object value) {
			if (value instanceof String || value == null)
				card.setProperty(this, Boolean.valueOf((String) value));
			else if (value instanceof Boolean)
				card.setProperty(this, value);
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getProperty(this);
		};
	},
	FLIPID(null) {
		@Override
		public ICardVisitor getAggregator() {
			return new CollisionAggregator(this, 0);
		}

		@Override
		public void setM(MagicCard card, Object value) {
			card.setPropertyString(this, value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getFlipId();
		};
	},
	COLOR_INDICATOR(null) {
		@Override
		public ICardVisitor getAggregator() {
			return new StringAggregator(this);
		}

		@Override
		protected void setStr(MagicCard card, String value) {
			card.setPropertyString(this, value);
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getProperty(this);
		};
	},
	PART(null) {
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setPropertyString(this, value);
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getPart();
		};
	},
	OTHER_PART(null) {
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setPropertyString(this, value);
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getProperty(this);
		};
	},
	SET_BLOCK(null) { // block of the set
		@Override
		public Object get(IMagicCard card) {
			return card.getEdition().getBlock();
		};
	},
	SET_CORE(null) { // type of the set (Core, Expantions, etc)
		@Override
		public Object get(IMagicCard card) {
			return card.getEdition().getType();
		};
	},
	SET_RELEASE(null) { // release date of the set
		@Override
		public ICardVisitor getAggregator() {
			return new DateAggregator(this);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getEdition().getReleaseDate();
		};
	},
	UNIQUE_COUNT(null) { // count of unique cards (usually only make sense for
							// group)
		@Override
		public ICardVisitor getAggregator() {
			return new FieldUniqueAggregator(this);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getUniqueCount();
		};
	},
	SIZE(null) { // flat size of the group, size of non-groupped element is
					// always 1
		@Override
		public ICardVisitor getAggregator() {
			return new FieldSizeAggregator(this);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getUniqueCount();
		};
	},
	SIDE(null) { // for multi sides/duble/flip card represent version of card (0
					// or 1)
		@Override
		public ICardVisitor getAggregator() {
			return new CollisionAggregator(this, 0);
		}

		@Override
		public void setM(MagicCard card, Object value) {
			card.setPropertyInteger(this, value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getSide();
		};
	},
	IMAGE_URL(null) { // for non gatherer cards
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setImageUrl(value);
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getImageUrl();
		};
	},
	LEGALITY(null) {
		@Override
		public ICardVisitor getAggregator() {
			return new FieldLegalityMapAggregator(this);
		}

		@Override
		public void setM(MagicCard card, Object value) {
			// store raw legality only
			card.setProperty(MagicCardField.LEGALITY, value);
		}

		@Override
		public Object get(IMagicCard card) {
			return card.getLegalityMap();
		};

	},
	LEGALITY_FILTER(null) {
		@Override
		public boolean isTransient() {
			return true;
		}

		@Override
		public void setM(MagicCard card, Object value) {
			// ignore anything loaded from XML
		}

		@Override
		public Object get(IMagicCard card) {
			MagicCard base = (card instanceof MagicCardPhysical) ? ((MagicCardPhysical) card).getBase()
					: (MagicCard) card;

			Object raw = base.getProperty(MagicCardField.LEGALITY);
			if (raw == null)
				return "";

			return normalizeLegality(raw.toString());
		}

		private String normalizeLegality(String raw) {
			if (raw == null)
				return "";

			String s = raw.replace(";", " ").replace("|", " ").trim();
			s = s.replaceAll("\\s+", " ");
			String[] tokens = s.split(" ");

			StringBuilder out = new StringBuilder();
			for (String t : tokens) {
				if (t.isEmpty())
					continue;
				if (t.endsWith("?") || t.endsWith("-") || t.endsWith("!"))
					continue;
				if (t.endsWith("1") || t.endsWith("+"))
					t = t.substring(0, t.length() - 1);
				out.append(t).append(" ");
			}

			if (out.length() == 0)
				return "Unknown";

			return out.toString().trim();
		}
	},

	COLOR(null) {
		@Override
		public ICardVisitor getAggregator() {
			return new ColorUnionAggregator(this);
		}

		@Override
		protected void setStr(MagicCard card, String value) {
			card.setPropertyString(this, value);
		}

		@Override
		public Object getM(MagicCard card) {
			// Cached at import time (ParseScryFallChecklist); a DB entry
			// that predates this still recomputes live here, same heuristic
			// as before - RD Return the Color, not the cost
			Object cached = card.getProperty(this);
			return cached != null ? cached : Colors.getInstance().getColorAsCost(card);
		};
	},
	COLOR_IDENTITY(null) { // Scryfall's own authoritative color_identity array
							// (a cost string, e.g. "{W}{U}"; "{C}" for a
							// CONFIRMED-colorless printing - see
							// ParseScryFallChecklist#colorIdentityCostString();
							// "" only ever means "never synced", not
							// colorless - it never matches the "Colorless"
							// filter checkbox, same as any other unknown
							// value) - see the class header. "Identity"
							// means this one; the app's own oracle-text
							// heuristic is COLOR_IDENTITY_EXTENDED below
		@Override
		public ICardVisitor getAggregator() {
			return new ColorUnionAggregator(this);
		}

		@Override
		protected void setStr(MagicCard card, String value) {
			card.setPropertyString(this, value);
		}

		@Override
		public Object getM(MagicCard card) {
			Object cached = card.getProperty(this);
			return cached != null ? cached : "";
		};
	},
	COLOR_IDENTITY_EXTENDED(null) { // the app's own oracle-text heuristic -
										// looser than Scryfall's own
										// COLOR_IDENTITY, useful for search
										// (e.g. finding a colorless fetch
										// land that still "touches" green),
										// not accurate enough to gate
										// legality on
		@Override
		public ICardVisitor getAggregator() {
			return new ColorUnionAggregator(this);
		}

		@Override
		protected void setStr(MagicCard card, String value) {
			card.setPropertyString(this, value);
		}

		@Override
		public Object getM(MagicCard card) {
			// Cached at import time; live fallback for pre-existing DB
			// entries - same oracle-text heuristic as before, unchanged
			Object cached = card.getProperty(this);
			return cached != null ? cached : Colors.getInstance().getColorIdentityAsCost(card);
		};
	},
	ENGLISH_NAME(null) { // block of the set
		@Override
		public ICardVisitor getAggregator() {
			return new StringAggregator(this);
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getEnglishName();
		}

		@Override
		public void setM(MagicCard card, Object value) {
			// ignore
		}
	},
	ENGLISH_TYPE(null) { // block of the set
		@Override
		public ICardVisitor getAggregator() {
			return new StringAggregator(this);
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getEnglishType();
		}

		@Override
		public void setM(MagicCard card, Object value) {
			// ignore
		}
	},
	HASHCODE(null) {
		@Override
		public ICardVisitor getAggregator() {
			return new CollisionAggregator(this, 0);
		}

		@Override
		public Object get(IMagicCard card) {
			return System.identityHashCode(card);
		};
	},
	PROPERTIES {
		@Override
		public void setM(MagicCard card, Object value) {
			if (value instanceof String)
				card.setProperties((String) value);
			else if (value == null)
				card.setProperties((LinkedHashMap<ICardField, Object>) null);
			else if (value instanceof LinkedHashMap)
				card.setProperties((LinkedHashMap) ((LinkedHashMap) value).clone());
			else
				throw new ClassCastException();
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getProperties();
		};
	},
	// end of magic base fields
	COUNT(true) {
		@Override
		public ICardVisitor getAggregator() {
			return new AbstractIntTransAggregator(this);
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getCount();
		};

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getCount();
		}

		@Override
		public void setM(MagicCardPhysical card, Object value) {
			if (value instanceof Integer)
				card.setCount((Integer) value);
			else
				card.setCount(Integer.parseInt((String) value));
		}
	},
	PRICE(true) {
		@Override
		public ICardVisitor getAggregator() {
			return new AbstractFloatCountAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getPrice();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			if (value instanceof Float)
				card.setPrice((Float) value);
			else
				card.setPrice(Float.parseFloat((String) value));
		}
	},
	COMMENT(true) {
		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getComment();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			card.setComment((String) value);
		}
	},
	LOCATION(true) {
		@Override
		public ICardVisitor getAggregator() {
			return new CollisionAggregator(this, Location.NO_WHERE);
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getLocation();
		};

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getLocation();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			if (value instanceof Location)
				card.setLocation((Location) value);
			else
				card.setLocation(Location.valueOf((String) value));
		}
	},
	CUSTOM(true) {
		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getCustom();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			card.setCustom((String) value);
		}
	},
	OWNERSHIP(true) {
		@Override
		public ICardVisitor getAggregator() {
			return new CollisionAggregator(this, Boolean.TRUE);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.isOwn();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			if (value instanceof Boolean)
				card.setOwn((Boolean) value);
			else
				card.setOwn(Boolean.parseBoolean((String) value));
		}
	},
	FORTRADECOUNT("forTrade", true) {
		@Override
		public ICardVisitor getAggregator() {
			return new AbstractIntTransAggregator(this);
		}

		@Override
		public boolean isTransient() {
			return true;
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getForTrade();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			if (value instanceof Integer)
				card.setProperty(MagicCardField.FORTRADECOUNT, value);
			else
				card.setProperty(MagicCardField.FORTRADECOUNT, Integer.parseInt((String) value));
		}
	},
	SPECIAL(true) { // like foil, premium, mint, played, online etc
		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getSpecial();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			card.setSpecial((String) value);
		}
	},
	SIDEBOARD(null, true) {
		@Override
		public Object aggregateValueOf(ICard card) {
			return ((IMagicCardPhysical) card).isSideboard();
		}

		@Override
		public Object getM(MagicCard card) {
			return card.isSideboard();
		};

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.isSideboard();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			MagicLogger.log(new Exception("Attempt to set sideboad field"));
		}
	},
	EXTRA(null, true) {
		@Override
		public ICardVisitor getAggregator() {
			return new CollisionAggregator(this, Boolean.FALSE);
		}

		@Override
		public Object aggregateValueOf(ICard card) {
			Location l = ((IMagicCardPhysical) card).getLocation();
			return l != null && l.isExtra();
		}

		@Override
		public Object getM(MagicCard card) {
			Location l = card.getLocation();
			return l != null && l.isExtra();
		};

		@Override
		public Object getM(MagicCardPhysical card) {
			Location l = card.getLocation();
			return l != null && l.isExtra();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			MagicLogger.log(new Exception("Attempt to set extra field"));
		}
	},
	OWN_COUNT(null, true) {
		@Override
		public ICardVisitor getAggregator() {
			return new FieldOwnCountAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getOwnCount();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	}, // count of own card (normal count counts own and virtual)
	OWN_TOTAL(null, true) {
		@Override
		public ICardVisitor getAggregator() {
			return new FieldOwnTotalCountAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getOwnTotalAll();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	}, // count of own card (normal count counts own and virtual)
	OWN_UNIQUE(null, true) {
		@Override
		public ICardVisitor getAggregator() {
			return new FieldOwnUniqueAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getUniqueCount();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	}, // count of own unique cards (only applies to groups usually)
	CREATURE_COUNT(null, true) {
		@Override
		public ICardVisitor getAggregator() {
			return new FieldCreatureCountAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getCreatureCount();
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getCreatureCount();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},
	COUNT4(null, true) {
		@Override
		public ICardVisitor getAggregator() {
			return new FieldCount4Aggregator(this);
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getCount4();
		};

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getCount4();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},
	PERCENT_COMPLETE(null, true) {
		@Override
		public ICardVisitor getAggregator() {
			return new FieldProggressAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			int c = card.getOwnCount();
			if (c > 0)
				return 100f;
			else
				return 0f;
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},
	PERCENT4_COMPLETE(null, true) {
		@Override
		public ICardVisitor getAggregator() {
			return new FieldProggress4Aggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			int c = card.getCount4();
			return (float) c * 100 / 4;
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},
	DATE(true) { // creation date of the card instance
		@Override
		public ICardVisitor getAggregator() {
			return new DateAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getDate();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			if (value instanceof String) {
				card.setDate((String) value);
			} else {
				card.setDate((Date) value);
			}
		}
	},
	ERROR(null, true) {// error field for import
		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getError();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			card.setError(value);
		}
	},

	// Define all the flip fields
	TYPE_COMBINED(null) {
		@Override
		public Object getM(MagicCard card) {
			StringBuilder sb = new StringBuilder();

			// Front TYPE
			Object frontType = card.get(TYPE);
			if (frontType != null)
				sb.append(frontType.toString());

			// Front ENGLISH_TYPE
			Object frontEng = card.get(ENGLISH_TYPE);
			if (frontEng != null) {
				if (sb.length() > 0)
					sb.append("\n");
				sb.append(frontEng.toString());
			}

			// Flip TYPE + ENGLISH_TYPE
			String flipId = card.getFlipId();
			if (flipId != null && !flipId.isEmpty()) {
				ICardStore<IMagicCard> store = DataManager.getInstance().getMagicDBStore();
				if (store != null) {
					IMagicCard flip = store.getCard(flipId);
					if (flip != null) {

						Object flipType = flip.get(TYPE);
						if (flipType != null) {
							if (sb.length() > 0)
								sb.append("\n");
							sb.append(flipType.toString());
						}

						Object flipEng = flip.get(ENGLISH_TYPE);
						if (flipEng != null) {
							if (sb.length() > 0)
								sb.append("\n");
							sb.append(flipEng.toString());
						}
					}
				}
			}

			return sb.toString();
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return getM((MagicCard) card.getBase());
		}

		@Override
		public void setStr(MagicCard card, String value) {
			// computed field: ignore writes
		}

		@Override
		public void setM(MagicCard card, Object value) {
			// computed field: ignore writes
		}
	},

	POWER_FLIP(null) {
		@Override
		public Object getM(MagicCard card) {
			String flipId = card.getFlipId();
			if (flipId == null || flipId.isEmpty())
				return null;

			ICardStore<IMagicCard> store = DataManager.getInstance().getMagicDBStore();
			if (store == null)
				return null;

			IMagicCard flip = store.getCard(flipId);
			if (flip == null)
				return null;

			Object val = flip.get(POWER);
			if (val == null)
				return null;

			// Only accept numeric values
			if (val instanceof Number)
				return val;

			// Try parsing string values
			try {
				return Integer.parseInt(val.toString());
			} catch (Exception e) {
				return null; // non-numeric → ignore
			}
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return getM((MagicCard) card.getBase());
		}

		@Override
		public void setStr(MagicCard card, String value) {
		}

		@Override
		public void setM(MagicCard card, Object value) {
		}
	},

	TOUGHNESS_FLIP(null) {
		@Override
		public Object getM(MagicCard card) {
			String flipId = card.getFlipId();
			if (flipId == null || flipId.isEmpty())
				return null;

			ICardStore<IMagicCard> store = DataManager.getInstance().getMagicDBStore();
			if (store == null)
				return null;

			IMagicCard flip = store.getCard(flipId);
			return flip != null ? flip.get(TOUGHNESS) : null;
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return getM((MagicCard) card.getBase());
		}
	},

	CMC_FLIP(null) {
		@Override
		public Object getM(MagicCard card) {
			String flipId = card.getFlipId();
			if (flipId == null || flipId.isEmpty())
				return null;

			ICardStore<IMagicCard> store = DataManager.getInstance().getMagicDBStore();
			if (store == null)
				return null;

			IMagicCard flip = store.getCard(flipId);
			return flip != null ? flip.get(CMC) : null;
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return getM((MagicCard) card.getBase());
		}
	},

	COLOR_COMBINED(null) {
		@Override
		public Object getM(MagicCard card) {
			StringBuilder sb = new StringBuilder();

			// front color
			Object front = card.get(COLOR);
			if (front != null)
				sb.append(front.toString());

			// flip color
			String flipId = card.getFlipId();
			if (flipId != null && !flipId.isEmpty()) {
				ICardStore<IMagicCard> store = DataManager.getInstance().getMagicDBStore();
				if (store != null) {
					IMagicCard flip = store.getCard(flipId);
					if (flip != null) {
						Object flipColor = flip.get(COLOR);
						if (flipColor != null) {
							if (sb.length() > 0)
								sb.append("\n");
							sb.append(flipColor.toString());
						}
					}
				}
			}

			return sb.toString();
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return getM((MagicCard) card.getBase());
		}

		@Override
		public void setStr(MagicCard card, String value) {
		}

		@Override
		public void setM(MagicCard card, Object value) {
		}
	},

	COLOR_IDENTITY_EXTENDED_COMBINED(null) { // the app's own heuristic
												// (COLOR_IDENTITY_EXTENDED),
												// concatenated across both
												// faces - COLOR_IDENTITY
												// itself (Scryfall's own data)
												// needs no such combining, it
												// is already the whole card's
												// value on either face
		@Override
		public Object getM(MagicCard card) {
			StringBuilder sb = new StringBuilder();

			// front identity
			Object front = card.get(COLOR_IDENTITY_EXTENDED);
			if (front != null)
				sb.append(front.toString());

			// flip identity
			String flipId = card.getFlipId();
			if (flipId != null && !flipId.isEmpty()) {
				ICardStore<IMagicCard> store = DataManager.getInstance().getMagicDBStore();
				if (store != null) {
					IMagicCard flip = store.getCard(flipId);
					if (flip != null) {
						Object flipIden = flip.get(COLOR_IDENTITY_EXTENDED);
						if (flipIden != null) {
							if (sb.length() > 0)
								sb.append("\n");
							sb.append(flipIden.toString());
						}
					}
				}
			}

			return sb.toString();
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return getM((MagicCard) card.getBase());
		}

		@Override
		public void setStr(MagicCard card, String value) {
		}

		@Override
		public void setM(MagicCard card, Object value) {
		}
	},

	ORACLE_COMBINED(null) {
		@Override
		public Object getM(MagicCard card) {
			StringBuilder sb = new StringBuilder();

			Object front = card.get(ORACLE);
			if (front != null)
				sb.append(front.toString());

			String flipId = card.getFlipId();
			if (flipId != null && !flipId.isEmpty()) {
				ICardStore<IMagicCard> store = DataManager.getInstance().getMagicDBStore();
				if (store != null) {
					IMagicCard flip = store.getCard(flipId);
					if (flip != null) {
						Object flipText = flip.get(ORACLE);
						if (flipText != null) {
							if (sb.length() > 0)
								sb.append("\n");
							sb.append(flipText.toString());
						}
					}
				}
			}

			return sb.toString();
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return getM((MagicCard) card.getBase());
		}

		@Override
		public void setStr(MagicCard card, String value) {
			// computed field: ignore writes
		}

		@Override
		public void setM(MagicCard card, Object value) {
			// computed field: ignore writes
		}
	},

	CTYPE_FLIP(null) {
		@Override
		public Object getM(MagicCard card) {
			String flipId = card.getFlipId();
			if (flipId == null || flipId.isEmpty())
				return null;

			ICardStore<IMagicCard> store = DataManager.getInstance().getMagicDBStore();
			if (store == null)
				return null;

			IMagicCard flip = store.getCard(flipId);
			return flip != null ? flip.get(CTYPE) : null;
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return getM((MagicCard) card.getBase());
		}
	},

	ACCESSORIES(null) { // Scryfall-derived (token ids + counters); kept in the property map, not exported
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setAccessories(value);
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getAccessories();
		};
	},

	FINISHES(null) { // Scryfall-derived: which finishes (nonfoil/foil/etched) this
						// PRINTING supports; kept in the property map, not exported
		@Override
		protected void setStr(MagicCard card, String value) {
			card.setFinishes(value);
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getFinishes();
		};
	},

	FINISH(true) { // per-copy: Nonfoil / Foil / Etched - always resolves to one of the
					// three (see MagicCardPhysical#getFinish())
		@Override
		public ICardVisitor getAggregator() {
			return new CollisionAggregator(this, null);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			// a single-tag "list" (e.g. "foil") - same shape as the MagicCard
			// override below, so filtering/generic display work identically
			// whether this is an owned copy or a printing being browsed
			return card.getFinish().toString();
		}

		@Override
		public Object getM(MagicCard card) {
			// browsing the printing itself (Printings/DB view - no specific
			// owned copy in play): what THIS PRINTING supports, never an
			// aggregate of whatever the user's own copies happen to be set to
			// (that's what the inherited default - CollisionAggregator over
			// getRealCards() - would otherwise do, since this field is
			// physical; explicitly overriding avoids it)
			return CardFinish.joinTags(card.getSupportedFinishes());
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			if (value == null || value instanceof CardFinish)
				card.setFinish((CardFinish) value);
			else
				card.setFinish(CardFinish.resolve(value.toString()));
		}
	},

	CONDITION(true) { // physical grade (Near Mint .. Damaged); null == not graded
		@Override
		public ICardVisitor getAggregator() {
			return new CollisionAggregator(this, null);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getCondition();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			if (value == null || value instanceof CardCondition)
				card.setCondition((CardCondition) value);
			else
				card.setCondition(CardCondition.resolve(value.toString()));
		}
	},

	PROXY(true) { // per-copy: this is a home-printed stand-in, not the real card
		@Override
		public ICardVisitor getAggregator() {
			return new CollisionAggregator(this, Boolean.FALSE);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			// null (not Boolean.FALSE) when genuine, so exports leave the cell blank
			return card.isProxy() ? Boolean.TRUE : null;
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			if (value instanceof Boolean)
				card.setProxy((Boolean) value);
			else
				card.setProxy(value != null && Boolean.parseBoolean(value.toString()));
		}
	},

	GENUINE_OWN_COUNT(null, true) { // OWN_COUNT excluding proxy copies
		@Override
		public ICardVisitor getAggregator() {
			return new FieldGenuineOwnCountAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.isProxy() ? 0 : card.getOwnCount();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},

	GENUINE_OWN_UNIQUE(null, true) { // OWN_UNIQUE excluding proxy copies
		@Override
		public ICardVisitor getAggregator() {
			return new FieldGenuineOwnUniqueAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.isOwn() && !card.isProxy() ? 1 : 0;
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},

	PERCENT_COMPLETE_GENUINE(null, true) { // completion % counting genuine copies only
		@Override
		public ICardVisitor getAggregator() {
			return new FieldGenuineProggressAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.isOwn() && !card.isProxy() ? 100f : 0f;
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},

	// The five fields below back Collector's "Count Finishes Separately"
	// completion mode: a printing that offers nonfoil+foil counts as TWO
	// slots to complete instead of one, mirroring the plain/genuine split
	// UNIQUE_COUNT/OWN_UNIQUE/GENUINE_OWN_UNIQUE/PERCENT_COMPLETE/
	// PERCENT_COMPLETE_GENUINE already have, just keyed on (printing, finish)
	// instead of printing alone.
	UNIQUE_COUNT_BY_FINISH(null) { // one slot per (printing, finish) that exists
		@Override
		public ICardVisitor getAggregator() {
			return new FieldUniqueByFinishAggregator(this);
		}

		@Override
		public Object get(IMagicCard card) {
			// card.getUniqueCount() (what plain UNIQUE_COUNT uses) is always 1
			// for a leaf - finish-oblivious, since UNIQUE_COUNT only counts
			// printings. This field counts one slot per FINISH the printing
			// supports instead, matching FieldUniqueByFinishAggregator's own
			// per-card contribution - a leaf's own value must agree with what
			// a single-card group reports for the same field.
			MagicCard base = card instanceof MagicCardPhysical ? ((MagicCardPhysical) card).getBase() : (MagicCard) card;
			return base.getSupportedFinishes().size();
		};
	},

	OWN_UNIQUE_BY_FINISH(null, true) { // one slot per (printing, finish) owned, proxies included
		@Override
		public ICardVisitor getAggregator() {
			return new FieldOwnUniqueByFinishAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			// was card.getUniqueCount() - a copy-paste from UNIQUE_COUNT_BY_
			// FINISH's own (also-buggy, now-fixed) leaf, not ownership-aware
			// at all. This field means "owned", so it must check isOwn() -
			// proxies count too, matching GENUINE_OWN_UNIQUE_BY_FINISH's own
			// leaf (isOwn() && !isProxy()) minus the proxy exclusion.
			return card.isOwn() ? 1 : 0;
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},

	GENUINE_OWN_UNIQUE_BY_FINISH(null, true) { // OWN_UNIQUE_BY_FINISH excluding proxy copies
		@Override
		public ICardVisitor getAggregator() {
			return new FieldGenuineOwnUniqueByFinishAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.isOwn() && !card.isProxy() ? 1 : 0;
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},

	PERCENT_COMPLETE_BY_FINISH(null, true) { // completion % counting finishes separately
		@Override
		public ICardVisitor getAggregator() {
			return new FieldProggressByFinishAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			int c = card.getOwnCount();
			if (c > 0)
				return 100f;
			else
				return 0f;
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},

	PERCENT_COMPLETE_GENUINE_BY_FINISH(null, true) { // ...counting finishes separately, genuine copies only
		@Override
		public ICardVisitor getAggregator() {
			return new FieldGenuineProggressByFinishAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.isOwn() && !card.isProxy() ? 100f : 0f;
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},

	// Progress4's own finish-aware pair: like COUNT4/PERCENT4_COMPLETE, but a
	// printing's target scales with how many finishes it supports (an
	// etched-only printing needs 4 copies to complete; a nonfoil+foil one
	// needs 4 of EACH, i.e. 8) instead of a flat 4 regardless of finish
	// variety. Always proxy-inclusive, matching COUNT4/PERCENT4_COMPLETE's
	// own long-standing proxy-agnostic behavior - Progress4 never had a
	// genuine-only variant to begin with.
	COUNT4_BY_FINISH(null, true) {
		@Override
		public ICardVisitor getAggregator() {
			return new FieldCount4ByFinishAggregator(this);
		}

		@Override
		public Object getM(MagicCard card) {
			return card.getCount4();
		};

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.getCount4();
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},

	PERCENT4_COMPLETE_BY_FINISH(null, true) {
		@Override
		public ICardVisitor getAggregator() {
			return new FieldProggress4ByFinishAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			int c = card.getCount4();
			return (float) c * 100 / 4;
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},

	// Progress4's own "Count Proxies" split, mirroring OWN_UNIQUE/
	// GENUINE_OWN_UNIQUE/PERCENT_COMPLETE/PERCENT_COMPLETE_GENUINE - without
	// this pair, Progress4's group-level number always included proxy
	// copies no matter what "Count Proxies" said, while the per-card leaf
	// text (proxy-aware via ProgressColumn#qualifyingOwnCount) did not, so a
	// card whose only copies were excluded proxies showed "0/4" on its own
	// row while still padding its group's total.
	GENUINE_COUNT4(null, true) { // COUNT4 excluding proxy copies
		@Override
		public ICardVisitor getAggregator() {
			return new FieldGenuineCount4Aggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.isOwn() && !card.isProxy() ? Math.min(card.getCount(), 4) : 0;
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},

	GENUINE_COUNT4_BY_FINISH(null, true) { // COUNT4_BY_FINISH excluding proxy copies
		@Override
		public ICardVisitor getAggregator() {
			return new FieldGenuineCount4ByFinishAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.isOwn() && !card.isProxy() ? Math.min(card.getCount(), 4) : 0;
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},

	PERCENT4_COMPLETE_GENUINE(null, true) { // completion % for playsets, genuine copies only
		@Override
		public ICardVisitor getAggregator() {
			return new FieldGenuineProggress4Aggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.isOwn() && !card.isProxy() ? (float) Math.min(card.getCount(), 4) * 100 / 4 : 0f;
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},

	PERCENT4_COMPLETE_GENUINE_BY_FINISH(null, true) { // ...counting finishes separately too, genuine copies only
		@Override
		public ICardVisitor getAggregator() {
			return new FieldGenuineProggress4ByFinishAggregator(this);
		}

		@Override
		public Object getM(MagicCardPhysical card) {
			return card.isOwn() && !card.isProxy() ? (float) Math.min(card.getCount(), 4) * 100 / 4 : 0f;
		}

		@Override
		protected void setM(MagicCardPhysical card, Object value) {
			// ignore
		}
	},

	// end of fields
	;

	private final String tag;
	private final String property;
	private final boolean phys;
	private ICardVisitor aggregator;

	MagicCardField() {
		this(false);
	}

	MagicCardField(String javaField) {
		this(javaField, false);
	}

	MagicCardField(boolean physical) {
		property = name().toLowerCase(Locale.ENGLISH);
		tag = property;
		phys = physical;
		aggregator = getAggregator();
	}

	MagicCardField(String javaField, boolean physical) {
		property = name().toLowerCase(Locale.ENGLISH);
		tag = javaField;
		phys = physical;
		aggregator = getAggregator();
	}

	public ICardVisitor getAggregator() {
		return new StringAggregator(this);
	}

	@Override
	public boolean isTransient() {
		return tag == null;
	}

	public static ICardField[] allFields() {
		MagicCardField[] values = MagicCardField.values();
		return values;
	}

	public static ICardField[] allNonTransientFields(boolean phys) {
		MagicCardField[] values = MagicCardField.values();
		ArrayList<ICardField> res = new ArrayList<>();
		for (MagicCardField f : values) {
			if (!f.isTransient()) {
				if (phys || !f.phys)
					res.add(f);
			}
		}
		return res.toArray(new ICardField[res.size()]);
	}

	/**
	 * How this field is written to external source, such as xml
	 */
	public String getTag() {
		return tag;
	}

	public String getProperty() {
		return property;
	}

	/**
	 * If field represents a special tag, what is the tag name
	 *
	 * @return
	 */
	String specialTag() {
		if (getTag() == null)
			return null;
		return getTag().toLowerCase(Locale.ENGLISH);
	}

	@Override
	public Object aggregateValueOf(ICard card) {
		return card.accept(aggregator, null);
	}

	public static ICardField fieldByName(String field) {
		if (field == null || field.length() == 0)
			return null;
		try {
			return valueOf(field);
		} catch (Exception e) {
			// ignore
		}
		// aliases
		if (field.equals("EDITION"))
			return SET;
		if (field.equals("QTY"))
			return COUNT;
		// // legacy
		// if (field.equals("CUSTOM"))
		// return LegacyField.INSTANCE;
		return null;
	}

	public static ICardField[] toFields(String line, String sep) {
		String split[] = line.split(sep);
		ICardField res[] = new ICardField[split.length];
		for (int i = 0; i < split.length; i++) {
			String string = split[i];
			ICardField field = fieldByName(string);
			res[i] = field;
		}
		return res;
	}

	@Override
	public String getLabel() {
		String name = name();
		name = name.charAt(0) + name.substring(1).toLowerCase(Locale.ENGLISH);
		name = name.replace('_', ' ');
		return name;
	}

	public boolean isPhysical() {
		return phys;
	}

	protected void setStr(MagicCard card, String value) {
		throw new IllegalArgumentException("Not settable " + this);
	}

	public void setM(MagicCard card, Object value) {
		if (value instanceof String || value == null) {
			setStr(card, (String) value);
		} else
			throw new IllegalArgumentException("Not supported type " + value.getClass() + " for " + this);
	}

	protected void setM(MagicCardPhysical card, Object value) {
		if (!isPhysical())
			setM(card.getBase(), value);
		else
			throw new IllegalArgumentException("Not settable " + this);
	}

	public void set(IMagicCard card, Object value) {
		if (card instanceof MagicCard)
			setM((MagicCard) card, value);
		else if (card instanceof MagicCardPhysical)
			setM((MagicCardPhysical) card, value);
		else
			throw new IllegalArgumentException("Don't know this class " + card.getClass());
	}

	public static Float castToFloat(Object value) {
		if (value instanceof String)
			return Float.parseFloat((String) value);
		if (value instanceof Float)
			return (Float) value;
		if (value == null)
			return null;
		throw new ClassCastException(value.getClass().toString());
	}

	public static Integer castToInteger(Object value) {
		if (value instanceof String)
			return Integer.parseInt((String) value);
		if (value instanceof Integer)
			return (Integer) value;
		if (value == null)
			return null;
		throw new ClassCastException();
	}

	public Object get(IMagicCard card) {
		if (card instanceof MagicCard) {
			return getM((MagicCard) card);
		}
		if (card instanceof MagicCardPhysical) {
			return getM((MagicCardPhysical) card);
		}
		throw new IllegalArgumentException("Don't know this class " + card.getClass());
	}

	protected Object getM(MagicCard card) {
		if (isPhysical()) {
			CardGroup realCards = card.getRealCards();
			if (realCards != null) {
				return realCards.get(this);
			} else {
				return null;
			}
		}
		throw new IllegalArgumentException("Not implemented");
	}

	protected Object getM(MagicCardPhysical card) {
		if (isPhysical())
			throw new IllegalArgumentException("Not implemented");
		return getM(card.getBase());
	}
}
