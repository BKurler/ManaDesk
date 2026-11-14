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
package com.reflexit.magiccards.core.sync;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * The symbol rendering of {@link SvgRasterizer}: a small SVG file becomes an image (scaled,
 * centered, black turned white for tinting) and the pixel filters (outlines, sharpen, scale) do
 * what they say on tiny hand-made images. SVGs are temp files read through {@code file:}
 * addresses, so nothing goes on the web. Works as plain JUnit.
 */
public class SvgRasterizerRenderTest extends TestCase {
	private static final int CLEAR = 0x00000000;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int BLACK = 0xFF000000;
	private File dir;

	@Override
	protected void setUp() throws Exception {
		dir = Files.createTempDirectory("svg-test").toFile();
	}

	@Override
	protected void tearDown() throws Exception {
		File[] files = dir.listFiles();
		if (files != null)
			for (File f : files)
				f.delete();
		dir.delete();
	}

	private File svg(String name, String content) throws IOException {
		File f = new File(dir, name);
		Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return f;
	}

	private static String square(String fill) {
		return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\" viewBox=\"0 0 10 10\">"
				+ "<rect x=\"0\" y=\"0\" width=\"10\" height=\"10\" " + fill + "/></svg>";
	}

	/** A 9x9 clear image with a 3x3 block (x and y from 3 to 5) of the given color. */
	private static BufferedImage block(int argb) {
		BufferedImage img = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
		for (int y = 3; y <= 5; y++)
			for (int x = 3; x <= 5; x++)
				img.setRGB(x, y, argb);
		return img;
	}

	private static int red(int argb) {
		return (argb >> 16) & 0xFF;
	}

	// ---- from a file

	public void testAFileIsRenderedAtTheAskedSize() throws Exception {
		BufferedImage img = SvgRasterizer.renderSvg(svg("red.svg", square("fill=\"#FF0000\"")), 20, 20);
		assertEquals(20, img.getWidth());
		assertEquals(20, img.getHeight());
		assertEquals(0xFFFF0000, img.getRGB(10, 10));
		assertEquals(0xFFFF0000, img.getRGB(1, 1));
	}

	public void testAWideTargetKeepsTheShapeSquareAndCenteredWithClearSides() throws Exception {
		BufferedImage img = SvgRasterizer.renderSvg(svg("red.svg", square("fill=\"#FF0000\"")), 40, 20);
		assertEquals(0xFFFF0000, img.getRGB(20, 10));
		assertEquals("left margin is clear", 0, img.getRGB(2, 10) >>> 24);
		assertEquals("right margin is clear", 0, img.getRGB(37, 10) >>> 24);
	}

	public void testASizeFromTheViewBoxAloneIsEnough() throws Exception {
		File f = svg("vb.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\">"
				+ "<rect width=\"10\" height=\"10\" fill=\"#00FF00\"/></svg>");
		BufferedImage img = SvgRasterizer.renderSvg(f, 10, 10);
		assertEquals(0xFF00FF00, img.getRGB(5, 5));
	}

	public void testAFileThatIsNotAnSvgIsAnError() throws Exception {
		File f = svg("bad.svg", "this is not an svg");
		try {
			SvgRasterizer.renderSvg(f, 10, 10);
			fail();
		} catch (IOException e) {
			// expected
		}
	}

	// ---- from a URL: black becomes white so the symbol can be tinted

	public void testBlackFillBecomesWhiteWhenReadFromAUrl() throws Exception {
		for (String fill : new String[] { "fill=\"#000\"", "fill=\"#000000\"", "fill=\"black\"" }) {
			File f = svg("black.svg", square(fill));
			BufferedImage img = SvgRasterizer.renderSvg(f.toURI().toURL(), 10, 10);
			assertEquals(fill, WHITE, img.getRGB(5, 5));
		}
	}

	public void testCurrentColorBecomesWhiteWhenReadFromAUrl() throws Exception {
		File f = svg("cc.svg", square("fill=\"currentColor\""));
		assertEquals(WHITE, SvgRasterizer.renderSvg(f.toURI().toURL(), 10, 10).getRGB(5, 5));
	}

	public void testOtherColorsAreKeptWhenReadFromAUrl() throws Exception {
		File f = svg("blue.svg", square("fill=\"#0000FF\""));
		assertEquals(0xFF0000FF, SvgRasterizer.renderSvg(f.toURI().toURL(), 10, 10).getRGB(5, 5));
	}

	public void testPreserveColorKeepsBlack() throws Exception {
		File f = svg("black.svg", square("fill=\"#000000\""));
		assertEquals(BLACK, SvgRasterizer.renderSvgPreserveColor(f.toURI().toURL(), 10, 10).getRGB(5, 5));
	}

	// ---- outlines

	public void testOutlineSurroundsTheShapeInBlackAndKeepsTheShape() {
		BufferedImage out = SvgRasterizer.addOutline(block(WHITE));
		assertEquals("the shape itself", WHITE, out.getRGB(3, 3));
		assertEquals("the middle", WHITE, out.getRGB(4, 4));
		assertEquals("one pixel around", BLACK, out.getRGB(2, 2));
		assertEquals(BLACK, out.getRGB(6, 4));
		assertEquals("two pixels away", CLEAR, out.getRGB(1, 1));
	}

	public void testSafeOutlineOnlyPaintsTheClearPixelsNextToTheShape() {
		BufferedImage out = SvgRasterizer.addSafeOutline(block(WHITE));
		assertEquals("the shape is untouched", WHITE, out.getRGB(3, 3));
		assertEquals(WHITE, out.getRGB(4, 4));
		assertEquals(BLACK, out.getRGB(2, 2));
		assertEquals(BLACK, out.getRGB(6, 5));
		assertEquals("too far", CLEAR, out.getRGB(1, 1));
		assertEquals("the image border is never painted", CLEAR, out.getRGB(0, 4));
	}

	public void testSafeOutlineIgnoresAFaintShape() {
		BufferedImage out = SvgRasterizer.addSafeOutline(block(0x40FFFFFF));
		assertEquals("alpha 64 is not opaque enough to be outlined", CLEAR, out.getRGB(2, 2));
	}

	public void testThinAndLightOutlinesDarkenTheSoftEdgeOfAShape() {
		int soft = 0x80FFFFFF;
		for (BufferedImage out : new BufferedImage[] { SvgRasterizer.addThinOutline(block(soft)),
				SvgRasterizer.addLightOutline(block(soft)) }) {
			assertTrue("edge pixel is darker than the middle: " + red(out.getRGB(3, 3)) + " vs " + red(out.getRGB(4, 4)),
					red(out.getRGB(3, 3)) < red(out.getRGB(4, 4)));
			assertEquals("clear stays clear", CLEAR, out.getRGB(1, 1));
			assertEquals(9, out.getWidth());
			assertEquals(9, out.getHeight());
		}
	}

	// ---- sharpen and scale

	public void testSharpenLeavesAFlatImageAsItIs() {
		BufferedImage flat = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 5; y++)
			for (int x = 0; x < 5; x++)
				flat.setRGB(x, y, 0xFF808080);
		assertEquals(0xFF808080, SvgRasterizer.applyMediumSharpen(flat).getRGB(2, 2));
		assertEquals(0xFF808080, SvgRasterizer.applyLightSharpen(flat).getRGB(2, 2));
	}

	public void testSharpenMakesAnEdgeStronger() {
		BufferedImage img = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 5; y++)
			for (int x = 0; x < 5; x++)
				img.setRGB(x, y, x < 2 ? 0xFF404040 : 0xFFC0C0C0);
		int before = red(img.getRGB(2, 2));
		assertTrue("the bright side of the edge gets brighter", red(SvgRasterizer.applyMediumSharpen(img).getRGB(2, 2)) >= before);
		assertEquals(5, SvgRasterizer.applyLightSharpen(img).getWidth());
	}

	public void testScaleGivesTheAskedSizeAndKeepsAFlatColor() {
		BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 4; y++)
			for (int x = 0; x < 4; x++)
				img.setRGB(x, y, 0xFFFF0000);
		BufferedImage big = SvgRasterizer.scale(img, 12, 8);
		assertEquals(12, big.getWidth());
		assertEquals(8, big.getHeight());
		assertEquals(0xFFFF0000, big.getRGB(6, 4));
	}
}
