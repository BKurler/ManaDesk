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
package com.reflexit.magiccards.ui.exportWizards;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;

import com.reflexit.magiccards.core.exports.ImportData;
import com.reflexit.magiccards.core.exports.ImportError;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.ui.views.columns.AbstractColumn;
import com.reflexit.magiccards.ui.views.columns.ColumnCollection;
import com.reflexit.magiccards.ui.views.columns.GenColumn;

import junit.framework.TestCase;

/**
 * Real header clicks on a real SWT table of the import preview ({@link PreviewTableViewer}): the first click
 * on the Error header puts the rows with an error on top (whatever column was clicked before), the second
 * reverses. Needs a display and SWT: a
 * JUnit Plug-in Test (or plain JUnit with the SWT jars on the class path); skipped without a display.
 */
public class PreviewTableViewerSortTest extends TestCase {
	private static MagicCardPhysical card(String name, String error) {
		MagicCard c = new MagicCard();
		c.setName(name);
		MagicCardPhysical p = new MagicCardPhysical(c, null);
		if (error != null)
			p.setError(error);
		return p;
	}

	/** Only a Name and an Error column; no colours (they need the running application). */
	private static ColumnCollection columns() {
		return new ColumnCollection() {
			@Override
			protected void createColumns(List<AbstractColumn> list) {
				list.add(new GenColumn(MagicCardField.NAME, "Name") {
					@Override
					public String getText(Object element) {
						return ((MagicCardPhysical) element).getName();
					}

					@Override
					public Color getBackground(Object element) {
						return null;
					}
				});
				list.add(new GenColumn(MagicCardField.ERROR, "Error") {
					@Override
					public String getText(Object element) {
						return DeckImportPreviewPage.errorShort(((MagicCardPhysical) element).getError());
					}

					@Override
					public Color getBackground(Object element) {
						return null;
					}
				});
			}
		};
	}

	private static String names(Table t) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < t.getItemCount(); i++)
			sb.append(t.getItem(i).getText(0)).append(i + 1 < t.getItemCount() ? "," : "");
		return sb.toString();
	}

	/**
	 * In the application a card's error is an {@code ImportError} object for some rows and a text for others (a
	 * "Mismatched info" sentence): they cannot be compared with each other, and the shared sort order threw a
	 * ClassCastException while the header was clicked, so the click did nothing.
	 */
	public void testMixedErrorTypes() {
		Display display;
		try {
			display = Display.getDefault();
		} catch (Throwable e) {
			return; // no display here
		}
		Shell shell = new Shell(display);
		try {
			shell.setLayout(new FillLayout());
			final ImportData data = new ImportData();
			PreviewTableViewer viewer = new PreviewTableViewer(shell, columns(), () -> data);
			MagicCardPhysical a = card("Clean A", null);
			MagicCardPhysical b = card("Plains", null);
			b.setError(ImportError.SET_NOT_FOUND_ERROR);
			MagicCardPhysical c = card("Abeyance", "Mismatched info: the id wins");
			MagicCardPhysical d = card("Circle", null);
			d.setError(ImportError.NO_LANG);
			MagicCardPhysical e = card("Clean B", null);
			List<MagicCardPhysical> list = new ArrayList<>();
			list.add(a);
			list.add(b);
			list.add(c);
			list.add(d);
			list.add(e);
			viewer.setInput(list);
			shell.setSize(600, 300);
			shell.open();
			Table t = viewer.getTable();
			for (int click = 0; click < 4; click++) {
				t.getColumn(1).notifyListeners(SWT.Selection, new Event());
				while (display.readAndDispatch()) {
					// process the refresh
				}
				boolean errorsOnTop = click % 2 == 0;
				// the three rows with an error together, on top (or last), the two clean rows together
				int withError = 0;
				for (int i = 0; i < t.getItemCount(); i++) {
					boolean has = !t.getItem(i).getText(1).isEmpty();
					int pos = errorsOnTop ? i : t.getItemCount() - 1 - i;
					assertEquals("click " + (click + 1) + " row " + i + " (" + names(t) + ")", pos < 3, has);
					if (has)
						withError++;
				}
				assertEquals(3, withError);
			}
			// the table can be loaded again with the sort in place (the wizard's Back / Next): no exception
			viewer.setInput(list);
			while (display.readAndDispatch()) {
				// process the refresh
			}
		} finally {
			shell.dispose();
		}
	}

	public void testErrorHeaderClicks() {
		Display display;
		try {
			display = Display.getDefault();
		} catch (Throwable e) {
			return; // no display here
		}
		Shell shell = new Shell(display);
		try {
			shell.setLayout(new FillLayout());
			final ImportData data = new ImportData();
			PreviewTableViewer viewer = new PreviewTableViewer(shell, columns(), () -> data);
			List<MagicCardPhysical> list = new ArrayList<>();
			list.add(card("Yavimaya Coast", null));
			list.add(card("Plains", "Set not found: x"));
			list.add(card("Athreos", "Num + Set needed: y"));
			list.add(card("Abeyance", "Mismatched info: z"));
			list.add(card("Island", null));
			list.add(card("Cavalier", "Num + Set needed: y"));
			viewer.setInput(list);
			shell.setSize(600, 300);
			shell.open();
			Table t = viewer.getTable();
			// the viewer has ONE sort direction for the whole table: after a click on another column (Name goes DOWN),
			// the next click on Error would be UP - it must still be a first click on Error
			t.getColumn(0).notifyListeners(SWT.Selection, new Event());
			while (display.readAndDispatch()) {
				// process the refresh
			}
			// first click: the rows with an error on top, by the text shown (stable inside a text), clean rows last
			t.getColumn(1).notifyListeners(SWT.Selection, new Event());
			while (display.readAndDispatch()) {
				// process the refresh
			}
			assertEquals("Abeyance,Athreos,Cavalier,Plains,Yavimaya Coast,Island", names(t));
			// second click: the exact reverse
			t.getColumn(1).notifyListeners(SWT.Selection, new Event());
			while (display.readAndDispatch()) {
				// process the refresh
			}
			assertEquals("Yavimaya Coast,Island,Plains,Athreos,Cavalier,Abeyance", names(t));
			// and back
			t.getColumn(1).notifyListeners(SWT.Selection, new Event());
			while (display.readAndDispatch()) {
				// process the refresh
			}
			assertEquals("Abeyance,Athreos,Cavalier,Plains,Yavimaya Coast,Island", names(t));
			// Name again (UP this time), then Error: again a first click, errors on top
			t.getColumn(0).notifyListeners(SWT.Selection, new Event());
			while (display.readAndDispatch()) {
				// process the refresh
			}
			t.getColumn(1).notifyListeners(SWT.Selection, new Event());
			while (display.readAndDispatch()) {
				// process the refresh
			}
			assertEquals("Abeyance,Athreos,Cavalier,Plains,Yavimaya Coast,Island", names(t));
		} finally {
			shell.dispose();
		}
	}
}
