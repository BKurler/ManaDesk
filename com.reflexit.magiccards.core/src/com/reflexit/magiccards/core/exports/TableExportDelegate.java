/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - export the FINISH column as "Nonfoil"/"Foil"/
 *                         "Etched" (CardFinish's label) instead of the raw
 *                         property value - same fix as CsvExportDelegate/
 *                         MinimumCsvExportDelegate
 */

package com.reflexit.magiccards.core.exports;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;

/**
 * Pipe separated table
 */
public class TableExportDelegate extends AbstractExportDelegatePerLine<IMagicCard> {
	private final String SEP = "|";

	@Override
	public Object getObjectByField(IMagicCard card, ICardField field) {
		if (field == MagicCardField.FINISH && card instanceof MagicCardPhysical)
			return ((MagicCardPhysical) card).getFinish().getLabel();
		return super.getObjectByField(card, field);
	}

	@Override
	public String getSeparator() {
		return SEP;
	}

	@Override
	protected String escape(String element) {
		if (element.contains(SEP)) {
			return element.replaceAll("\\Q" + SEP, "?");
		}
		return element;
	}

	@Override
	public String getExample() {
		return getTablePiped();
	}

	public static String getTablePiped() {
		return "NAME|ID|GATHERERID|COST|TYPE|POWER|TOUGHNESS|SET|COUNT|SPECIAL|COMMENT|TEXT|SIDEBOARD\n"
				+ "Reya Dawnbringer|4b81ef83-41c2-46dc-aff9-41bb3a32637a|106384|{6}{W}{W}{W}|Legendary Creature - Angel|4|6|Tenth Edition|4|||Flying<br>At the beginning of your upkeep, you may return target creature card from your graveyard to the battlefield.|false\n"
				+ "Platinum Angel|7b782865-b9d1-41ed-8a7b-a36c17022190|191313|{7}|Artifact Creature - Angel|4|4|Magic 2010|3|foil,c=good||Flying<br>You can't lose the game and your opponents can't win the game.|false\n"
				+ "Reya Dawnbringer|7ee7d4a5-25ee-4efe-bfd3-75873a1e83f3|196998|{6}{W}{W}{W}|Legendary Creature - Angel|4|6|Duel Decks: Divine vs. Demonic|3|c=heavily_played||Flying<br>At the beginning of your upkeep, you may return target creature card from your graveyard to the battlefield.|false\n"
				+ "Reya Dawnbringer|7ee7d4a5-25ee-4efe-bfd3-75873a1e83f3|196998|{6}{W}{W}{W}|Legendary Creature - Angel|4|6|Duel Decks: Divine vs. Demonic|1|c=heavily_played,fortrade||Flying<br>At the beginning of your upkeep, you may return target creature card from your graveyard to the battlefield.|false\n";
	}
}
