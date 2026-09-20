/*
 * Contributors:
 *     Rémi Dutil (2026) - new: tint() is the piece that makes the set-symbol
 *                         icon rendering correct (EditionFileCache used to
 *                         paint a SOLID rarity-coloured square behind an
 *                         un-tinted glyph instead of tinting the glyph itself
 *                         on a transparent background) - covers the two
 *                         things that fix depends on: a fully transparent
 *                         source pixel must stay fully transparent (alpha
 *                         untouched, RGB irrelevant), and an opaque source
 *                         pixel must take on the tint colour while keeping
 *                         its own alpha
 */
package com.reflexit.magiccards.core.sync;

import java.awt.Color;
import java.awt.image.BufferedImage;

import junit.framework.TestCase;

public class SvgRasterizerTest extends TestCase {
	private static BufferedImage onePixel(int argb) {
		BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		img.setRGB(0, 0, argb);
		return img;
	}

	private static int alpha(BufferedImage img) {
		return (img.getRGB(0, 0) >>> 24) & 0xFF;
	}

	private static int rgb(BufferedImage img) {
		return img.getRGB(0, 0) & 0xFFFFFF;
	}

	public void testTransparentPixelStaysTransparent() {
		// alpha=0, with some arbitrary (irrelevant) colour underneath - this
		// is what a glyph's background looks like after SvgRasterizer.renderSvg
		BufferedImage src = onePixel(0x00123456);
		BufferedImage out = SvgRasterizer.tint(src, new Color(0xF4C542));
		assertEquals("fully transparent pixels must stay fully transparent", 0, alpha(out));
	}

	public void testOpaquePixelTakesTintColor() {
		BufferedImage src = onePixel(0xFF000000); // opaque black, the glyph's own rendered colour
		Color tint = new Color(0xF4C542); // RarityColor.RARE
		BufferedImage out = SvgRasterizer.tint(src, tint);
		assertEquals("opaque pixels must keep their own alpha", 255, alpha(out));
		assertEquals("opaque pixels must take on the tint colour", tint.getRGB() & 0xFFFFFF, rgb(out));
	}

	public void testSemiTransparentPixelKeepsItsAlphaButTakesTintColor() {
		// an antialiased glyph edge - partial alpha must be preserved exactly,
		// not binarized, while the colour still becomes the tint colour
		BufferedImage src = onePixel(0x80000000);
		Color tint = new Color(0xE84A1A); // RarityColor.MYTHIC
		BufferedImage out = SvgRasterizer.tint(src, tint);
		assertEquals(0x80, alpha(out));
		assertEquals(tint.getRGB() & 0xFFFFFF, rgb(out));
	}
}
