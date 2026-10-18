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
package com.reflexit.magiccards.ui.dialogs;

import junit.framework.TestCase;

/**
 * Boxed means every card of the deck is really in its box, so turning it on for a
 * deck asks for a confirmation (plain JUnit: only the decision is tested).
 */
public class EditDeckPropertiesDialogBoxedTest extends TestCase {
	public void testTurningBoxedOnAsks() {
		assertTrue(EditDeckPropertiesDialog.needsBoxedConfirmation(false, true, true));
	}

	public void testAlreadyBoxedDoesNotAskAgain() {
		assertFalse(EditDeckPropertiesDialog.needsBoxedConfirmation(true, true, true));
	}

	public void testTurningBoxedOffDoesNotAsk() {
		assertFalse(EditDeckPropertiesDialog.needsBoxedConfirmation(true, false, true));
		assertFalse(EditDeckPropertiesDialog.needsBoxedConfirmation(false, false, true));
	}

	public void testGreyedBoxedNeverAsks() {
		// an owned deck: the checkbox is disabled
		assertFalse(EditDeckPropertiesDialog.needsBoxedConfirmation(false, true, false));
	}
}
