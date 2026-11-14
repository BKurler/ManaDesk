/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - owns DETAILS_QUERY_URL_BASE (moved from the removed ParseGathererOracle)
 *     Rémi Dutil (2026) - removed the Gatherer search/scraping code (getSearchQuery, htmlToString, saveManaSymbol,
 *                         patterns): nothing called it
 */
package com.reflexit.magiccards.core.sync;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.NotNull;

public abstract class GatherHelper extends ParserHtmlHelper {
	// !!! RD This is not used anymore... so I force http instead of http just for
	// investigating links support
	public static final String GATHERER_URL_BASE = "http://gatherer.wizards.com/";
	public static final String DETAILS_QUERY_URL_BASE = GATHERER_URL_BASE + "Pages/Card/Details.aspx?multiverseid=";

	public GatherHelper() {
		super();
	}

	@NotNull
	public static URL createImageURL(int cardId) {
		try {
			return new URL(GATHERER_URL_BASE + "Handlers/Image.ashx?multiverseid=" + cardId + "&type=card");
		} catch (MalformedURLException e) {
			throw new MagicException(e);
		}
	}

	public static URL createImageDetailURL(String cardId) {
		try {
			return new URL(DETAILS_QUERY_URL_BASE + cardId);
		} catch (MalformedURLException e) {
			throw new MagicException(e);
		}
	}

	public static String extractCardIdFromURL(URL url) {
		String query = url.getQuery();

		// RD Updated to support Scryfall card ID that contains letters, numbers, dashes
		// and underscore
		Pattern pattern = Pattern.compile("multiverseid=([\\-a-zA-Z0-9_]+)");
		Matcher matcher = pattern.matcher(query);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	public static URL createSetImageURL(String editionAbbr, String rarity) {
		try {
			String rarLetter = rarity == null ? "C" : rarity.substring(0, 1).toUpperCase();
			return new URL(GATHERER_URL_BASE + "Handlers/Image.ashx?type=symbol&set=" + editionAbbr
					+ "&size=small&rarity=" + rarLetter);
		} catch (MalformedURLException e) {
			throw new MagicException(e);
		}
	}

	public static URL createManaImageURL(String symbol) {
		String manaName = symbol.replaceAll("[{}/]", "");
		try {
			return new URL(GATHERER_URL_BASE + "Handlers/Image.ashx?size=small&name=" + manaName + "&type=symbol");
		} catch (MalformedURLException e) {
			return null;
		}
	}
}