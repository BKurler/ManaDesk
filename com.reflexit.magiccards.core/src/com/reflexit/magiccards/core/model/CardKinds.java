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
import java.util.LinkedHashMap;

/**
 * Exposes the paper / digital kind of a printing to the filter machinery as a
 * two-entry checkbox group, like {@link Proxies}:
 * <ul>
 * <li>"Paper": the printing exists on paper (everything but the digital-only cards);</li>
 * <li>"Digital": it exists on MTGO or Arena (digital-only, or a paper card also
 * playable online - see {@link DigitalSupport}).</li>
 * </ul>
 * A paper card that is also online is in both; checking both shows everything.
 */
public class CardKinds implements ISearchableProperty {
	private static final CardKinds instance = new CardKinds();

	public static final String PAPER = "Paper";
	public static final String DIGITAL = "Digital";

	public static CardKinds getInstance() {
		return instance;
	}

	private final LinkedHashMap<String, String> names = new LinkedHashMap<String, String>();

	private CardKinds() {
		names.put(getPrefConstant(PAPER), PAPER);
		names.put(getPrefConstant(DIGITAL), DIGITAL);
	}

	/** The preference key of the "Paper" checkbox. */
	public static String paperId() {
		return instance.getPrefConstant(PAPER);
	}

	@Override
	public String getIdPrefix() {
		return getFilterField().toString();
	}

	@Override
	public FilterField getFilterField() {
		return FilterField.DIGITAL;
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
