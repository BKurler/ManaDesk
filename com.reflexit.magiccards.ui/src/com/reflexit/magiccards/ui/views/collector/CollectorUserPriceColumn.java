/*
 * Contributors:
 *     Rémi Dutil (2026) - Collector view "User Price" = your valuation of the copies you own,
 *                         proxy copies excluded unless "Count Proxies" is on
 */
package com.reflexit.magiccards.ui.views.collector;

import java.text.DecimalFormat;
import java.util.Currency;

import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.core.sync.CurrencyConvertor;
import com.reflexit.magiccards.ui.views.columns.PriceColumn;

/**
 * "User Price" in the Collector view: your own valuation summed over the copies
 * you <b>own</b> (virtual excluded), and - unless "Count Proxies" is on - over
 * genuine copies only. A base {@link MagicCard} has no User Price of its own, so
 * this walks its physical copies.
 */
public class CollectorUserPriceColumn extends PriceColumn {

	@Override
	public String getColumnFullName() {
		return "User Price";
	}

	@Override
	public String getText(Object element) {
		double value = ownedValue(element);
		if (value <= 0)
			return "";
		Currency cur = CurrencyConvertor.getCurrency();
		return cur.getSymbol() + " " + new DecimalFormat("#0.00").format(value);
	}

	private static double ownedValue(Object element) {
		boolean countProxies = CollectorView.isCountProxies();
		if (element instanceof ICardGroup) {
			double sum = 0;
			for (ICard child : ((ICardGroup) element).getChildrenList())
				sum += ownedValue(child);
			return sum;
		}
		if (element instanceof MagicCardPhysical) {
			MagicCardPhysical p = (MagicCardPhysical) element;
			if (!p.isOwn() || (p.isProxy() && !countProxies))
				return 0;
			return unit(p.getPrice()) * p.getCount();
		}
		if (element instanceof MagicCard) {
			double sum = 0;
			for (MagicCardPhysical p : ((MagicCard) element).getPhysicalCards())
				sum += ownedValue(p);
			return sum;
		}
		return 0;
	}

	private static double unit(float price) {
		return price > 0 ? price : 0;
	}
}
