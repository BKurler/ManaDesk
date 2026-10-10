/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - one line of a list of cards to buy: quantity + the
 *                         printing it should be (set, collector number,
 *                         finish), formatted per store by BuyListFormat.
 *     Rémi Dutil (2026) - TCGplayer product id of the printing (0 = unknown):
 *                         Mass Entry takes "qty-productId", which avoids
 *                         TCGplayer's own product names entirely (e.g.
 *                         "Manabond (Future Sight)" for MB2 208). *******************************************************************************/
package com.reflexit.magiccards.core.exports;

import java.util.Locale;

import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;

/**
 * One card to buy: how many, and which printing (set code / set name /
 * collector number / finish). Any printing field may be {@code null} when
 * unknown - formats then simply ask for the card by name.
 */
public final class BuyListLine {
	private final String name;
	private final int quantity;
	private final String setCode;
	private final String setName;
	private final String collectorNumber;
	private final CardFinish finish;
	private final int tcgProductId;

	public BuyListLine(String name, int quantity, String setCode, String setName, String collectorNumber,
			CardFinish finish) {
		this(name, quantity, setCode, setName, collectorNumber, finish, 0);
	}

	public BuyListLine(String name, int quantity, String setCode, String setName, String collectorNumber,
			CardFinish finish, int tcgProductId) {
		this.name = name == null ? "" : name.trim();
		this.quantity = quantity;
		this.setCode = blankToNull(setCode == null ? null : setCode.toUpperCase(Locale.ENGLISH));
		this.setName = blankToNull(setName);
		this.collectorNumber = blankToNull(collectorNumber);
		this.finish = finish == null ? CardFinish.NONFOIL : finish;
		this.tcgProductId = Math.max(0, tcgProductId);
	}

	/** Same line, another quantity. */
	public BuyListLine withQuantity(int q) {
		return new BuyListLine(name, q, setCode, setName, collectorNumber, finish, tcgProductId);
	}

	/** The printing of {@code card} (its finish too, for an owned copy). */
	public static BuyListLine of(IMagicCard card, int quantity) {
		Edition ed = card.getEdition();
		String code = ed == null ? null : ed.getMainAbbreviation();
		CardFinish finish = card instanceof MagicCardPhysical ? ((MagicCardPhysical) card).getFinish() : null;
		return new BuyListLine(card.getName(), quantity, code, card.getSet(), card.getCollectorId(), finish,
				tcgProductIdOf(card, finish));
	}

	/**
	 * TCGplayer product for this printing in this finish, 0 when unknown. The
	 * card database keeps the regular product (non-foil and foil are one
	 * product there); TCGplayer sells etched as a separate product whose id is
	 * only kept for an etched-only printing - so an etched copy of a printing
	 * that also exists non-etched has no usable id (0: falls back to the name).
	 */
	static int tcgProductIdOf(IMagicCard card, CardFinish finish) {
		int id = card.getTcgId();
		if (id <= 0)
			return 0;
		if (finish == CardFinish.ETCHED) {
			IMagicCard printing = card instanceof MagicCardPhysical ? ((MagicCardPhysical) card).getCard() : card;
			boolean etchedOnly = printing instanceof MagicCard && ((MagicCard) printing).isEtchedOnly();
			return etchedOnly ? id : 0;
		}
		return id;
	}

	private static String blankToNull(String s) {
		return s == null || s.trim().isEmpty() ? null : s.trim();
	}

	public String getName() {
		return name;
	}

	public int getQuantity() {
		return quantity;
	}

	/** Upper-case set code (e.g. "SLD"), or {@code null}. */
	public String getSetCode() {
		return setCode;
	}

	public String getSetName() {
		return setName;
	}

	public String getCollectorNumber() {
		return collectorNumber;
	}

	/** Never {@code null} ({@link CardFinish#NONFOIL} when unknown). */
	public CardFinish getFinish() {
		return finish;
	}

	/** TCGplayer product id of this printing in this finish, 0 when unknown. */
	public int getTcgProductId() {
		return tcgProductId;
	}
}
