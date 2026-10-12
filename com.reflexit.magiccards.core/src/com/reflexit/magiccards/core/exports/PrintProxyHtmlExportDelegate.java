/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - getCardImageUrl(): no NPE when
 *                         CardCache.getImageURL() returns null; falls back to
 *                         the remote image URL (the exported page may be
 *                         opened when the web is back).
 *     Rémi Dutil (2026) - laid out on the shared PrintSheet: one card image per
 *                         60 x 83.8 mm slot (a little smaller than a card, to
 *                         fit a sleeve), 9 per page, printed at 100% - with
 *                         sideboard lists when appended to the same file
 */

package com.reflexit.magiccards.core.exports;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.abs.ICardCountable;
import com.reflexit.magiccards.core.model.storage.ILocatable;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.sync.CardCache;

public class PrintProxyHtmlExportDelegate extends AbstractExportDelegate<IMagicCard> {
	public static final String CARD_URI = "card://";
	public static final String CARDID = "cardId=";

	@Override
	public void export(ICoreProgressMonitor monitor) throws InvocationTargetException {
		monitor.beginTask("Exporting " + getName(), 100);
		try {
			if (store.getCardStore().size() > 0) {
				IMagicCard card = store.getCardStore().iterator().next();
				if (card instanceof ILocatable) {
					location = ((ILocatable) card).getLocation();
					if (location != null)
						location = location.toMainDeck();
				}
			}
			List<String> slots = new ArrayList<>();
			for (IMagicCard card : store) {
				int count = card instanceof ICardCountable ? ((ICardCountable) card).getCount() : 1;
				String img = "<img class=\"proxy\" src=\"" + PrintSheet.esc(nz(getCardImageUrl(card))) + "\" alt=\""
						+ PrintSheet.esc(card.getName()) + "\">";
				for (int i = 0; i < count; i++)
					slots.add(img);
			}
			PrintSheet.write(stream, "Print Proxies", slots);
		} finally {
			monitor.done();
		}
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}

	protected String getCardImageUrl(IMagicCard card) {
		/*-
		<img src="https://magiccards.info/scans/en/m14/102.jpg" alt="Name" />		
		 */
		String url = "";
		try {
			URL u = CardCache.getImageURL(card);
			if (u == null) // web not accessible now - the exported page may be opened later
				u = CardCache.createRemoteImageURL(card);
			if (u != null)
				url = u.toExternalForm();
		} catch (MalformedURLException e) {
			// oki
		}
		return url;
	}

	@Override
	public boolean isColumnChoiceSupported() {
		return false;
	}

	@Override
	public boolean isMultipleLocationSupported() {
		return true;
	}

	@Override
	public boolean isSideboardSupported() {
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
}
