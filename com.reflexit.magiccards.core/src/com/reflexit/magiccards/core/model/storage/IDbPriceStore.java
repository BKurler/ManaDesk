

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - getDbPriceEtched/setDbPriceEtched (3rd price bucket,
 *                         alongside normal/foil)
 */

package com.reflexit.magiccards.core.model.storage;

import java.util.Collection;
import java.util.HashMap;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.seller.IPriceProvider;
import com.reflexit.magiccards.core.seller.IPriceProviderStore;

public interface IDbPriceStore {
	IPriceProviderStore setProviderByName(String name);

	Collection<IPriceProvider> getProviders();

	IPriceProvider getProvider();

	void initialize();

	HashMap<String, String> getPriceMap(IPriceProviderStore provider);

	float getDbPrice(IMagicCard card);

	float getDbPriceFoil(IMagicCard card);

	float getDbPriceEtched(IMagicCard card);

	void setDbPrice(IMagicCard card, float price);

	void setDbPriceFoil(IMagicCard card, float price);

	void setDbPriceEtched(IMagicCard card, float price);

	boolean isInitialized();

	void reloadPrices();

}
