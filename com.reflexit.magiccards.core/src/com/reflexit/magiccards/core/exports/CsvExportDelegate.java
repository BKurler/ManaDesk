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
 *                         property value the field's own aggregateValueOf()
 *                         would give. This base class is shared by "ManaDesk
 *                         Full CSV" and (via ScsvExportDelegate) "ManaDesk
 *                         Full CSV Semicolon"; MinimumCsvExportDelegate
 *                         already had this same fix on its own override,
 *                         these two "Full" formats had not
 */

package com.reflexit.magiccards.core.exports;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;

/**
 * Export of ManaDesk csv
 */
public class CsvExportDelegate extends AbstractExportDelegatePerLine<IMagicCard> {
	@Override
	protected boolean isForExport(ICardField field) {
		return super.isForExport(field) || field == MagicCardField.SIDEBOARD;
	}

	@Override
	public Object getObjectByField(IMagicCard card, ICardField field) {
		if (field == MagicCardField.FINISH && card instanceof MagicCardPhysical)
			return ((MagicCardPhysical) card).getFinish().getLabel();
		return super.getObjectByField(card, field);
	}

	@Override
	public String getSeparator() {
		return ",";
	}

	@Override
	protected String escape(String element) {
		return escapeQuot(element);
	}

	@Override
	public String getExample() {
		return "NAME,SET,COUNT,SPECIAL,COMMENT,LANG,COLLNUM,GATHERERID,ID,OWNERSHIP\n"
				+ "\"Aang, Swift Savior // Aang and La, Ocean's Fury (transform)\",Avatar: The Last Airbender,1,,,English,359b,0,-372afa84-9ca9-46e6-8643-e16249505c59,true\n"
				+ "2001 World Championships Ad,World Championship Decks 2001,1,,c1,English,0,0,3513d5d3-7e88-4f5d-927f-1e67eb18e58e,true\n"
				+ "A Reckoning Approaches,Archenemy: Nicol Bolas Schemes,1,s1,c2,English,16★,430661,3c05afe6-c92f-440d-b09a-cc23b15da495,false\n"
				+ "Abandon Hope,Tempest,1,s2,c3,English,107,4635,942cf220-472c-48f6-8f60-993939ea5ab8,true\n"
				+ "Abaddon the Despoiler,\"Warhammer 40,000 Commander\",1,s3,,English,171,582569,a78ab8fe-4499-4860-87d3-f8707398c00c,true\n"
				+ "Reya Dawnbringer,Tenth Edition,1,,,English,35★,0,f9349fdc-3d9c-4fa9-88b6-a7bc782bfd44,true\n";
	}
}
