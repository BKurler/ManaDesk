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

import java.lang.reflect.InvocationTargetException;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Export of ManaDesk Minimum csv
 */
public class MinimumCsvExportDelegate extends CsvExportDelegate {

	@Override
	protected boolean addsLocationColumnWhenMultiDeck() {
		return true;
	}

	@Override
	public void printHeader() {
		stream.println((multiDeck ? "LOCATION," : "")
				+ "NAME,SET,COUNT,SPECIAL,COMMENT,LANG,COLLNUM,GATHERERID,ID,OWNERSHIP");
	}

	protected ICardField[] doGetFields() {
		ICardField fields[] = new ICardField[] { MagicCardField.NAME, MagicCardField.SET, MagicCardField.COUNT,
				MagicCardField.SPECIAL, MagicCardField.COMMENT, MagicCardField.LANG, MagicCardField.COLLNUM,
				MagicCardField.GATHERERID, MagicCardField.ID, MagicCardField.OWNERSHIP

		};
		return fields;
	}

	@Override
	public void run(ICoreProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		setColumns(doGetFields());
		super.run(monitor);
	}

	@Override
	public Object getObjectByField(IMagicCard card, ICardField field) {
		if (field == MagicCardField.LANG && card.getLanguage() == null)
			return "English";
		return field.aggregateValueOf(card);
	}

	@Override
	public boolean isColumnChoiceSupported() {
		return false;
	}

}
