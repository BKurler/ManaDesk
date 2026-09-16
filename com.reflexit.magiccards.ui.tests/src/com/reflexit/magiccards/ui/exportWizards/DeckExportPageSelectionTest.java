/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: regression test for the
 *                         "Export..." dialog silently dropping the navigator
 *                         selection it was opened with
 *******************************************************************************/

package com.reflexit.magiccards.ui.exportWizards;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.jface.viewers.StructuredSelection;
import org.junit.Test;

/**
 * {@link DeckExportPage#shouldRestoreRememberedSelection(org.eclipse.jface.viewers.IStructuredSelection)}.
 *
 * Regression: {@code restoreWidgetValues()} used to reload the last-used
 * export selection from dialog settings unconditionally. Since that reload
 * (via the "Decks / collections:" field's {@code ModifyListener}) overwrites
 * {@code resourceSelection}, and {@code setTextFromSelection()} - called right
 * after in {@code createControl()} - just re-derives the text field FROM
 * {@code resourceSelection}, an explicit incoming selection (e.g. right-click
 * "Export..." on one or several decks/collections in the navigator) was
 * silently replaced by whatever was exported last time, before the dialog
 * ever showed.
 */
public class DeckExportPageSelectionTest {

	@Test
	public void explicitSingleSelection_winsOverRemembered() {
		assertFalse(DeckExportPage.shouldRestoreRememberedSelection(new StructuredSelection("some-deck")));
	}

	@Test
	public void explicitMultiSelection_winsOverRemembered() {
		assertFalse(DeckExportPage
				.shouldRestoreRememberedSelection(new StructuredSelection(Arrays.asList("deck-a", "deck-b", "deck-c"))));
	}

	@Test
	public void noSelection_fallsBackToRemembered() {
		assertTrue(DeckExportPage.shouldRestoreRememberedSelection(null));
	}

	@Test
	public void emptySelection_fallsBackToRemembered() {
		assertTrue(DeckExportPage.shouldRestoreRememberedSelection(new StructuredSelection(Collections.emptyList())));
	}
}
