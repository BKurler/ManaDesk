/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: card finish as a searchable/filterable property
 *     Rémi Dutil (2026) - ONLY_ID: "Only" mode toggle (contains vs. is only),
 *                         same mechanism MagicCardFilter already had for
 *                         Color (ColorTypes.ONLY_ID) but had never wired to
 *                         a checkbox
 *     Rémi Dutil (2026) - replaced ONLY_ID with AND_ID: checking several
 *                         finishes now defaults to "has any of these" (OR,
 *                         unchanged) with an "And" toggle to require ALL of
 *                         them at once - not "and excludes every unchecked
 *                         one", which "Only" did and turned out not to be
 *                         the wanted meaning. Same AND_ID name/mechanism
 *                         Color already has (ColorTypes.AND_ID), again never
 *                         wired to a checkbox until now.
 *     Rémi Dutil (2026) - AND_ID redefined again, this time as an exact
 *                         match: checking several finishes with And now
 *                         requires all of them AND excludes every unchecked
 *                         one too - back to what "Only" originally did, kept
 *                         under the "And" name/label
 */
package com.reflexit.magiccards.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * Exposes {@link CardFinish} to the filter machinery as a checkbox group,
 * exactly like {@link CardConditions}. Order follows the enum (Nonfoil
 * first). Unlike Condition there's no "not set" pseudo-value - a copy's
 * finish always resolves to one of the three, see
 * {@link MagicCardPhysical#getFinish()}.
 */
public class CardFinishes implements ISearchableProperty {
	private static final CardFinishes instance = new CardFinishes();

	/**
	 * "And" mode: by default, checking several finishes matches a printing/copy
	 * that has <em>any</em> of them (OR). With this checked, it must have
	 * <em>all</em> of the checked ones at once (AND) - e.g. "Foil" + "Etched" +
	 * And matches a printing that offers both, not one that offers only one of
	 * the two. Unchecked finishes are never excluded by this - only Color's own
	 * checkbox-count-based filters do that kind of exclusion.
	 * {@link com.reflexit.magiccards.core.model.MagicCardFilter} checks for
	 * this key directly, the same way it already does for
	 * {@link ColorTypes#AND_ID}.
	 */
	public static final String AND_ID = getInstance().getPrefConstant("And");

	public static CardFinishes getInstance() {
		return instance;
	}

	private final LinkedHashMap<String, String> names = new LinkedHashMap<String, String>();

	private CardFinishes() {
		for (CardFinish f : CardFinish.values()) {
			names.put(getPrefConstant(f.getLabel()), f.getLabel());
		}
	}

	@Override
	public String getIdPrefix() {
		return getFilterField().toString();
	}

	@Override
	public FilterField getFilterField() {
		return FilterField.FINISH;
	}

	@Override
	public Collection<String> getIds() {
		return new ArrayList<String>(this.names.keySet());
	}

	@Override
	public String getNameById(String id) {
		return this.names.get(id);
	}

	public String getPrefConstant(String name) {
		return FilterField.getPrefConstant(getIdPrefix(), name);
	}
}
