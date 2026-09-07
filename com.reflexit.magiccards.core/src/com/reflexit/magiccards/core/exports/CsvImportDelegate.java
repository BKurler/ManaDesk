/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia. All rights reserved. This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */
package com.reflexit.magiccards.core.exports;

import java.io.IOException;
import java.util.List;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Import for CVS deck format
 */
public class CsvImportDelegate extends TableImportDelegate {
	public CsvImportDelegate() {
	}

	/** Minimum number of separator-delimited fields a valid line must have.
	 * Formats that carry a single column (e.g. an id-only list) lower it to 1. */
	protected int getMinFields() {
		return 2;
	}

	/** Columns a generated "Example..." should show; {@code null} = no example. */
	protected ICardField[] getExampleFields() {
		return null;
	}

	@Override
	public String getExample() {
		ICardField[] cols = getExampleFields();
		if (cols == null || cols.length == 0)
			return null;
		char sep = getSeparator();
		StringBuilder header = new StringBuilder();
		StringBuilder row1 = new StringBuilder();
		StringBuilder row2 = new StringBuilder();
		for (int i = 0; i < cols.length; i++) {
			if (i > 0) {
				header.append(sep);
				row1.append(sep);
				row2.append(sep);
			}
			header.append(cols[i].name());
			row1.append(exampleValue(cols[i], 0));
			row2.append(exampleValue(cols[i], 1));
		}
		return header + "\n" + row1 + "\n" + row2 + "\n";
	}

	private static String exampleValue(ICardField f, int which) {
		if (f == MagicCardField.NAME)
			return which == 0 ? "Lightning Bolt" : "Counterspell";
		if (f == MagicCardField.SET)
			return which == 0 ? "Limited Edition Alpha" : "Ice Age";
		if (f == MagicCardField.EDITION_ABBR)
			return which == 0 ? "LEA" : "ICE";
		if (f == MagicCardField.COUNT)
			return which == 0 ? "4" : "2";
		if (f == MagicCardField.ID)
			return which == 0 ? "ce711943-c1a1-43a0-8b89-8d169cfb8e11"
					: "0c4eaecf-dd4c-45ab-9b50-2abe987d35d4";
		if (f == MagicCardField.GATHERERID)
			return which == 0 ? "209" : "2568";
		if (f == MagicCardField.COLLNUM)
			return which == 0 ? "161" : "64";
		if (f == MagicCardField.LANG)
			return "English";
		if (f == MagicCardField.OWNERSHIP)
			return "true";
		if (f == MagicCardField.SPECIAL)
			return which == 0 ? "foil" : "";
		if (f == MagicCardField.COMMENT)
			return which == 0 ? "traded from Bob" : "";
		if (f == MagicCardField.PRICE)
			return which == 0 ? "1.50" : "";
		return "";
	}

	/**
	 * @param monitor
	 * @throws IOException
	 */
	@Override
	public void doRun(ICoreProgressMonitor monitor) throws IOException {
		runCsvImport(monitor);
	}

	@Override
	public char getSeparator() {
		return ',';
	}

	public void runCsvImport(ICoreProgressMonitor monitor) throws IOException {
		monitor.beginTask("Importing csv", 100);
		CsvImporter importer = null;
		importer = new CsvImporter(getStream(), getSeparator());
		try {
			do {
				lineNum++;
				List<String> list = importer.readLine();
				if (list == null)
					break;
				if (list.size() < getMinFields()) {
					throw new MagicException("Line " + lineNum + ": Format error, at least " + getMinFields()
							+ " field(s) are expected");
				}
				if (lineNum == 1) {
					setHeaderFields(list);
					continue;
				}
				MagicCardPhysical card = createCard(list);
				if (card != null)
					importCard(card);
				monitor.worked(1);
			} while (true);
		} finally {
			importer.close();
			monitor.done();
		}
	}
}
