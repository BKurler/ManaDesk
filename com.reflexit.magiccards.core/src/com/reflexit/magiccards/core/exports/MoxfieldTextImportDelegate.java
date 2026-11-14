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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Imports the text of Moxfield's "Copy for Moxfield" deck export: one card per line, with its printing,
 *
 * <pre>
 * 1 Wilhelt, the Rotcleaver (MIC) 2
 * 1 Arcane Denial (PLST) CMA-30
 * 1 Grave Researcher / Reanimate (SOS) 85
 * 1 Sol Ring (C21) 263 *F*
 * </pre>
 *
 * that is: count, name, the set code between parentheses, the collector number, and {@code *F*} (foil) or
 * {@code *E*} (etched) when the copy is a foil. The card is found by its set and collector number. A line may
 * have no printing ("1 Sol Ring": Moxfield's other exports, the MTGO and plain text ones, are read too), but a
 * file with <b>no</b> line that has a set code and a number is not a Moxfield list and is rejected, so another
 * importer (the Freeform Text one) gets a go.
 *
 * <p>
 * Section headings ({@code SIDEBOARD:}, {@code MAINBOARD:}, {@code COMMANDER:}...) are understood: the sideboard
 * goes to the sideboard, the commander and the companion stay in the deck, and the maybeboard / considering cards
 * are not read. Moxfield writes a two-faced or split card as "A / B", ManaDesk's name is "A // B".
 */
public class MoxfieldTextImportDelegate extends AbstractImportDelegate {
	// count, name, (SET), collector number, optional foil mark
	private static final Pattern PRINTING = Pattern.compile(
			"^(\\d{1,4})\\s*[xX]?\\s+(.+?)\\s+\\(([A-Za-z0-9_]{2,8})\\)\\s+(\\S+?)(?:\\s+\\*([FfEe])\\*)?\\s*$");
	private static final Pattern PLAIN = Pattern.compile("^(\\d{1,4})\\s*[xX]?\\s+(.*\\S)\\s*$");
	private static final Pattern HEADING = Pattern.compile(
			"^(side\\s*board|main\\s*board|main\\s*deck|main|deck|commanders?|companions?|maybe\\s*board|considering"
					+ "|tokens?)\\s*:?$",
			Pattern.CASE_INSENSITIVE);

	/** Where the lines being read go. */
	private enum Zone {
		DECK, SIDEBOARD, SKIP
	}

	@Override
	protected void doRun(ICoreProgressMonitor monitor) throws IOException {
		importData.setFields(new ICardField[] { MagicCardField.COUNT, MagicCardField.NAME, MagicCardField.SET,
				MagicCardField.COLLNUM, MagicCardField.FINISH });
		Zone zone = Zone.DECK;
		boolean printing = false;
		boolean any = false;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(getStream(), FileUtils.CHARSET_UTF_8))) {
			String raw;
			while ((raw = r.readLine()) != null) {
				lineNum++;
				String line = raw.trim();
				if (line.startsWith("﻿"))
					line = line.substring(1).trim();
				if (line.isEmpty())
					continue;
				Matcher h = HEADING.matcher(line);
				if (h.matches()) {
					zone = zone(h.group(1));
					continue;
				}
				MagicCardPhysical card = parseLine(line);
				if (card == null)
					throw new IOException("Not a Moxfield deck list: cannot read line " + lineNum + ": " + line);
				any = true;
				printing |= card.getCollectorId() != null && !card.getCollectorId().isEmpty();
				if (zone == Zone.SKIP)
					continue;
				if (zone == Zone.SIDEBOARD)
					card.setLocation(getSideboardLocation());
				importCard(card);
				monitor.worked(1);
			}
		}
		if (any && !printing)
			throw new IOException("Not a Moxfield deck list: no line has a set code and a collector number");
	}

	private static Zone zone(String heading) {
		String h = heading.toLowerCase(java.util.Locale.ENGLISH).replaceAll("\\s+", "");
		if (h.equals("sideboard"))
			return Zone.SIDEBOARD;
		if (h.equals("maybeboard") || h.equals("considering") || h.startsWith("token"))
			return Zone.SKIP;
		return Zone.DECK;
	}

	/** One card line, or {@code null} when the line is not a card. */
	private MagicCardPhysical parseLine(String line) {
		MagicCardPhysical card = createDefaultCard();
		Matcher m = PRINTING.matcher(line);
		if (m.matches()) {
			card.setCount(count(m.group(1)));
			card.set(MagicCardField.NAME, name(m.group(2)));
			setEditionCode(card, m.group(3));
			card.set(MagicCardField.COLLNUM, m.group(4));
			String f = m.group(5);
			if (f != null)
				card.setFinish(f.equalsIgnoreCase("e") ? CardFinish.ETCHED : CardFinish.FOIL);
			return card;
		}
		m = PLAIN.matcher(line);
		if (m.matches()) {
			card.setCount(count(m.group(1)));
			card.set(MagicCardField.NAME, name(m.group(2)));
			return card;
		}
		return null;
	}

	/** Moxfield writes "A / B" for a card with two names, ManaDesk (as Scryfall) "A // B". */
	static String name(String moxfield) {
		String n = moxfield.trim();
		if (n.contains(" / ") && !n.contains(" // "))
			n = n.replace(" / ", " // ");
		return n;
	}

	private static int count(String s) {
		try {
			int n = Integer.parseInt(s.trim());
			return n <= 0 ? 1 : n;
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	@Override
	public String getExample() {
		return new MoxfieldTextExportDelegate().getExample();
	}
}
