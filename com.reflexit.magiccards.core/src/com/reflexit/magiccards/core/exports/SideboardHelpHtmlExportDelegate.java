/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk
 */
package com.reflexit.magiccards.core.exports;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
			boolean hasExtra = cards.stream().anyMatch(SideboardHelpHtmlExportDelegate::isExtra);
			// sideboard cards first, then extra, name-sorted within each
			cards.sort(Comparator.<MagicCardPhysical, Boolean>comparing(SideboardHelpHtmlExportDelegate::isExtra)
					.thenComparing(c -> String.valueOf(c.getName()).toLowerCase()));
			writeHtml(deckName(deckLoc), hasExtra ? "Sideboard and Extra" : "Sideboard", cards);
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

	private void writeHtml(String deckName, String sectionLabel, List<MagicCardPhysical> cards) {
		stream.println("<!DOCTYPE html>");
		stream.println("<html><head><meta charset=\"UTF-8\">");
		stream.println("<title>" + esc(deckName) + " - " + esc(sectionLabel) + "</title>");
		stream.println("<style>");
		// a Magic card on its side: long edge horizontal
		stream.println("  @page { size: 88mm 63mm; margin: 2.5mm; }");
		stream.println("  * { box-sizing: border-box; }");
		stream.println("  html, body { margin: 0; padding: 0; }");
		stream.println("  body { font: 3.9pt/1.15 'Arial Narrow', 'Segoe UI', Arial, sans-serif;"
				+ " color: #000; -webkit-print-color-adjust: exact; print-color-adjust: exact; }");
		stream.println("  .card { width: 83mm; height: 58mm; padding: 1.5mm; border: 0.2mm solid #999;"
				+ " overflow: hidden; page-break-after: always; }");
		stream.println("  .title { font-size: 4.6pt; font-weight: 700; margin: 0 0 0.8mm; padding-bottom: 0.4mm;"
				+ " border-bottom: 0.2mm solid #000; }");
		stream.println("  .title small { font-weight: 400; }");
		stream.println("  table { width: 100%; border-collapse: collapse; table-layout: fixed; }");
		stream.println("  th, td { text-align: left; vertical-align: top; padding: 0.15mm 0.6mm;"
				+ " overflow-wrap: break-word; }");
		stream.println("  th { font-weight: 700; text-transform: uppercase; letter-spacing: 0.1pt;"
				+ " border-bottom: 0.15mm solid #000; white-space: nowrap; }");
		stream.println("  tbody tr:nth-child(even) { background: #eee; }");
		stream.println("  .qty { width: 3.5mm; text-align: right; font-weight: 700; }");
		stream.println("  .set { width: 8mm; }");
		// special/comment only ever hold short tags ("foil", "PROXY", ...) - keep
		// them roughly as wide as their header word and give the room to Name
		stream.println("  .special { width: 8mm; }");
		stream.println("  .comment { width: 10mm; }");
		stream.println("  .empty { color: #666; font-style: italic; padding: 2mm 0; }");
		stream.println("</style></head><body>");

		stream.println("<div class=\"card\">");
		stream.println("  <div class=\"title\">" + esc(deckName) + " <small>&ndash; " + esc(sectionLabel)
				+ "</small></div>");
		if (cards.isEmpty()) {
			stream.println("  <div class=\"empty\">No " + esc(sectionLabel.toLowerCase()) + " cards.</div>");
		} else {
			stream.println("  <table>");
			stream.println("    <thead><tr>"
					+ "<th class=\"qty\">#</th><th class=\"name\">Name</th><th class=\"set\">Set</th>"
					+ "<th class=\"special\">Special</th><th class=\"comment\">Comment</th></tr></thead>");
			stream.println("    <tbody>");
			for (MagicCardPhysical c : cards) {
				stream.println("    <tr>"
						+ "<td class=\"qty\">" + c.getCount() + "</td>"
						+ "<td class=\"name\">" + esc(c.getName()) + "</td>"
						+ "<td class=\"set\">" + esc(setLabel(c.getSet())) + "</td>"
						+ "<td class=\"special\">" + esc(nz(c.getSpecial())) + "</td>"
						+ "<td class=\"comment\">" + esc(nz(c.getComment())) + "</td>"
						+ "</tr>");
			}
			stream.println("    </tbody>");
			stream.println("  </table>");
		}
		stream.println("</div>");
		stream.println("</body></html>");
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
