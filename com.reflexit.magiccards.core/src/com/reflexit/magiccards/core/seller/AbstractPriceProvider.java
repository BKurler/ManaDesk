

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - getDbPriceEtched/setDbPriceEtched: the priceMap value
 *                         grew a 3rd ':'-joined segment (normal:foil:etched);
 *                         reworked around shared readSegment/writeSegment
 *                         helpers so all three read/write the same way (a
 *                         missing 3rd segment on an older cached entry reads
 *                         back as 0, same as a missing entry always has)
 */

package com.reflexit.magiccards.core.seller;

import java.io.IOException;
import java.net.URL;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.exports.ClassicNoXExportDelegate;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.storage.IDbPriceStore;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.monitor.SubCoreProgressMonitor;
import com.reflexit.magiccards.core.sync.CurrencyConvertor;
import com.reflexit.magiccards.core.xml.PricesXmlStreamWriter;

public class AbstractPriceProvider implements IPriceProvider {
	protected String name;
	protected final HashMap<String, String> priceMap;
	protected final Properties properties;

	public AbstractPriceProvider(String name) {
		this.name = name;
		this.properties = new Properties();
		this.priceMap = new HashMap<>();
	}

	@Override
	public Currency getCurrency() {
		String cur = getProperties().getProperty("currency");
		if (cur == null)
			return CurrencyConvertor.USD;
		return Currency.getInstance(cur);
	}

	@Override
	public void updatePricesAndSync(Iterable<IMagicCard> iterable, ICoreProgressMonitor monitor) throws IOException {
		monitor.beginTask("Loading prices from " + getURL() + " ...", 200);
		try {
			Iterable<IMagicCard> res = updatePrices(iterable, new SubCoreProgressMonitor(monitor, 100));
			if (res != null) {
				save();
				sync(res, new SubCoreProgressMonitor(monitor, 100));
			}
		} finally {
			monitor.done();
		}
	}

	@Override
	public void Sync(Iterable<IMagicCard> iterable, ICoreProgressMonitor monitor) throws IOException {
		monitor.beginTask("Sync prices", 200);
		try {
			sync(iterable, new SubCoreProgressMonitor(monitor, 100));
		} finally {
			monitor.done();
		}
	}

	public int getSize(Iterable<IMagicCard> iterable) {
		int size = 0;
		for (IMagicCard magicCard : iterable) {
			size++;
		}
		return size;
	}

	public Set<String> getSets(Iterable<IMagicCard> iterable) {
		HashSet<String> sets = new HashSet();
		for (IMagicCard magicCard : iterable) {
			String set = magicCard.getSet();
			sets.add(set);
		}
		return sets;
	}

	public void sync(Iterable<IMagicCard> res, ICoreProgressMonitor monitor) {
		IDbPriceStore dbPriceStore = DataManager.getDBPriceStore();
		if (dbPriceStore.getProvider().equals(this))
			dbPriceStore.reloadPrices();
	}

	public Iterable<IMagicCard> updatePrices(Iterable<IMagicCard> iterable, ICoreProgressMonitor monitor)
			throws IOException {
		throw new MagicException("This price provider " + name + " does not support interactive update");
	}

	@Override
	public URL getURL() {
		return null;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public URL buy(Iterable<IMagicCard> cards) {
		return null;
	}

	@Override
	public String export(Iterable<IMagicCard> cards) {
		String res = new ClassicNoXExportDelegate().export(cards);
		return res;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	// The priceMap value is 3 ':'-joined segments: normal:foil:etched (in the
	// provider's own currency, see getCurrency()). Segment 0/1 are the original
	// format; segment 2 (etched) was added later - readSegment() defaults a
	// missing 3rd segment to 0, so older cached entries keep working untouched.
	private static final int SEG_NORMAL = 0, SEG_FOIL = 1, SEG_ETCHED = 2;

	private float readSegment(String id, int segment) {
		String prices = priceMap.get(id);
		if (prices == null)
			return 0f;
		String[] parts = prices.split(":", -1);
		if (segment >= parts.length || parts[segment].isEmpty())
			return 0f;
		try {
			return Float.parseFloat(parts[segment]);
		} catch (NumberFormatException e) {
			return 0f;
		}
	}

	private synchronized void writeSegment(String id, int segment, float value, Currency cur) {
		float[] segs = { readSegment(id, SEG_NORMAL), readSegment(id, SEG_FOIL), readSegment(id, SEG_ETCHED) };
		float converted = CurrencyConvertor.convertFromInto(value, cur, getCurrency());
		segs[segment] = converted;
		if (segs[SEG_NORMAL] == 0 && segs[SEG_FOIL] == 0 && segs[SEG_ETCHED] == 0) {
			priceMap.remove(id);
		} else {
			priceMap.put(id, segs[SEG_NORMAL] + ":" + segs[SEG_FOIL] + ":" + segs[SEG_ETCHED]);
		}
	}

	private synchronized float readPrice(String id, int segment, Currency cur) {
		if (!priceMap.containsKey(id))
			return 0f;
		float price = readSegment(id, segment);
		return CurrencyConvertor.convertFromInto(price, getCurrency(), cur);
	}

	@Override
	public void setDbPrice(String id, float price, Currency cur) {
		writeSegment(id, SEG_NORMAL, price, cur);
	}

	@Override
	public void setDbPriceFoil(String id, float price, Currency cur) {
		writeSegment(id, SEG_FOIL, price, cur);
	}

	@Override
	public void setDbPriceEtched(String id, float price, Currency cur) {
		writeSegment(id, SEG_ETCHED, price, cur);
	}

	@Override
	public void setDbPrice(IMagicCard magicCard, float price, Currency cur) {
		setDbPrice(magicCard.getCardId(), price, cur);
	}

	@Override
	public void setDbPriceFoil(IMagicCard magicCard, float price, Currency cur) {
		setDbPriceFoil(magicCard.getCardId(), price, cur);
	}

	@Override
	public void setDbPriceEtched(IMagicCard magicCard, float price, Currency cur) {
		setDbPriceEtched(magicCard.getCardId(), price, cur);
	}

	@Override
	public float getDbPrice(IMagicCard card, Currency cur) {
		return readPrice(card.getCardId(), SEG_NORMAL, cur);
	}

	@Override
	public float getDbPrice(String id, Currency cur) {
		return readPrice(id, SEG_NORMAL, cur);
	}

	@Override
	public float getDbPriceFoil(IMagicCard card, Currency cur) {
		return readPrice(card.getCardId(), SEG_FOIL, cur);
	}

	@Override
	public float getDbPriceFoil(String id, Currency cur) {
		return readPrice(id, SEG_FOIL, cur);
	}

	@Override
	public float getDbPriceEtched(IMagicCard card, Currency cur) {
		return readPrice(card.getCardId(), SEG_ETCHED, cur);
	}

	@Override
	public float getDbPriceEtched(String id, Currency cur) {
		return readPrice(id, SEG_ETCHED, cur);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractPriceProvider other = (AbstractPriceProvider) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	public static transient PricesXmlStreamWriter writer = new PricesXmlStreamWriter();

	@Override
	public void save() throws IOException {
		writer.write(this);
	}

	@Override
	public HashMap<String, String> getPriceMap() {
		return priceMap;
	}

	@Override
	public Properties getProperties() {
		return properties;
	}
}
