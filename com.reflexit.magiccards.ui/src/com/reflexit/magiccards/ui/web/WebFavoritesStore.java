/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: persistence for the "Browse
 *                         Website..." favorites list (New Deck/Collection
 *                         wizard). One Properties file with indexed keys,
 *                         not one-file-per-entry like LoadFilterPreference-
 *                         Group's saved filters - this is a single small
 *                         ordered list edited as a whole, not "load one of
 *                         many saved snapshots". Both the preference page
 *                         and BrowseWebsiteDialog call load()/save()
 *                         directly against this same file, no cache: the
 *                         list is small and edited rarely.
 *     Rémi Dutil (2026) - added loadLastUrl()/saveLastUrl(): the dialog
 *                         should reopen on the last page the user actually
 *                         imported from (not always jump to the first
 *                         favorite) - a single extra key in the same
 *                         Properties file, since it is one more small,
 *                         rarely-changed value belonging to the same
 *                         "Browse Website..." feature.
 *     Rémi Dutil (2026) - added BUILT_IN_FAVORITES: 10 permanent, non-
 *                         editable/removable favorites - the base (not a
 *                         specific deck) URL of every site DeckTextExtractor
 *                         has been fixed to support - so a brand-new
 *                         workspace still gets useful starting points
 *                         instead of an empty list (users are expected to
 *                         favorite SITES, browse to their actual deck, then
 *                         Import - not favorite individual decks one by
 *                         one). Deliberately never written into
 *                         web-favorites.ini - load() prepends them fresh
 *                         every time (always first, in this fixed order,
 *                         ahead of the user's own saved entries) and save()
 *                         filters them back out - so they can never drift
 *                         out of sync with this list or get duplicated into
 *                         the file, and a future change to this list takes
 *                         effect immediately for every existing workspace.
 *     Rémi Dutil (2026) - added Tipsy Magic to BUILT_IN_FAVORITES - a real
 *                         site DeckTextExtractor already supports (see its
 *                         own "List"/"Flat Sorted View" fixes).
 *******************************************************************************/
package com.reflexit.magiccards.ui.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;

public class WebFavoritesStore {
	private static final String FILE_NAME = "web-favorites.ini";
	private static final String LAST_URL_KEY = "lastUrl";

	/** The 10 permanent, non-editable/removable favorites - see this class'
	 *  own header. Order here is the order they always appear in (the user's
	 *  own favorites are listed after these). */
	public static final List<WebFavorite> BUILT_IN_FAVORITES = Collections.unmodifiableList(Arrays.asList(
			new WebFavorite("Aetherhub", "https://aetherhub.com/"),
			new WebFavorite("Archidekt", "https://archidekt.com/"),
			new WebFavorite("deckbox.org", "https://deckbox.org/"),
			new WebFavorite("deckstats.net", "https://deckstats.net/"),
			new WebFavorite("Moxfield", "https://moxfield.com/"),
			new WebFavorite("MTG Decks", "https://mtgdecks.net/"),
			new WebFavorite("MTGGoldfish", "https://www.mtggoldfish.com/"),
			new WebFavorite("mtgtop8", "https://mtgtop8.com/"),
			new WebFavorite("TappedOut", "https://tappedout.net/"),
			new WebFavorite("TCGplayer", "https://www.tcgplayer.com/"),
			new WebFavorite("Tipsy Magic", "https://tipsymagic.com/")));

	/** Whether {@code url} is one of {@link #BUILT_IN_FAVORITES} - the single
	 *  source of truth WebFavoritesListEditor checks to disable Remove/Edit
	 *  for a built-in row, and save() checks to keep them out of the file. */
	public static boolean isBuiltIn(String url) {
		for (WebFavorite f : BUILT_IN_FAVORITES)
			if (f.getUrl().equals(url))
				return true;
		return false;
	}

	private WebFavoritesStore() {
	}

	private static File getFile() {
		File dir = new File(FileUtils.getMagicCardsDir(), ".settings");
		dir.mkdirs();
		return new File(dir, FILE_NAME);
	}

	private static Properties loadProps() {
		Properties props = new Properties();
		File file = getFile();
		if (!file.isFile())
			return props;
		try (FileInputStream in = new FileInputStream(file)) {
			props.load(in);
		} catch (IOException e) {
			MagicLogger.log(e);
		}
		return props;
	}

	private static void storeProps(Properties props) {
		try (FileOutputStream out = new FileOutputStream(getFile())) {
			props.store(out, "ManaDesk web favorites");
		} catch (IOException e) {
			MagicLogger.log(e);
		}
	}

	/** {@link #BUILT_IN_FAVORITES}, always first, followed by the user's own
	 *  saved entries (any stored entry that happens to match a built-in URL -
	 *  e.g. left over from before this feature existed - is skipped, so it
	 *  never appears twice). */
	public static List<WebFavorite> load() {
		List<WebFavorite> result = new ArrayList<>(BUILT_IN_FAVORITES);
		Properties props = loadProps();
		for (int i = 0;; i++) {
			String name = props.getProperty("fav." + i + ".name");
			String url = props.getProperty("fav." + i + ".url");
			if (name == null && url == null)
				break;
			if (!isBuiltIn(url))
				result.add(new WebFavorite(name, url));
		}
		return result;
	}

	/** Rewrites just the favorites entries - {@link #loadLastUrl}/{@link
	 *  #saveLastUrl} share the same file, so the existing lastUrl (if any) is
	 *  read first and carried over rather than being silently dropped. Any
	 *  {@link #BUILT_IN_FAVORITES} entry in {@code favorites} is skipped -
	 *  those are never persisted, {@link #load()} always recomputes them
	 *  fresh (this is also what keeps WebFavoritesListEditor's Remove/Edit
	 *  lockout meaningful even if a caller ever forgot to filter them out
	 *  itself). */
	public static void save(List<WebFavorite> favorites) {
		Properties props = loadProps();
		for (Object key : props.keySet().toArray())
			if (((String) key).startsWith("fav."))
				props.remove(key);
		int i = 0;
		for (WebFavorite f : favorites) {
			if (isBuiltIn(f.getUrl()))
				continue;
			props.setProperty("fav." + i + ".name", f.getName());
			props.setProperty("fav." + i + ".url", f.getUrl());
			i++;
		}
		storeProps(props);
	}

	/** The page BrowseWebsiteDialog last successfully imported from, so it can
	 *  reopen there by default instead of always jumping to the first
	 *  favorite - {@code null} if nothing has been imported yet. */
	public static String loadLastUrl() {
		return loadProps().getProperty(LAST_URL_KEY);
	}

	public static void saveLastUrl(String url) {
		Properties props = loadProps();
		if (url == null || url.isEmpty())
			props.remove(LAST_URL_KEY);
		else
			props.setProperty(LAST_URL_KEY, url);
		storeProps(props);
	}
}
