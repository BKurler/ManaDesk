/*
 * Contributors:
 *     Rémi Dutil (2026) - Collector view "Online Price" = market value of the copies you own
 */
package com.reflexit.magiccards.ui.views.collector;

import java.text.DecimalFormat;
import java.util.Currency;

import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.core.sync.CurrencyConvertor;
import com.reflexit.magiccards.ui.views.columns.SellerPriceColumn;

/**
 * "Online Price" in the Collector view: the market value of the copies you
 * actually <b>own</b> (virtual copies excluded), summed - i.e. what your holdings
 * of that printing / set are worth. Contrast the plain {@link SellerPriceColumn},
 * whose Collector group total is a catalog sum ("buy one of everything") because
 * Collector rows are base cards with a hard-coded count of 1.
 */
public class CollectorOnlinePriceColumn extends SellerPriceColumn {

	@Override
	public String getColumnFullName() {
		return "Online Price";
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
		if (element instanceof ICardGroup) {
			double sum = 0;
			for (ICard child : ((ICardGroup) element).getChildrenList())
				sum += ownedValue(child);
			return sum;
		}
		if (element instanceof MagicCardPhysical) {
			MagicCardPhysical p = (MagicCardPhysical) element;
			return p.isOwn() ? unit(p.getDbPrice()) * p.getCount() : 0;
		}
		if (element instanceof MagicCard) {
			MagicCard mc = (MagicCard) element;
			return unit(mc.getDbPrice()) * mc.getOwnCount(); // getOwnCount excludes virtual copies
		}
		return 0;
	}

	private static double unit(float price) {
		return price > 0 ? price : 0; // drop the -1 / -0.0001 "no data" sentinels
	}
}
