/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: card condition as a searchable/filterable property
 */
package com.reflexit.magiccards.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * Exposes {@link CardCondition} to the filter machinery as a checkbox group,
 * exactly like {@link Rarity}. Order follows the enum (Near Mint first).
 */
public class CardConditions implements ISearchableProperty {
	private static final CardConditions instance = new CardConditions();

	/** Pseudo-value for "match copies with no grade" - handled specially in {@link FilterField}. */
	public static final String NOT_GRADED = "Not graded";

	public static CardConditions getInstance() {
		return instance;
	}

	private final LinkedHashMap<String, String> names = new LinkedHashMap<String, String>();

	private CardConditions() {
		for (CardCondition c : CardCondition.values()) {
			names.put(getPrefConstant(c.getLabel()), c.getLabel());
		}
		names.put(getPrefConstant(NOT_GRADED), NOT_GRADED);
	}

	@Override
	public String getIdPrefix() {
		return getFilterField().toString();
	}

	@Override
	public FilterField getFilterField() {
		return FilterField.CONDITION;
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
