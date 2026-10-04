/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: a named (name, url) pair for
 *                         the "Browse Website..." favorites list (see
 *                         WebFavoritesStore). toDisplayString()/
 *                         fromDisplayString() are only used as the row text
 *                         of the preference page's list widget - actual
 *                         persistence goes through the structured name/url
 *                         fields, not this string.
 *     Rémi Dutil (2026) - a live-test run turned up real, silently corrupted
 *                         favorites: entries whose auto-suggested name (from
 *                         the page's own &lt;title&gt;, e.g. "MB oldschool
 *                         rev 2 - Deckstats Deckbuilder" - the common "Page
 *                         Title - Site Name" convention) itself contains the
 *                         " - " separator. fromDisplayString() split on the
 *                         FIRST occurrence, so the name's own trailing
 *                         "- Deckstats Deckbuilder" ended up glued onto the
 *                         front of the URL instead - and since doLoad()/
 *                         doStore() round-trip every row through this exact
 *                         method, simply opening and saving the Web
 *                         Favorites preference page was enough to trigger
 *                         it, with no editing required. Switched to the LAST
 *                         occurrence instead - a real URL essentially never
 *                         contains a literal, unencoded " - " (raw spaces
 *                         aren't valid in a URL to begin with), while a real
 *                         page title routinely does.
 *******************************************************************************/
package com.reflexit.magiccards.ui.web;

public final class WebFavorite {
	private static final String SEPARATOR = " - ";
	private final String name;
	private final String url;

	public WebFavorite(String name, String url) {
		this.name = name == null ? "" : name;
		this.url = url == null ? "" : url;
	}

	public String getName() {
		return name;
	}

	public String getUrl() {
		return url;
	}

	public String toDisplayString() {
		return name + SEPARATOR + url;
	}

	public static WebFavorite fromDisplayString(String s) {
		int i = s.lastIndexOf(SEPARATOR);
		return i < 0 ? new WebFavorite(s, "") : new WebFavorite(s.substring(0, i), s.substring(i + SEPARATOR.length()));
	}
}
