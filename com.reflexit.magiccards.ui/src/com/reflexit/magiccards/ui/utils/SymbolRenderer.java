/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - .proxywrap / .proxymark styles for proxy card art (grey stamp)
 *******************************************************************************/
package com.reflexit.magiccards.ui.utils;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.reflexit.magiccards.core.sync.SymbolConverter;

public final class SymbolRenderer {

	public static final int SYMBOL_SIZE = 16;

	private static final String bundleBase;

	static {
		String base;
		try {
			Bundle bundle = FrameworkUtil.getBundle(SymbolRenderer.class);
			URL root = bundle.getEntry("/"); // bundle root
			URL fileUrl = FileLocator.toFileURL(root); // convert to file: URL
			base = fileUrl.toExternalForm(); // e.g. file:/C:/workspace/.../com.reflexit.magiccards.ui/
		} catch (Exception e) {
			base = ".";
		}
		bundleBase = base;
	}

	private SymbolRenderer() {
	}

	// scaled symbols (per PNG path)
	private static final Map<String, Image> CACHE = new HashMap<>();
	// full cost images (per cost string)
	private static final Map<String, Image> COST_CACHE = new HashMap<>();
	// base PNGs (per PNG path)
	private static final Map<String, Image> BASE_SYMBOL_CACHE = new HashMap<>();

	// Load a single mana symbol PNG from icons/symbols/
	public static Image getManaSymbolImage(String symbol, String path) {
		Image cached = BASE_SYMBOL_CACHE.get(path);
		if (cached != null && !cached.isDisposed()) {
			return cached;
		}

		try {
			URL url = SymbolRenderer.class.getClassLoader().getResource(path);
			if (url == null) {
				System.err.println("Missing symbol PNG: " + path);
				return null;
			}
			Image img = new Image(Display.getDefault(), url.openStream());
			BASE_SYMBOL_CACHE.put(path, img);
			return img;
		} catch (Exception e) {
			System.err.println("Failed to load symbol PNG: " + path + " : " + e.getMessage());
			return null;
		}
	}

	// Scale to standard height, cached per key (typically "scaled:" + pngPath)
	public static Image scaleToSymbolHeight(Image img, String key) {
		if (img == null) {
			return null;
		}

		Image cached = CACHE.get(key);
		if (cached != null && !cached.isDisposed()) {
			return cached;
		}

		Rectangle b = img.getBounds();
		int target = SYMBOL_SIZE;
		int w = b.width * target / b.height;

		// Create alpha-enabled image
		PaletteData palette = new PaletteData(0xFF0000, 0x00FF00, 0x0000FF);
		ImageData data = new ImageData(w, target, 32, palette);
		data.alphaData = new byte[w * target]; // fully transparent

		Image scaled = new Image(Display.getDefault(), data);

		GC gc = new GC(scaled);
		gc.setAntialias(SWT.ON);
		gc.setInterpolation(SWT.HIGH);
		gc.drawImage(img, 0, 0, b.width, b.height, 0, 0, w, target);
		gc.dispose();

		CACHE.put(key, scaled);
		return scaled;
	}

	// Parse "{G}{U}" and return a list of SWT Images (cached)
	private static Collection<Image> getManaImages(String cost) {
		if (cost == null || cost.isEmpty())
			return Collections.emptyList();

		Collection<Image> result = new ArrayList<>();
		String text = cost.trim();

		while (!text.isEmpty()) {
			int start = text.indexOf('{');
			int end = text.indexOf('}');

			if (start != 0 || end < 0) {
				// Invalid mana cost (e.g. "*", "—", "N/A") → no symbols
				return Collections.emptyList();
			}

			String symbol = text.substring(0, end + 1);
			text = text.substring(end + 1).trim();

			String file = SymbolConverter.getScryfallFilename(symbol);
			if (file == null) {
				System.err.println("Unknown mana symbol: " + symbol);
				continue;
			}

			String pngPath = "icons/symbols/" + file.replace(".svg", ".png");

			Image base = getManaSymbolImage(symbol, pngPath);
			if (base != null) {
				Image scaled = scaleToSymbolHeight(base, "scaled:" + pngPath);
				if (scaled != null) {
					result.add(scaled);
				}
			}
		}

		return result;
	}

	public static Image buildCostImage(String cost) {
		if (cost == null || cost.isEmpty())
			return null;

		Image cached = COST_CACHE.get(cost);
		if (cached != null && !cached.isDisposed()) {
			return cached;
		}

		Image img = buildCostImageInternal(cost);
		if (img != null) {
			COST_CACHE.put(cost, img);
		}
		return img;
	}

	public static Image buildCostImageInternal(String cost) {
		Collection<Image> imgs = getManaImages(cost);
		if (imgs.isEmpty())
			return null;

		int totalWidth = 0;
		int height = SYMBOL_SIZE;

		for (Image img : imgs) {
			totalWidth += img.getBounds().width;
		}

		// Create an alpha-enabled image
		PaletteData palette = new PaletteData(0xFF0000, 0x00FF00, 0x0000FF);
		ImageData data = new ImageData(totalWidth, height, 32, palette);
		data.alphaData = new byte[totalWidth * height]; // fully transparent

		Image result = new Image(Display.getDefault(), data);
		GC gc = new GC(result);
		gc.setAntialias(SWT.ON);
		gc.setInterpolation(SWT.HIGH);

		int x = 0;
		for (Image img : imgs) {
			Rectangle b = img.getBounds();
			gc.drawImage(img, 0, 0, b.width, b.height, x, 0, b.width, height);
			x += b.width;
		}

		gc.dispose();
		return result;
	}

	public static String wrapHtml(String html, Browser browser) {
		if (html == null)
			return "";

		// Replace {R}, {G}, {R/G}, etc. with <img> tags
		html = replaceSymbolsWithHtmlImages(html);

		// Build SWT-based style
		String style = buildHtmlStyle(browser);

		// Full HTML with <base href> so <img src="icons/..."> resolves
		return "<head>" + "<base href=\"" + bundleBase + "\"/>" + "<style>"
				+ "body { margin: 8px; padding: 0; line-height: 1.4; }" + "p { margin: 6px 0; }"
				+ "a { color: #0645AD; text-decoration: none; }" + "a:hover { text-decoration: underline; }"
				+ "img { vertical-align: middle; }"

				// Card image always above text, responsive
				+ ".cardimage {" + "    display: block;" + "    margin: 0 auto 8px auto;" + "    width: 100%;"
				+ "    max-width: 600px;" // adjust to your preferred card width
				+ "    height: auto;" + "    border-radius: 4px;" + "}"

				// Text block below image, stable layout
				+ ".cardtext {" + "    margin-top: 8px;" + "}"

				// Proxy copy: faded / greyed art with a diagonal "Proxy" stamp
				+ ".proxywrap {" + "    position: relative;" + "    display: block;" + "    margin: 0 auto 8px auto;"
				+ "    width: 100%;" + "    max-width: 600px;" + "}"
				+ ".proxywrap .cardimage {" + "    margin: 0;" + "    filter: grayscale(100%);"
				+ "    opacity: 0.55;" + "}"
				+ ".proxymark {" + "    position: absolute;" + "    top: 50%;" + "    left: 50%;"
				+ "    transform: translate(-50%, -50%) rotate(-32deg);" + "    font-size: 44px;"
				+ "    font-weight: bold;" + "    letter-spacing: 10px;" + "    text-transform: uppercase;"
				+ "    color: rgba(110, 110, 110, 0.72);" + "    border: 5px solid rgba(110, 110, 110, 0.6);"
				+ "    border-radius: 8px;" + "    padding: 6px 30px;" + "    white-space: nowrap;"
				+ "    pointer-events: none;" + "}"

				+ "</style>" + "</head>" + "<body style='overflow:auto;" + style + "'>" + html + "<script>"
				+ "window.onload = function() {" + "  var imgs = document.images;"
				+ "  for (var i = 0; i < imgs.length; i++) {" + "    imgs[i].onload = function(){};" + "  }" + "};"
				+ "</script>" +

				"</body></html>";
	}

	private static String buildHtmlStyle(Browser browser) {
		org.eclipse.swt.graphics.FontData fd = browser.getFont().getFontData()[0];
		org.eclipse.swt.graphics.RGB bg = browser.getBackground().getRGB();
		org.eclipse.swt.graphics.RGB fg = browser.getForeground().getRGB();

		return "font-size:" + fd.getHeight() + "pt;" + "background-color: rgb(" + bg.red + "," + bg.green + ","
				+ bg.blue + ");" + "color: rgb(" + fg.red + "," + fg.green + "," + fg.blue + ");" + "font-family:"
				+ fd.getName() + ";";
	}

	public static String replaceSymbolsWithHtmlImages(String html) {
		if (html == null || html.isEmpty())
			return "";

		StringBuilder out = new StringBuilder(html.length());

		int i = 0;
		while (i < html.length()) {
			char c = html.charAt(i);

			// Look for "{...}"
			if (c == '{') {
				int end = html.indexOf('}', i);
				if (end > i) {
					String symbol = html.substring(i, end + 1); // e.g. "{R}", "{R/G}", "{2/W}"

					// ---------------------------------------------------------
					// NEW: handle {SETICON:<abbr>}
					// Example: {SETICON:KHM-Rare} → Sets/KHM-Rare.png
					// ---------------------------------------------------------
					if (symbol.startsWith("{SETICON:")) {
						String inner = symbol.substring(1, symbol.length() - 1);
						String abbr = inner.substring("SETICON:".length());

						String fileUrl = ImageCreator.getInstance().getSetIconFileURL(abbr);
						if (fileUrl != null) {
							out.append("<img src=\"").append(fileUrl)
									.append("\" style=\"vertical-align:middle; margin-right:4px;\"/>");
						}

						i = end + 1;
						continue;
					}

					// Ask SymbolConverter for the filename
					String file = SymbolConverter.getScryfallFilename(symbol);
					if (file != null) {
						String png = file.replace(".svg", ".png");
						String path = "icons/symbols/" + png;

						// Insert HTML <img> tag
						out.append("<img src=\"").append(path).append("\" style=\"vertical-align:middle;\"/>");

						i = end + 1;
						continue;
					}
				}
			}

			// Default: copy character
			out.append(c);
			i++;
		}

		return out.toString();
	}

}
