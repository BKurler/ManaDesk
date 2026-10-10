/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - sync(): a retired price source saved in the
 *                         preferences falls back to the default source;
 *                         the display currency follows the source (Cardmarket
 *                         EUR, TCGplayer USD).
 */
package com.reflexit.magiccards.ui.preferences;

import java.util.Collection;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.seller.IPriceProvider;
import com.reflexit.magiccards.core.seller.IPriceProviderStore;
import com.reflexit.magiccards.ui.MagicUIActivator;

public class PriceProviderManager implements IPropertyChangeListener {
	static private final PriceProviderManager instance = new PriceProviderManager();

	public static final PriceProviderManager getInstance() {
		return instance;
	}

	public String getProviderName() {
		String name = MagicUIActivator.getDefault().getPreferenceStore()
				.getString(PreferenceConstants.PRICE_PROVIDER);
		return name;
	}

	public void setProviderName(String name) {
		MagicUIActivator.getDefault().getPreferenceStore().setValue(PreferenceConstants.PRICE_PROVIDER, name);
	}

	@Override
	public void propertyChange(org.eclipse.jface.util.PropertyChangeEvent event) {
		String property = event.getProperty();
		Object newValue = event.getNewValue();
		if (property.equals(PreferenceConstants.PRICE_PROVIDER)) {
			if (newValue != null && !newValue.equals(event.getOldValue())) {
				useSourceCurrency((String) newValue); // before the reload the switch triggers
				DataManager.getDBPriceStore().setProviderByName((String) newValue);
			}
		}
	}

	public void sync(IPreferenceStore preferenceStore) {
		// a source that no longer exists (old TCG Player Low, MOTL, ...): back to the default
		if (!com.reflexit.magiccards.core.seller.PriceSources.isKnown(getProviderName()))
			preferenceStore.setToDefault(PreferenceConstants.PRICE_PROVIDER);
		String providerName = getProviderName();
		if (providerName != null) {
			useSourceCurrency(providerName);
			DataManager.getDBPriceStore().setProviderByName(providerName);
		}
		preferenceStore.addPropertyChangeListener(this);
	}

	/**
	 * Prices are shown in the selected source's own currency (Cardmarket: EUR,
	 * TCGplayer: USD) - there is no separate currency setting in the UI, and
	 * showing Cardmarket's euro prices converted to US$ made no sense.
	 */
	private static void useSourceCurrency(String sourceName) {
		com.reflexit.magiccards.core.sync.CurrencyConvertor
				.setCurrency(com.reflexit.magiccards.core.seller.PriceSources.currencyOf(sourceName));
	}

	public Collection<IPriceProvider> getProviders() {
		return DataManager.getDBPriceStore().getProviders();
	}

	public IPriceProviderStore getDefaultProvider() {
		return getProviders().iterator().next();
	}
}
