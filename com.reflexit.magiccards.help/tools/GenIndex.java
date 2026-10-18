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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Generates the help's keyword index (index.xml, the Index tab) from the html pages: one entry per
 * page title and per heading, plus a list of every page that talks about the main terms (Filter, Sort,
 * Columns...). Run it again after the pages change:
 * {@code java tools/GenIndex.java .} from the help plug-in folder (com.reflexit.magiccards.help).
 * Not part of the plug-in (it is not in a source folder).
 */
public class GenIndex {
	static String esc(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	static String unent(String s) {
		return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&mdash;", "-")
				.replace("&nbsp;", " ").replace("&rsaquo;", ">").replace("&gt;", ">").replaceAll("&[a-z]+;", "");
	}

	static String text(String html) {
		return unent(html.replaceAll("(?s)<!--.*?-->", " ").replaceAll("(?s)<script.*?</script>", " ").replaceAll("<[^>]*>", " ")
				.replaceAll("\\s+", " ")).trim();
	}

	// a keyword the user may look for -> terms searched in the text of every page
	static final String[][] TERMS = { { "Filter", "filter" }, { "Find", "find" }, { "Sort", "sort" }, { "Group", "group" },
			{ "Columns", "column" }, { "Search", "search" }, { "Wildcard", "wildcard" }, { "Regular expression", "regex", "m/" },
			{ "Abilities", "abilit" }, { "Traits", "trait" }, { "Color identity", "color identity" },
			{ "Extended color identity", "extended color identity", "extended identity" }, { "Produced mana", "produced mana", "produces" },
			{ "Reserved List", "reserved list" }, { "Game Changers", "game changer" }, { "EDHREC", "edhrec" },
			{ "Colors", "color" }, { "Mana", "mana" }, { "Symbols", "symbol" }, { "Rarity", "rarity" },
			{ "Finish", "finish" }, { "Foil", "foil" }, { "Condition", "condition" }, { "Proxy", "proxy", "proxies" },
			{ "Own and virtual cards", "virtual" }, { "Ownership", "ownership" }, { "Location", "location" },
			{ "Collection", "collection" }, { "Deck", "deck" }, { "Sideboard", "sideboard" }, { "Extra list", "extra list" },
			{ "Commander", "commander" }, { "Legality", "legality" }, { "Formats", "format" }, { "Tokens", "token" },
			{ "Counters", "counter" }, { "Accessories", "accessories" }, { "Printings", "printing" }, { "Set", "set filter", "sets" },
			{ "Price", "price" }, { "Buyer", "buyer" }, { "Proxier", "proxier" }, { "Collector", "collector" },
			{ "Gallery", "gallery" }, { "Card Info", "card info" }, { "Rulings", "ruling" }, { "Scryfall", "scryfall" },
			{ "Card database", "card database" }, { "Update", "update" }, { "Import", "import" }, { "Export", "export" },
			{ "Backup", "backup" }, { "Preferences", "preferences" }, { "Language", "language" }, { "Print", "print" },
			{ "What's New", "what's new" }, { "Report a bug", "report a bug", "bug" }, { "Keyboard shortcuts", "ctrl" } };

	public static void main(String[] a) throws Exception {
		Path root = Paths.get(a[0]);
		Path html = root.resolve("html");
		List<Path> pages = new ArrayList<Path>();
		try (java.util.stream.Stream<Path> w = Files.walk(html)) {
			w.filter(p -> p.toString().endsWith(".html")).sorted().forEach(pages::add);
		}
		Map<String, String> titles = new LinkedHashMap<String, String>(); // href -> title
		Map<String, String> texts = new LinkedHashMap<String, String>();
		// keyword -> ordered (href -> title)
		TreeMap<String, LinkedHashMap<String, String>> entries = new TreeMap<String, LinkedHashMap<String, String>>(String.CASE_INSENSITIVE_ORDER);
		Pattern h = Pattern.compile("(?s)<h([23])([^>]*)>(.*?)</h\\1>");
		Pattern id = Pattern.compile("id=\"([^\"]+)\"");
		for (Path p : pages) {
			String raw = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
			String href = "html/" + html.relativize(p).toString().replace('\\', '/');
			Matcher t = Pattern.compile("(?s)<title>(.*?)</title>").matcher(raw);
			String title = t.find() ? text(t.group(1)) : href;
			titles.put(href, title);
			texts.put(href, text(raw).toLowerCase(Locale.ROOT));
			// the page itself
			entries.computeIfAbsent(title, k -> new LinkedHashMap<String, String>()).put(href, title);
			// its headings
			Matcher m = h.matcher(raw);
			while (m.find()) {
				String head = text(m.group(3)).replaceFirst("^\\d+\\.\\s*", "");
				if (head.isEmpty() || head.length() > 70 || head.startsWith("\""))
					continue;
				Matcher im = id.matcher(m.group(2));
				String target = im.find() ? href + "#" + im.group(1) : href;
				entries.computeIfAbsent(head, k -> new LinkedHashMap<String, String>()).putIfAbsent(target, title + " - " + head);
			}
		}
		// concordance for the terms people look for: every page that talks about it
		TreeMap<String, LinkedHashMap<String, String>> terms = new TreeMap<String, LinkedHashMap<String, String>>(String.CASE_INSENSITIVE_ORDER);
		for (String[] term : TERMS) {
			LinkedHashMap<String, String> hits = new LinkedHashMap<String, String>();
			for (Map.Entry<String, String> e : texts.entrySet()) {
				for (int i = 1; i < term.length; i++) {
					if (e.getValue().contains(term[i])) {
						hits.put(e.getKey(), titles.get(e.getKey()));
						break;
					}
				}
			}
			if (!hits.isEmpty())
				terms.put(term[0], hits);
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<!--\n * Copyright (c) 2026 Rémi Dutil.\n * All rights reserved. This program and the accompanying materials are made\n"
				+ " * available under the terms of the Eclipse Public License v2.0 which accompanies\n * this distribution, and is available at:\n"
				+ " *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html\n *\n * Contributors:\n"
				+ " *     Rémi Dutil (2026) - keyword index of the help (the Index tab), generated from the pages\n-->\n");
		sb.append("<?NLS TYPE=\"org.eclipse.help.index\"?>\n<index>\n");
		// terms first (an entry of the same name merges with the headings)
		TreeMap<String, LinkedHashMap<String, String>> all = new TreeMap<String, LinkedHashMap<String, String>>(String.CASE_INSENSITIVE_ORDER);
		for (Map.Entry<String, LinkedHashMap<String, String>> e : terms.entrySet())
			all.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<String, String>()).putAll(e.getValue());
		for (Map.Entry<String, LinkedHashMap<String, String>> e : entries.entrySet())
			all.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<String, String>()).putAll(e.getValue());
		int topics = 0;
		for (Map.Entry<String, LinkedHashMap<String, String>> e : all.entrySet()) {
			sb.append("   <entry keyword=\"").append(esc(e.getKey())).append("\">\n");
			for (Map.Entry<String, String> t : e.getValue().entrySet()) {
				sb.append("      <topic href=\"").append(esc(t.getKey())).append("\" title=\"").append(esc(t.getValue())).append("\"/>\n");
				topics++;
			}
			sb.append("   </entry>\n");
		}
		sb.append("</index>\n");
		Files.write(root.resolve("index.xml"), sb.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println("pages: " + pages.size() + "  entries: " + all.size() + "  topics: " + topics + "  Filter -> " + (terms.get("Filter") == null ? 0 : terms.get("Filter").size()) + " pages");
	}
}
