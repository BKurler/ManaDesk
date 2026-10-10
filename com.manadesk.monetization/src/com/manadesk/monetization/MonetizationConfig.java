/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil
 * All rights reserved.
 *
 * This file is NOT open-source.
 * Permission is granted to use this file ONLY as part of the ManaDesk application.
 * Modification, redistribution, or reuse of this file or its contents is prohibited.
 * You may NOT replace affiliate identifiers, ad URLs, or donation links.
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: parsed monetization configuration (hosted JSON or built-in default).
 *******************************************************************************/
package com.manadesk.monetization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * The monetization configuration: what the "Support ManaDesk" dialog shows.
 * Read from the JSON file hosted by the author (see {@link MonetizationManager})
 * or from the built-in default shipped in this plug-in. Unknown fields are
 * ignored, so a newer hosted file never breaks an older ManaDesk.
 *
 * <pre>
 * { "version": 1,
 *   "support": { "title": "...", "message": "...",
 *                "links": [ { "id", "label", "description", "url" } ] } }
 * </pre>
 * A link with an empty url is not shown.
 */
public final class MonetizationConfig {

	/** One donation / support destination. */
	public static final class SupportLink {
		private final String id;
		private final String label;
		private final String description;
		private final String url;

		SupportLink(String id, String label, String description, String url) {
			this.id = id;
			this.label = label;
			this.description = description;
			this.url = url;
		}

		public String getId() {
			return id;
		}

		public String getLabel() {
			return label;
		}

		public String getDescription() {
			return description;
		}

		public String getUrl() {
			return url;
		}
	}

	static final MonetizationConfig EMPTY = new MonetizationConfig(0, "Support ManaDesk", "",
			Collections.<SupportLink> emptyList());

	private final int version;
	private final String supportTitle;
	private final String supportMessage;
	private final List<SupportLink> supportLinks;

	private MonetizationConfig(int version, String title, String message, List<SupportLink> links) {
		this.version = version;
		this.supportTitle = title;
		this.supportMessage = message;
		this.supportLinks = Collections.unmodifiableList(links);
	}

	public int getVersion() {
		return version;
	}

	public String getSupportTitle() {
		return supportTitle;
	}

	public String getSupportMessage() {
		return supportMessage;
	}

	/** Only the links that have a url. */
	public List<SupportLink> getSupportLinks() {
		return supportLinks;
	}

	/**
	 * @throws IllegalArgumentException when {@code json} is not a usable
	 *         configuration (bad JSON, no {@code version} >= 1)
	 */
	public static MonetizationConfig parse(String json) {
		Object root;
		try {
			root = new JSONParser().parse(json);
		} catch (ParseException e) {
			throw new IllegalArgumentException("Invalid monetization configuration: " + e, e);
		}
		if (!(root instanceof JSONObject))
			throw new IllegalArgumentException("Invalid monetization configuration: not a JSON object");
		JSONObject o = (JSONObject) root;
		int version = o.get("version") instanceof Number ? ((Number) o.get("version")).intValue() : 0;
		if (version < 1)
			throw new IllegalArgumentException("Invalid monetization configuration: missing \"version\"");
		String title = EMPTY.supportTitle;
		String message = "";
		List<SupportLink> links = new ArrayList<>();
		Object support = o.get("support");
		if (support instanceof JSONObject) {
			JSONObject s = (JSONObject) support;
			title = str(s.get("title"), title);
			message = str(s.get("message"), "");
			Object arr = s.get("links");
			if (arr instanceof JSONArray) {
				for (Object l : (JSONArray) arr) {
					if (!(l instanceof JSONObject))
						continue;
					JSONObject lo = (JSONObject) l;
					String url = str(lo.get("url"), "").trim();
					String label = str(lo.get("label"), "").trim();
					if (url.isEmpty() || label.isEmpty() || !isWebUrl(url))
						continue;
					links.add(new SupportLink(str(lo.get("id"), label), label, str(lo.get("description"), ""), url));
				}
			}
		}
		return new MonetizationConfig(version, title, message, links);
	}

	private static String str(Object o, String def) {
		return o instanceof String ? (String) o : def;
	}

	/** Only plain https/http links are ever opened (never file:, javascript: ...). */
	static boolean isWebUrl(String url) {
		String u = url.toLowerCase(java.util.Locale.ENGLISH);
		return u.startsWith("https://") || u.startsWith("http://");
	}
}
