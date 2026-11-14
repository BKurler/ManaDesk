/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *******************************************************************************/
package com.reflexit.magiccards.core.exports;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.storage.ILocatable;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Exports a deck as the text Moxfield's own "Copy for Moxfield" gives, which Moxfield's Import accepts as is:
 * {@code 1 Sol Ring (C21) 263 *F*} (count, name, set code, collector number, and {@code *F*} foil /
 * {@code *E*} etched), then a blank line and {@code SIDEBOARD:} with the sideboard. Copies of the same printing
 * and finish are added up. The extra list (tokens, emblems...) is left out. See {@link MoxfieldTextImportDelegate}.
 */
public class MoxfieldTextExportDelegate extends AbstractExportDelegate<IMagicCard> {

	@Override
	public void export(ICoreProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		if (monitor == null)
			monitor = ICoreProgressMonitor.NONE;
		monitor.beginTask("Exporting to " + getLabel() + "...", store.getSize());
		try {
			Map<String, Integer> main = new LinkedHashMap<>();
			Map<String, Integer> side = new LinkedHashMap<>();
			for (IMagicCard card : store) {
				Location loc = card instanceof ILocatable ? ((ILocatable) card).getLocation() : null;
				if (loc != null && loc.isExtra())
					continue;
				int count = card instanceof MagicCardPhysical ? ((MagicCardPhysical) card).getCount() : 1;
				CardFinish finish = card instanceof MagicCardPhysical ? ((MagicCardPhysical) card).getFinish()
						: CardFinish.NONFOIL;
				String line = line(card.getName(), card.getSet(), card.getCollectorId(), finish);
				if (line.isEmpty() || count <= 0)
					continue;
				(loc != null && loc.isSideboard() ? side : main).merge(line, count, Integer::sum);
				monitor.worked(1);
			}
			stream.print(write(main, side));
		} finally {
			monitor.done();
		}
	}

	/** The line of a card without its count: "Name (SET) 123 *F*". */
	static String line(String cardName, String set, String number, CardFinish finish) {
		String name = ManaBoxCsvExportDelegate.name(cardName);
		if (name.isEmpty())
			return "";
		// Moxfield writes a card with two names as "A / B"
		StringBuilder sb = new StringBuilder(name.replace(" // ", " / "));
		String abbr = set == null || set.isEmpty() ? null : Editions.getInstance().getAbbrByName(set);
		if (abbr != null && !abbr.isEmpty() && number != null && !number.isEmpty()) {
			sb.append(" (").append(abbr.toUpperCase(Locale.ENGLISH)).append(") ").append(number);
			if (finish == CardFinish.FOIL)
				sb.append(" *F*");
			else if (finish == CardFinish.ETCHED)
				sb.append(" *E*");
		}
		return sb.toString();
	}

	/** The text of the list. */
	static String write(Map<String, Integer> main, Map<String, Integer> side) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Integer> e : main.entrySet())
			sb.append(e.getValue()).append(' ').append(e.getKey()).append('\n');
		if (!side.isEmpty()) {
			if (!main.isEmpty())
				sb.append('\n');
			sb.append("SIDEBOARD:\n");
			for (Map.Entry<String, Integer> e : side.entrySet())
				sb.append(e.getValue()).append(' ').append(e.getKey()).append('\n');
		}
		return sb.toString();
	}

	@Override
	public boolean isColumnChoiceSupported() {
		return false;
	}

	@Override
	public String getContentSlug() {
		return "moxfield";
	}

	@Override
	public String getExample() {
		return "1 Wilhelt, the Rotcleaver (MIC) 2\n" //
				+ "1 Arcane Denial (PLST) CMA-30\n" //
				+ "1 Grave Researcher / Reanimate (SOS) 85\n" //
				+ "1 Sol Ring (C21) 263 *F*\n" //
				+ "\n" //
				+ "SIDEBOARD:\n" //
				+ "2 Negate (M21) 59\n";
	}
}
