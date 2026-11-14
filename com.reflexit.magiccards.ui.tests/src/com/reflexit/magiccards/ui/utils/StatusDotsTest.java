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
package com.reflexit.magiccards.ui.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The little status markers painted on a deck / collection icon: where each one goes, and that
 * nothing is painted when no flag is set. The markers are painted on a white 30 x 30 picture and
 * the pixels read back. JUnit Plug-in Test (pictures need a display).
 */
public class StatusDotsTest {
	private static final int SIZE = 30;
	private static final RGB WHITE = new RGB(255, 255, 255);
	private Image image;

	@Before
	public void setUp() {
		image = new Image(Display.getDefault(), SIZE, SIZE);
		GC gc = new GC(image);
		Color white = new Color(Display.getDefault(), WHITE);
		gc.setBackground(white);
		gc.fillRectangle(0, 0, SIZE, SIZE);
		gc.dispose();
		white.dispose();
	}

	@After
	public void tearDown() {
		image.dispose();
	}

	private void paint(boolean virtual, boolean readOnly, boolean unsorted, boolean boxed, boolean digital) {
		GC gc = new GC(image);
		try {
			gc.setAntialias(SWT.OFF);
			StatusDots.paint(gc, SIZE, SIZE, virtual, readOnly, unsorted, boxed, digital);
		} finally {
			gc.dispose();
		}
	}

	private RGB pixel(int x, int y) {
		ImageData d = image.getImageData();
		return d.palette.getRGB(d.getPixel(x, y));
	}

	@Test
	public void testNothingIsPaintedWithoutAFlag() {
		paint(false, false, false, false, false);
		for (int x = 0; x < SIZE; x += 3)
			for (int y = 0; y < SIZE; y += 3)
				assertEquals(WHITE, pixel(x, y));
	}

	@Test
	public void testVirtualIsABlueDotBottomLeft() {
		paint(true, false, false, false, false);
		assertEquals(StatusDots.VIRTUAL, pixel(3, 27));
		assertEquals(WHITE, pixel(27, 27));
		assertEquals(WHITE, pixel(3, 3));
	}

	@Test
	public void testReadOnlyIsARedDotBottomRight() {
		paint(false, true, false, false, false);
		assertEquals(StatusDots.READ_ONLY, pixel(27, 27));
		assertEquals(WHITE, pixel(3, 27));
	}

	@Test
	public void testUnsortedIsAGreenDotTopLeft() {
		paint(false, false, true, false, false);
		assertEquals(StatusDots.UNSORTED, pixel(3, 3));
		assertEquals(WHITE, pixel(27, 3));
	}

	@Test
	public void testBoxedIsABlackDotTopLeft() {
		paint(false, false, false, true, false);
		assertEquals(StatusDots.BOXED, pixel(3, 3));
	}

	@Test
	public void testSeveralMarkersCanBeShownTogether() {
		paint(true, true, false, true, false);
		assertEquals(StatusDots.VIRTUAL, pixel(3, 27));
		assertEquals(StatusDots.READ_ONLY, pixel(27, 27));
		assertEquals(StatusDots.BOXED, pixel(3, 3));
	}

	@Test
	public void testDigitalDrawsALetterInTheMiddle() {
		paint(false, false, false, false, true);
		boolean drawn = false;
		for (int x = 8; x < 22 && !drawn; x++)
			for (int y = 8; y < 22 && !drawn; y++)
				drawn = !WHITE.equals(pixel(x, y));
		assertTrue("a letter D in the middle", drawn);
		assertEquals("the corners stay clear", WHITE, pixel(1, 1));
	}

	@Test
	public void testADecoratedIconThatDoesNotExistIsNull() {
		assertNull(StatusDots.decorate("icons/no-such-icon.png", true, false, false, false, false));
	}
}
