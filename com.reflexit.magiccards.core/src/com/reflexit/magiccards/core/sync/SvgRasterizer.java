/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - renderSvg() reads through WebUtils.openUrl() - fast
 *                         failure and the simulated outage apply to set icons
 *                         too.
 *******************************************************************************/

package com.reflexit.magiccards.core.sync;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGUniverse;

public class SvgRasterizer {

	// --- Public API: render from URL ---
	public static BufferedImage renderSvg(URL url, int width, int height) throws IOException {
		try (InputStream in = WebUtils.openUrl(url, 1)) {
			String svg = new String(in.readAllBytes(), StandardCharsets.UTF_8);

			// Normalize fill colors to white for tinting
			svg = svg.replace("fill=\"#000\"", "fill=\"#FFFFFF\"");
			svg = svg.replace("fill=\"#000000\"", "fill=\"#FFFFFF\"");
			svg = svg.replace("fill=\"black\"", "fill=\"#FFFFFF\"");
			svg = svg.replace("currentColor", "#FFFFFF");

			SVGUniverse universe = new SVGUniverse();
			URI uri = universe.loadSVG(new StringReader(svg), "patched");
			SVGDiagram diagram = universe.getDiagram(uri);

			return renderDiagram(diagram, width, height);
		}
	}

	// --- Public API: render from File (kept for compatibility) ---
	public static BufferedImage renderSvg(File svgFile, int width, int height) throws IOException {
		SVGUniverse universe = new SVGUniverse();
		SVGDiagram diagram = universe.getDiagram(universe.loadSVG(svgFile.toURI().toURL()));

		if (diagram == null) {
			throw new IOException("Failed to load SVG: " + svgFile);
		}

		return renderDiagram(diagram, width, height);
	}

	public static BufferedImage renderSvgPreserveColor(URL url, int width, int height) throws IOException {
		SVGUniverse universe = new SVGUniverse();

		// Load SVG directly without modifying its content
		URI uri = universe.loadSVG(url);
		SVGDiagram diagram = universe.getDiagram(uri);

		if (diagram == null) {
			throw new IOException("Failed to load SVG: " + url);
		}

		return renderDiagram(diagram, width, height);
	}

	// --- Shared rendering logic ---
	private static BufferedImage renderDiagram(SVGDiagram diagram, int width, int height) throws IOException {
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		// Get diagram size
		float diagWidth = diagram.getWidth();
		float diagHeight = diagram.getHeight();

		// Fallback to viewBox if width/height are missing
		if (diagWidth == 0 || diagHeight == 0) {
			Rectangle2D vb = diagram.getViewRect();
			diagWidth = (float) vb.getWidth();
			diagHeight = (float) vb.getHeight();
		}

		if (diagWidth == 0 || diagHeight == 0) {
			throw new IOException("SVG has invalid size (no width/height/viewBox)");
		}

		// Compute scale
		float scaleX = width / diagWidth;
		float scaleY = height / diagHeight;
		float scale = Math.min(scaleX, scaleY);

		// Center the icon
		float tx = (width - diagWidth * scale) / 2f;
		float ty = (height - diagHeight * scale) / 2f;

		g.translate(tx, ty);
		g.scale(scale, scale);

		try {
			diagram.render(g);
		} catch (Exception e) {
			throw new IOException("Failed to render SVG", e);
		} finally {
			g.dispose();
		}

		return bi;
	}

	/**
	 * Adds a black outline around and inside the opaque parts of the image.
	 * - Outer contour: black pixels around the shape
	 * - Inner contour: black pixels inside the shape where transparency borders
	 */
	public static BufferedImage addOutline(BufferedImage src) {
		int w = src.getWidth();
		int h = src.getHeight();

		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

		// Copy original
		Graphics2D g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();

		// Outline pass
		for (int y = 1; y < h - 1; y++) {
			for (int x = 1; x < w - 1; x++) {

				int argb = src.getRGB(x, y);
				int alpha = (argb >>> 24);
				if (alpha == 0)
					continue;

				boolean edge = false;

				for (int dy = -1; dy <= 1; dy++) {
					for (int dx = -1; dx <= 1; dx++) {
						if (dx == 0 && dy == 0)
							continue;
						int nAlpha = (src.getRGB(x + dx, y + dy) >>> 24);
						if (nAlpha == 0) {
							edge = true;
							break;
						}
					}
					if (edge)
						break;
				}

				if (edge) {
					// Outer contour
					for (int dy = -1; dy <= 1; dy++) {
						for (int dx = -1; dx <= 1; dx++) {
							int nx = x + dx;
							int ny = y + dy;
							if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
								out.setRGB(nx, ny, 0xFF000000);
							}
						}
					}

					// Inner contour
					out.setRGB(x, y, 0xFF000000);
				}
			}
		}

		// Draw original tinted symbol on top
		g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();

		return out;
	}

	public static BufferedImage addThinOutline(BufferedImage src) {
		int w = src.getWidth();
		int h = src.getHeight();

		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

		Graphics2D g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();

		for (int y = 1; y < h - 1; y++) {
			for (int x = 1; x < w - 1; x++) {

				int argb = src.getRGB(x, y);
				int alpha = (argb >>> 24);
				if (alpha == 0)
					continue;

				boolean edge = false;

				for (int dy = -1; dy <= 1; dy++) {
					for (int dx = -1; dx <= 1; dx++) {
						if (dx == 0 && dy == 0)
							continue;
						int nAlpha = (src.getRGB(x + dx, y + dy) >>> 24);
						if (nAlpha == 0) {
							edge = true;
							break;
						}
					}
					if (edge)
						break;
				}

				if (edge) {
					out.setRGB(x, y, 0xFF000000);
				}
			}
		}

		g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();

		return out;
	}

	public static BufferedImage addSafeOutline(BufferedImage src) {
		int w = src.getWidth();
		int h = src.getHeight();

		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

		// Copy original
		Graphics2D g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();

		// Outline pass: draw ONLY on transparent pixels
		for (int y = 1; y < h - 1; y++) {
			for (int x = 1; x < w - 1; x++) {

				int argb = src.getRGB(x, y);
				int alpha = (argb >>> 24);

				// Only outline transparent pixels
				if (alpha != 0)
					continue;

				boolean nearOpaque = false;

				for (int dy = -1; dy <= 1; dy++) {
					for (int dx = -1; dx <= 1; dx++) {
						int nAlpha = (src.getRGB(x + dx, y + dy) >>> 24);
						if (nAlpha > 128) {
							nearOpaque = true;
							break;
						}
					}
					if (nearOpaque)
						break;
				}

				if (nearOpaque) {
					out.setRGB(x, y, 0xFF000000); // black outline pixel
				}
			}
		}

		return out;
	}

	public static BufferedImage addLightOutline(BufferedImage src) {
		int w = src.getWidth();
		int h = src.getHeight();

		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

		Graphics2D g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();

		for (int y = 1; y < h - 1; y++) {
			for (int x = 1; x < w - 1; x++) {

				int argb = src.getRGB(x, y);
				int alpha = (argb >>> 24);
				if (alpha == 0)
					continue;

				boolean edge = false;

				for (int dy = -1; dy <= 1; dy++) {
					for (int dx = -1; dx <= 1; dx++) {
						if (dx == 0 && dy == 0)
							continue;
						int nAlpha = (src.getRGB(x + dx, y + dy) >>> 24);
						if (nAlpha == 0) {
							edge = true;
							break;
						}
					}
					if (edge)
						break;
				}

				if (edge) {
					out.setRGB(x, y, 0xFF000000);
				}
			}
		}

		g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();

		return out;
	}

	public static BufferedImage applyMediumSharpen(BufferedImage src) {
		float[] kernel = { 0f, -1f, 0f, -1f, 5f, -1f, 0f, -1f, 0f };

		ConvolveOp op = new ConvolveOp(new Kernel(3, 3, kernel), ConvolveOp.EDGE_NO_OP, null);
		return op.filter(src, null);
	}

	public static BufferedImage applyLightSharpen(BufferedImage src) {
		float[] kernel = { 0f, -0.5f, 0f, -0.5f, 3f, -0.5f, 0f, -0.5f, 0f };

		ConvolveOp op = new ConvolveOp(new Kernel(3, 3, kernel), ConvolveOp.EDGE_NO_OP, null);
		return op.filter(src, null);
	}

	public static BufferedImage scale(BufferedImage src, int w, int h) {
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return dst;
	}

	public static BufferedImage tint(BufferedImage src, Color tint) {
		int w = src.getWidth();
		int h = src.getHeight();

		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

		int tr = tint.getRed();
		int tg = tint.getGreen();
		int tb = tint.getBlue();

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = src.getRGB(x, y);

				int alpha = (argb >> 24) & 0xFF;
				if (alpha == 0) {
					out.setRGB(x, y, 0); // fully transparent
					continue;
				}

				int tinted = (alpha << 24) | (tr << 16) | (tg << 8) | tb;
				out.setRGB(x, y, tinted);
			}
		}

		return out;
	}
}