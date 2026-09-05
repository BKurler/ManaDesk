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
 */

package com.reflexit.magiccards.core.exports;

import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;

import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Import delegate interface
 */
public interface IExportDelegate<T> {
	public ReportType getType();

	public void init(OutputStream st, boolean header, IFilteredCardStore<T> cards);

	public void run(ICoreProgressMonitor monitor) throws InvocationTargetException, InterruptedException;

	public void setColumns(ICardField[] columnsForExport);

	public void setReportType(ReportType reportType);

	public boolean isSideboardSupported();

	public boolean isColumnChoiceSupported();

	public boolean isMultipleLocationSupported();

	public String getExample();

	/**
	 * Short slug describing what this export produces (e.g. "sideboard-list").
	 * The export wizard drops it into the proposed file name between the deck
	 * name and the extension - "deck1-sideboard-list.html". Empty (the default)
	 * means the proposed name is just "deckname.ext".
	 */
	default String getContentSlug() {
		return "";
	}

	/**
	 * True when this export is scoped to the sideboard (never the main deck) -
	 * e.g. the printable sideboard list. The deck-view Export tab uses it to
	 * disable the "add main deck" toggle while keeping "include extra" live.
	 */
	default boolean isSideboardOnly() {
		return false;
	}

	/**
	 * True when this export type is most useful as ONE file spanning every
	 * selected deck (a proxies sheet, a printable sideboard-list booklet).
	 * Seeds the export wizard's "Combine in one file" checkbox.
	 */
	default boolean isCombineByDefault() {
		return false;
	}

	/**
	 * Set by the export wizard when several decks are combined into one file, so
	 * the output carries a "Deck" column and rows stay identifiable. No-op for
	 * delegates that render their own per-deck sections.
	 */
	default void setMultiDeck(boolean multiDeck) {
	}
}