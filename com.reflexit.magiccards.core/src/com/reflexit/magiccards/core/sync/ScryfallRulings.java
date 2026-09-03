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
package com.reflexit.magiccards.core.sync;

import java.io.IOException;
import java.net.URL;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Turns a Scryfall <em>rulings</em> endpoint
 * ({@code https://api.scryfall.com/cards/<id>/rulings}) into a small HTML
 * fragment - one {@code <li>} per ruling, newest formatting kept simple - so it
 * can be shown in the card description browser instead of raw JSON.
 */
public final class ScryfallRulings {

	private ScryfallRulings() {
	}

	/** @return {@code true} for a Scryfall rulings endpoint URL. */
	public static boolean isRulingsUrl(String url) {
		return url != null && url.contains("scryfall.io/") == false && url.contains("scryfall.com/")
				&& url.endsWith("/rulings");
	}

	/**
	 * Fetch the rulings JSON and render it as an HTML {@code <ul>} (or a short
	 * "no rulings" paragraph). Ruling comments are HTML-escaped but mana-symbol
	 * braces like <code>{T}</code> are kept for the symbol renderer.
	 */
	public static String fetchAsHtml(String rulingsUrl) throws IOException {
		String json = WebUtils.openUrlText(new URL(rulingsUrl));
		try {
			JSONObject top = (JSONObject) new JSONParser().parse(json);
			JSONArray data = (JSONArray) top.get("data");
			if (data == null || data.isEmpty())
				return "<p>No rulings for this card.</p>";

			StringBuilder sb = new StringBuilder("<ul class=\"rulings\">");
			for (Object o : data) {
				JSONObject r = (JSONObject) o;
				String date = text(r.get("published_at"));
				String source = text(r.get("source"));
				String comment = escapeHtml(text(r.get("comment")));
				sb.append("<li>");
				if (!date.isEmpty())
					sb.append("<b>").append(date).append("</b> &ndash; ");
				sb.append(comment);
				if (!source.isEmpty() && !"wotc".equalsIgnoreCase(source))
					sb.append(" <i>(").append(source).append(")</i>");
				sb.append("</li>");
			}
			sb.append("</ul>");
			return sb.toString();
		} catch (ParseException e) {
			throw new IOException("Unexpected rulings response from Scryfall", e);
		}
	}

	private static String text(Object o) {
		return o == null ? "" : o.toString().trim();
	}

	private static String escapeHtml(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
