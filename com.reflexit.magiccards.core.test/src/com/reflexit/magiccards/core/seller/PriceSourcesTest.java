/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - PriceSources: default source per locale, labels,
 *                         Cardmarket in EUR.
 *******************************************************************************/
package com.reflexit.magiccards.core.seller;

import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

public class PriceSourcesTest {

	@Test
	public void europeGetsCardmarketOthersTcgplayer() {
		Assert.assertEquals(PriceSources.CARDMARKET, PriceSources.defaultFor(Locale.FRANCE));
		Assert.assertEquals(PriceSources.CARDMARKET, PriceSources.defaultFor(Locale.GERMANY));
		Assert.assertEquals(PriceSources.CARDMARKET, PriceSources.defaultFor(Locale.UK));
		Assert.assertEquals(PriceSources.TCGPLAYER, PriceSources.defaultFor(Locale.CANADA_FRENCH));
		Assert.assertEquals(PriceSources.TCGPLAYER, PriceSources.defaultFor(Locale.US));
		Assert.assertEquals(PriceSources.TCGPLAYER, PriceSources.defaultFor(Locale.ENGLISH)); // no country
		Assert.assertEquals(PriceSources.TCGPLAYER, PriceSources.defaultFor(null));
	}

	@Test
	public void labelsAndCurrencies() {
		Assert.assertEquals("TCGplayer (USD)", PriceSources.label(PriceSources.TCGPLAYER));
		Assert.assertEquals("Cardmarket (EUR)", PriceSources.label(PriceSources.CARDMARKET));
		Assert.assertEquals("USD", PriceSources.create(PriceSources.TCGPLAYER).getCurrency().getCurrencyCode());
		Assert.assertEquals("EUR", PriceSources.create(PriceSources.CARDMARKET).getCurrency().getCurrencyCode());
		Assert.assertEquals("EUR", PriceSources.currencyOf(PriceSources.CARDMARKET));
		Assert.assertEquals("USD", PriceSources.currencyOf(PriceSources.TCGPLAYER));
		Assert.assertFalse(PriceSources.isKnown("TCG Player (Low)"));
		Assert.assertTrue(PriceSources.isKnown(PriceSources.CARDMARKET));
	}
}
