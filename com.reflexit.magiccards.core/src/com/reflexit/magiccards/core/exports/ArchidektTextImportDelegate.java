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
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Imports the text of an Archidekt deck export: one card per line,
 *
 * <pre>
 * 1x Sol Ring (c21) 263 [Ramp]
 * 1x Lightning Bolt (m11) 149 *F* [Burn{noDeck}]
 * 2x Negate (m21) 59 [Sideboard]
 * </pre>
 *
 * that is: count and {@code x}, name, the set code between parentheses, the collector number, {@code *F*}
 * (foil) or {@code *E*} (etched), and the categories between square brackets (a {@code ^tag^} is ignored).
 * The category {@code Sideboard} sends the card to the sideboard; {@code Maybeboard} and any category marked
 * {@code {noDeck}} (Archidekt's "not in the deck" lists, such as Upgrades) are not read; any other category
 * (Commander, Ramp...) keeps the card in the deck. A file with no line that has both a
 * {@code Nx} count and a set code or a category is not an Archidekt list and is rejected, so another importer
 * gets a go.
 */
public class ArchidektTextImportDelegate extends AbstractImportDelegate {
	/** What a line is made of: count[x], name, [(set) [number]], then marks: *F*, [categories], ^tags^. */
	private static final Pattern LINE = Pattern.compile("^(\\d{1,4})(x?)\\s+(.+?)"
			+ "(?:\\s+\\(([A-Za-z0-9_]{2,8})\\)(?:\\s+([^\\s\\[\\]^*]\\S*))?)?"
			+ "((?:\\s+(?:\\*[FfEe]\\*|\\[[^\\]]*\\]|\\^[^^]*\\^))*)\\s*$");
	private static final Pattern MARK = Pattern.compile("\\*([FfEe])\\*|\\[([^\\]]*)\\]");

	/** What a line's categories say about the card. */
	enum Zone {
		DECK, SIDEBOARD, SKIP
	}

	@Override
	protected void doRun(ICoreProgressMonitor monitor) throws IOException {
		importData.setFields(new ICardField[] { MagicCardField.COUNT, MagicCardField.NAME, MagicCardField.SET,
				MagicCardField.COLLNUM, MagicCardField.FINISH });
		boolean archidekt = false;
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
				Matcher m = LINE.matcher(line);
				if (!m.matches())
					throw new IOException("Not an Archidekt deck list: cannot read line " + lineNum + ": " + line);
				any = true;
				String marks = m.group(6) == null ? "" : m.group(6);
				archidekt |= m.group(2).length() > 0 && (m.group(4) != null || marks.indexOf('[') >= 0);
				Zone zone = Zone.DECK;
				CardFinish finish = null;
				Matcher k = MARK.matcher(marks);
				while (k.find()) {
					if (k.group(1) != null)
						finish = k.group(1).equalsIgnoreCase("e") ? CardFinish.ETCHED : CardFinish.FOIL;
					else
						zone = merge(zone, zone(k.group(2)));
				}
				if (zone == Zone.SKIP)
					continue;
				MagicCardPhysical card = createDefaultCard();
				card.setCount(count(m.group(1)));
				card.set(MagicCardField.NAME, name(m.group(3)));
				if (m.group(4) != null)
					setEditionCode(card, m.group(4));
				if (m.group(5) != null)
					card.set(MagicCardField.COLLNUM, m.group(5));
				if (finish != null)
					card.setFinish(finish);
				if (zone == Zone.SIDEBOARD)
					card.setLocation(getSideboardLocation());
				importCard(card);
				monitor.worked(1);
			}
		}
		if (any && !archidekt)
			throw new IOException("Not an Archidekt deck list: no line has a count like 1x and a set code or category");
	}

	/** The zone the categories of one bracket name: "Sideboard" / "Maybeboard" win over the others. */
	static Zone zone(String categories) {
		Zone z = Zone.DECK;
		if (categories == null)
			return z;
		for (String c : categories.split(",")) {
			String n = c.replaceAll("\\{[^}]*\\}", "").trim().toLowerCase(Locale.ENGLISH);
			if (c.toLowerCase(Locale.ENGLISH).contains("{nodeck}")) // e.g. the "Upgrades" list: not in the deck
				z = merge(z, Zone.SKIP);
			else if (n.equals("sideboard"))
				z = merge(z, Zone.SIDEBOARD);
			else if (n.equals("maybeboard"))
				z = merge(z, Zone.SKIP);
		}
		return z;
	}

	private static Zone merge(Zone a, Zone b) {
		if (a == Zone.SKIP || b == Zone.SKIP)
			return Zone.SKIP;
		return a == Zone.SIDEBOARD || b == Zone.SIDEBOARD ? Zone.SIDEBOARD : Zone.DECK;
	}

	/** Archidekt writes "A // B" for a card with two names, as ManaDesk does; "A / B" is accepted too. */
	static String name(String archidekt) {
		return MoxfieldTextImportDelegate.name(archidekt);
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
		return new ArchidektTextExportDelegate().getExample();
	}
}
