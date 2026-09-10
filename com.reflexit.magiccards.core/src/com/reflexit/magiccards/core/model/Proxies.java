/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: proxy flag as a filterable property
 */
package com.reflexit.magiccards.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * Exposes the per-copy proxy flag to the filter machinery as a two-entry
 * checkbox group ("Genuine" / "Proxy"), exactly like {@link Rarity} /
 * {@link CardConditions}.
 */
public class Proxies implements ISearchableProperty {
	private static final Proxies instance = new Proxies();

	public static final String GENUINE = "Genuine";
	public static final String PROXY = "Proxy";

	public static Proxies getInstance() {
		return instance;
	}

	private final LinkedHashMap<String, String> names = new LinkedHashMap<String, String>();

	private Proxies() {
		names.put(getPrefConstant(GENUINE), GENUINE);
		names.put(getPrefConstant(PROXY), PROXY);
	}

	@Override
	public String getIdPrefix() {
		return getFilterField().toString();
	}

	@Override
	public FilterField getFilterField() {
		return FilterField.PROXY;
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
