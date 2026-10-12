/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - laid out on the shared PrintSheet: each deck's list is
 *                         one card-sized slot (upright, text sideways), 9 per
 *                         page with proxies, printed at 100%
 *******************************************************************************/
package com.reflexit.magiccards.core.exports;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.ILocatable;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Produces a tiny, card-sized printable "Print Sideboard List" - a reference
 * you cut out and drop into the deck box so you don't have to memorize every
 * deck's sideboard. The page is sized to a Magic card turned on its side
 * (88mm x 63mm) so the text runs along the card's long edge, and lists every
 * sideboard card with its quantity, name, set, special flags and comment.
 */
public class SideboardHelpHtmlExportDelegate extends AbstractExportDelegate<IMagicCard> {

	@Override
	public void export(ICoreProgressMonitor monitor) throws InvocationTargetException {
		if (monitor == null)
			monitor = ICoreProgressMonitor.NONE;
		monitor.beginTask("Exporting sideboard help...", 100);
		try {
			Location deckLoc = resolveDeckLocation();
			List<MagicCardPhysical> cards = collectSideboard(deckLoc);
			// group by deck so a combined multi-deck export gets one printable
			// card per deck (each `.card` div is its own page-break)
			Map<Location, List<MagicCardPhysical>> byDeck = new LinkedHashMap<>();
			for (MagicCardPhysical c : cards) {
				Location k = c.getLocation() != null ? c.getLocation().toMainDeck() : deckLoc;
				byDeck.computeIfAbsent(k == null ? Location.NO_WHERE : k, x -> new ArrayList<>()).add(c);
			}
			List<String> slots = new ArrayList<>();
			if (byDeck.isEmpty()) {
				slots.add(cardSlot(deckName(deckLoc), "Sideboard", new ArrayList<>()));
			} else {
				for (Map.Entry<Location, List<MagicCardPhysical>> e : byDeck.entrySet()) {
					List<MagicCardPhysical> group = e.getValue();
					boolean hasExtra = group.stream().anyMatch(SideboardHelpHtmlExportDelegate::isExtra);
					// sideboard cards first, then extra, name-sorted within each
					group.sort(Comparator.<MagicCardPhysical, Boolean>comparing(SideboardHelpHtmlExportDelegate::isExtra)
							.thenComparing(c -> String.valueOf(c.getName()).toLowerCase()));
					slots.add(cardSlot(e.getKey().getName(), hasExtra ? "Sideboard and Extra" : "Sideboard", group));
				}
			}
			PrintSheet.write(stream, "Sideboard list", slots);
		} finally {
			monitor.done();
		}
	}

	private static boolean isExtra(MagicCardPhysical c) {
		return c.getLocation() != null && c.getLocation().isExtra();
	}

	private Location resolveDeckLocation() {
		if (store != null && store.getSize() > 0) {
			Object first = store.iterator().next();
			if (first instanceof ILocatable) {
				Location loc = ((ILocatable) first).getLocation();
				if (loc != null)
					return loc.toMainDeck();
			}
		}
		return location != null ? location.toMainDeck() : null;
	}

	/**
	 * Sideboard (and, when the caller put them in the store, extra) cards come
	 * from the store the Export tab / wizard already filtered. If that turned
	 * up nothing - e.g. "include sideboard" was left off in the wizard - fall
	 * back to reading the sideboard list straight off disk so this export
	 * still does something useful.
	 */
	private List<MagicCardPhysical> collectSideboard(Location deckLoc) {
		List<MagicCardPhysical> res = new ArrayList<>();
		if (store != null) {
			for (Object o : store) {
				if (o instanceof MagicCardPhysical) {
					MagicCardPhysical mcp = (MagicCardPhysical) o;
					Location l = mcp.getLocation();
					if (l != null && (l.isSideboard() || l.isExtra()))
						res.add(mcp);
				}
			}
		}
		if (!res.isEmpty() || deckLoc == null || deckLoc.equals(Location.NO_WHERE)
				|| deckLoc.getName() == null || deckLoc.getName().isEmpty())
			return res;
		try {
			ICardStore<IMagicCard> sb = DataManager.getInstance().getCardStore(deckLoc.toSideboard());
			if (sb != null) {
				for (IMagicCard c : sb) {
					if (c instanceof MagicCardPhysical)
						res.add((MagicCardPhysical) c);
				}
			}
		} catch (RuntimeException e) {
			// no sideboard for this deck - render the empty state
		}
		return res;
	}

	private String deckName(Location deckLoc) {
		if (deckLoc != null && deckLoc.getName() != null && !deckLoc.getName().isEmpty())
			return deckLoc.getName();
		return getName();
	}

	/** One deck's list: a card-sized slot of the sheet ({@code .sb}, styled by PrintSheet). */
	private String cardSlot(String deckName, String sectionLabel, List<MagicCardPhysical> cards) {
		StringBuilder sb = new StringBuilder();
		sb.append("<div class=\"sb\">\n");
		sb.append("  <div class=\"title\">").append(esc(deckName)).append(" <small>&ndash; ").append(esc(sectionLabel))
				.append("</small></div>\n");
		if (cards.isEmpty()) {
			sb.append("  <div class=\"empty\">No ").append(esc(sectionLabel.toLowerCase())).append(" cards.</div>\n");
		} else {
			sb.append("  <table>\n");
			sb.append("    <thead><tr>"
					+ "<th class=\"qty\">#</th><th class=\"name\">Name</th><th class=\"set\">Set</th>"
					+ "<th class=\"special\">Special</th><th class=\"comment\">Comment</th></tr></thead>\n");
			sb.append("    <tbody>\n");
			for (MagicCardPhysical c : cards) {
				sb.append("    <tr>").append("<td class=\"qty\">").append(c.getCount()).append("</td>")
						.append("<td class=\"name\">").append(esc(c.getName())).append("</td>")
						.append("<td class=\"set\">").append(esc(setLabel(c.getSet()))).append("</td>")
						.append("<td class=\"special\">").append(esc(nz(c.getSpecial()))).append("</td>")
						.append("<td class=\"comment\">").append(esc(nz(c.getComment()))).append("</td>")
						.append("</tr>\n");
			}
			sb.append("    </tbody>\n");
			sb.append("  </table>\n");
		}
		sb.append("</div>");
		return sb.toString();
	}

	private static String setLabel(String setName) {
		if (setName == null || setName.isEmpty())
			return "";
		String abbr = Editions.getInstance().getAbbrByName(setName);
		return abbr != null && !abbr.isEmpty() ? abbr : setName;
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}

	private static String esc(String s) {
		if (s == null)
			return "";
		StringBuilder sb = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '&':
				sb.append("&amp;");
				break;
			case '<':
				sb.append("&lt;");
				break;
			case '>':
				sb.append("&gt;");
				break;
			case '"':
				sb.append("&quot;");
				break;
			default:
				sb.append(c);
			}
		}
		return sb.toString();
	}

	@Override
	public String getContentSlug() {
		return "sideboard-list";
	}

	@Override
	public boolean isSideboardOnly() {
		return true;
	}

	@Override
	public boolean isCombineByDefault() {
		return true;
	}

	@Override
	public boolean isPrintSheet() {
		return true;
	}

	@Override
	public boolean isColumnChoiceSupported() {
		return false;
	}

	@Override
	public boolean isMultipleLocationSupported() {
		return false;
	}

	@Override
	public boolean isSideboardSupported() {
		return true;
	}
}
