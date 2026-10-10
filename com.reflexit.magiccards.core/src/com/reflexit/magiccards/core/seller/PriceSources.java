/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - the two card price sources, both filled from the
 *                         Scryfall bulk file: TCGplayer (USD) and Cardmarket
 *                         (EUR); default chosen from the system locale;
 *                         currencyOf() - prices are displayed in the selected
 *                         source's own currency.
 *******************************************************************************/
package com.reflexit.magiccards.core.seller;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The card price sources ManaDesk keeps - both come from Scryfall's daily
 * prices in the bulk file, each in its own prices file ({@code <DB>/prices}):
 * <ul>
 * <li>{@link #TCGPLAYER} - TCGplayer market prices, USD: non-foil / foil /
 * etched;</li>
 * <li>{@link #CARDMARKET} - Cardmarket prices, EUR: non-foil / foil /
 * etched.</li>
 * </ul>
 * (Scryfall's third source, Cardhoarder MTGO tickets, prices the digital game
 * and is not kept - ManaDesk is paper only.)
 */
public final class PriceSources {
	/** Name of the TCGplayer source - also its prices file name (kept for existing databases). */
	public static final String TCGPLAYER = "TCG Player (Medium)";
	/** Name of the Cardmarket source - also its prices file name. */
	public static final String CARDMARKET = "Cardmarket";

	/** Europe = where Cardmarket is the usual marketplace (EU / EEA + UK + Switzerland). */
	private static final Set<String> CARDMARKET_COUNTRIES = new HashSet<>(Arrays.asList("AT", "BE", "BG", "HR",
			"CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PL",
			"PT", "RO", "SK", "SI", "ES", "SE", "IS", "LI", "NO", "CH", "GB"));

	private PriceSources() {
	}

	/** The source names, in the order offered to the user. */
	public static String[] names() {
		return new String[] { TCGPLAYER, CARDMARKET };
	}

	/** User-facing label: "TCGplayer (USD)" / "Cardmarket (EUR)". */
	public static String label(String name) {
		if (CARDMARKET.equals(name))
			return "Cardmarket (EUR)";
		if (TCGPLAYER.equals(name))
			return "TCGplayer (USD)";
		return name;
	}

	public static boolean isKnown(String name) {
		return TCGPLAYER.equals(name) || CARDMARKET.equals(name);
	}

	/** Cardmarket for a European country, TCGplayer everywhere else. */
	public static String defaultFor(Locale locale) {
		String country = locale == null ? "" : locale.getCountry();
		return CARDMARKET_COUNTRIES.contains(country) ? CARDMARKET : TCGPLAYER;
	}

	/** The currency source {@code name} is priced in: EUR for Cardmarket, USD otherwise. */
	public static String currencyOf(String name) {
		return CARDMARKET.equals(name) ? "EUR" : "USD";
	}

	/** A new, empty provider for {@code name}, in its own currency. */
	public static CustomPriceProvider create(String name) {
		CustomPriceProvider p = new CustomPriceProvider(name);
		if (CARDMARKET.equals(name))
			p.getProperties().setProperty("currency", currencyOf(name));
		return p;
	}
}
