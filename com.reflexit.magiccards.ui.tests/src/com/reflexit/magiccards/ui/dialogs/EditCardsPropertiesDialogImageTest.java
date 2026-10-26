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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The image pane of the Edit Card dialog: a proxy copy gets what Card Info and the gallery give it, a dashed red
 * frame around the image and a red "Proxy" badge under it; a genuine copy shows the plain image. Plain JUnit (no
 * display needed).
 */
public class EditCardsPropertiesDialogImageTest {
	private static final String URL = "file:/cards/TRK/English/abc.jpg";

	@Test
	public void testAProxyHasTheRedFrameAndTheBadge() {
		String html = EditCardsPropertiesDialog.imageHtml(URL, true, 223);
		assertTrue(html, html.contains(URL));
		assertTrue(html, html.contains("outline:3px dashed #d32f2f"));
		assertTrue(html, html.contains(">Proxy</span>"));
	}

	@Test
	public void testTheImageKeepsTheSameWidthWithOrWithoutTheProxyMarks() {
		assertTrue(EditCardsPropertiesDialog.imageHtml(URL, true, 223).contains("width:223px"));
		assertTrue(EditCardsPropertiesDialog.imageHtml(URL, false, 223).contains("width:223px"));
	}

	@Test
	public void testAGenuineCopyShowsThePlainImage() {
		String html = EditCardsPropertiesDialog.imageHtml(URL, false, 223);
		assertTrue(html, html.contains(URL));
		assertFalse(html, html.contains("dashed"));
		assertFalse(html, html.contains("Proxy"));
	}
}
