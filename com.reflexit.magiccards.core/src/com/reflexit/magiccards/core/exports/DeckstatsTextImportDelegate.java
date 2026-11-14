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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Imports the deck text of Deckstats.net ("Export" &rarr; text): one card per line, with its printing,
 *
 * <pre>
 * //Main
 * 1 Lightning Bolt (SLZ) 305
 * 1 Lightning Bolt (STA) 42
 * 2 Negate
 * </pre>
 *
 * that is: count, the name, and optionally the set code in parentheses followed by the collector number. The
 * older {@code [SETCODE#collector number]} before the name and a {@code SB:} prefix for the sideboard are read
 * too. {@code //Heading} lines are comments, except {@code //Sideboard} (the cards after it go to the sideboard)
 * and {@code //Maybeboard} (not read); a {@code # comment} at the end of a line is ignored. A file with
 * <b>no</b> line that has a set code and a number is not taken for a Deckstats list and is rejected, so another
 * importer (the Freeform Text one) gets a go.
 */
public class DeckstatsTextImportDelegate extends AbstractImportDelegate {
	// optional "SB:", count, optional [SET#num], name, optional "(SET) num", optional "# comment"
	private static final Pattern LINE = Pattern.compile(
			"^(SB:\\s*)?(\\d{1,4})\\s*[xX]?\\s+(?:\\[([A-Za-z0-9_]{2,8})#([^\\]\\s]+)\\]\\s*)?(.*?)"
					+ "(?:\\s+\\(([A-Za-z0-9_]{2,8})\\)\\s+([^\\s#]+))?\\s*(?:#.*)?$",
			Pattern.CASE_INSENSITIVE);

	private enum Zone {
		DECK, SIDEBOARD, SKIP
	}

	@Override
	protected void doRun(ICoreProgressMonitor monitor) throws IOException {
		importData.setFields(new ICardField[] { MagicCardField.COUNT, MagicCardField.NAME, MagicCardField.SET,
				MagicCardField.COLLNUM });
		Zone zone = Zone.DECK;
		boolean printing = false;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(getStream(), FileUtils.CHARSET_UTF_8))) {
			String raw;
			while ((raw = r.readLine()) != null) {
				lineNum++;
				String line = raw.trim();
				if (line.startsWith("﻿"))
					line = line.substring(1).trim();
				if (line.isEmpty())
					continue;
				if (line.startsWith("//")) {
					String h = line.substring(2).trim().toLowerCase(Locale.ENGLISH);
					if (h.startsWith("side"))
						zone = Zone.SIDEBOARD;
					else if (h.startsWith("maybe") || h.startsWith("consider"))
						zone = Zone.SKIP;
					else
						zone = Zone.DECK;
					continue;
				}
				Matcher m = LINE.matcher(line);
				if (!m.matches() || m.group(5).isEmpty())
					throw new IOException("Not a Deckstats deck list: cannot read line " + lineNum + ": " + line);
				String set = m.group(3) != null ? m.group(3) : m.group(6);
				String num = m.group(3) != null ? m.group(4) : m.group(7);
				if (set != null)
					printing = true;
				if (zone == Zone.SKIP)
					continue;
				MagicCardPhysical card = createDefaultCard();
				card.setCount(count(m.group(2)));
				card.set(MagicCardField.NAME, m.group(5));
				if (set != null) {
					setEditionCode(card, DeckstatsCsvExportDelegate.scryfallCode(set));
					card.set(MagicCardField.COLLNUM, num);
				}
				if (m.group(1) != null || zone == Zone.SIDEBOARD)
					card.setLocation(getSideboardLocation());
				importCard(card);
				monitor.worked(1);
			}
		}
		if (!printing)
			throw new IOException("Not a Deckstats deck list: no line has a set code and a number");
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
		return new DeckstatsTextExportDelegate().getExample();
	}
}
