/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - getDbPriceEtched/setDbPriceEtched (3rd price bucket,
 *                         alongside normal/foil)
 */

package com.reflexit.magiccards.core.seller;

import java.io.IOException;
import java.net.URL;
import java.util.Currency;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

public interface IPriceProvider extends IPriceProviderStore {
	URL getURL();

	URL buy(Iterable<IMagicCard> cards);

	public String export(Iterable<IMagicCard> cards);

	/**
	 * Update prices for given card list in price storage for actve provider and
	 * dbprice on the list
	 * 
	 * @param iterable
	 *            - if not null - used to get exact card list to update prices
	 *            for
	 * @param monitor
	 *            - progress monitor
	 * @throws IOException
	 */
	public void updatePricesAndSync(Iterable<IMagicCard> iterable, ICoreProgressMonitor monitor) throws IOException;

	public void Sync(Iterable<IMagicCard> iterable, ICoreProgressMonitor monitor) throws IOException;

	void save() throws IOException;

	void setDbPrice(IMagicCard card, float price, Currency cur);

	void setDbPrice(String id, float price, Currency cur);

	void setDbPriceFoil(IMagicCard card, float price, Currency cur);

	void setDbPriceFoil(String id, float price, Currency cur);

	void setDbPriceEtched(IMagicCard card, float price, Currency cur);

	void setDbPriceEtched(String id, float price, Currency cur);

	float getDbPrice(IMagicCard card, Currency cur);

	float getDbPrice(String id, Currency cur);

	float getDbPriceFoil(IMagicCard card, Currency cur);

	float getDbPriceFoil(String id, Currency cur);

	float getDbPriceEtched(IMagicCard card, Currency cur);

	float getDbPriceEtched(String id, Currency cur);

	Currency getCurrency();

}
