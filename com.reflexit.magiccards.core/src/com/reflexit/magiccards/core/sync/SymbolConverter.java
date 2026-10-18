/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - Scryfall API policy: "ManaDesk/<version>" User-Agent
 *                         and an Accept header.
 *     Rémi Dutil (2026) - symbols now come from ScryfallReference (bundled snapshot,
 *                         refreshed on database update); missing PNGs are rendered on demand.
 *******************************************************************************/

package com.reflexit.magiccards.core.sync;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.sync.ScryfallReference.Symbol;

/**
 * Maps a card symbol ({@code {R/G/P}}) to Scryfall's picture of it. The symbol list
 * itself lives in {@link ScryfallReference} (bundled snapshot, refreshed from
 * Scryfall's {@code /symbology} when the card database is updated), so a new symbol
 * needs no code change.
 */
public final class SymbolConverter {

	private SymbolConverter() {
	}

	/** Returns the canonical Scryfall filename for a mana symbol. */
	public static String getScryfallFilename(String symbol) {
		return ScryfallReference.symbolFile(symbol);
	}

	/** Returns the Scryfall SVG URL for a mana symbol. */
	public static URL getScryfallManaURL(String symbol) {
		Symbol s = ScryfallReference.symbols().get(symbol);
		if (s == null) {
			System.err.println("Unknown mana symbol: " + symbol);
			return null;
		}
		try {
			return new URL(s.svgUri);
		} catch (Exception e) {
			System.err.println("Invalid URL for symbol " + symbol + ": " + e.getMessage());
			return null;
		}
	}

	// =====================================================================================
	//  PNGs for symbols that were not bundled with the application
	// =====================================================================================

	/** Where the pictures of symbols that were not bundled are kept. */
	public static File getLocalPngDir() {
		File d = new File(new File(FileUtils.getStateLocationFile(), "scryfall"), "symbols");
		d.mkdirs();
		return d;
	}

	/** @return the rendered picture of a symbol file ({@code "RG.svg"} or {@code "RG.png"}), or {@code null} if not rendered yet */
	public static File getLocalPng(String file) {
		if (file == null)
			return null;
		File f = new File(getLocalPngDir(), pngName(file));
		return f.isFile() && f.length() > 0 ? f : null;
	}

	/** {@code "RG.svg"} -> {@code "RG.png"} */
	static String pngName(String file) {
		return file.endsWith(".svg") ? file.substring(0, file.length() - 4) + ".png" : file;
	}

	/** Tells which symbol pictures already ship with the application. */
	public interface Bundled {
		boolean has(String pngName);
	}

	/**
	 * Renders the pictures of the symbols the application does not ship with (new
	 * symbols Scryfall added since the build). Best effort - blocking, never call
	 * from the UI thread.
	 *
	 * @return the number of pictures rendered
	 */
	public static int renderMissingPngs(Bundled bundled) {
		int n = 0;
		File dir = getLocalPngDir();
		for (Symbol s : ScryfallReference.symbols().values()) {
			String png = pngName(s.file);
			if (bundled.has(png) || new File(dir, png).isFile())
				continue;
			try {
				BufferedImage bi = SvgRasterizer.renderSvgPreserveColor(new URL(s.svgUri), 19, 19);
				File tmp = new File(dir, png + ".tmp");
				try (OutputStream out = new FileOutputStream(tmp)) {
					ImageIO.write(bi, "png", out);
				}
				File dest = new File(dir, png);
				if (dest.exists())
					dest.delete();
				if (tmp.renameTo(dest))
					n++;
			} catch (Exception e) {
				if (WebUtils.isWebUnavailable(e))
					break;
				MagicLogger.log("Could not render symbol " + s.symbol + ": " + e.getMessage());
			}
		}
		return n;
	}

	// =====================================================================================
	//  MAIN - refreshes the snapshot that ships with the build, and renders every PNG
	// =====================================================================================

	/**
	 * Developer tool. {@code args[0]}: folder to write Scryfall's lists to (copy them to
	 * {@code core/.../sync/reference/} to refresh the bundled snapshot). The symbol
	 * PNGs are written to {@code <tmp>/symbols} (copy to {@code ui/icons/symbols}).
	 * Neither is required for the application to work - it renders what is missing.
	 */
	public static void main(String[] args) {
		try {
			File snapshot = new File(
					args.length > 0 ? args[0] : new File(System.getProperty("java.io.tmpdir"), "reference").getPath());
			ScryfallReference.downloadSnapshot(snapshot);
			System.out.println("Scryfall lists written to: " + snapshot.getAbsolutePath());

			File outDir = new File(System.getProperty("java.io.tmpdir"), "symbols");
			if (!outDir.exists() && !outDir.mkdirs()) {
				System.err.println("ERROR: Could not create output directory: " + outDir);
				return;
			}
			String json = new String(Files.readAllBytes(new File(snapshot, "symbology.json").toPath()), "UTF-8");
			List<Symbol> all = new ArrayList<Symbol>(ScryfallReference.parseSymbology(json).values());
			for (Symbol s : all) {
				try {
					BufferedImage bi = SvgRasterizer.renderSvgPreserveColor(new URL(s.svgUri), 19, 19);
					File outPng = new File(outDir, pngName(s.file));
					try (OutputStream out = new FileOutputStream(outPng)) {
						ImageIO.write(bi, "png", out);
					}
					System.out.println("Wrote PNG: " + outPng.getName());
				} catch (Exception e) {
					System.err.println("WARNING: Failed to convert " + s.svgUri + ": " + e.getMessage());
				}
			}
			System.out.println("PNGs written to: " + outDir.getAbsolutePath());
		} catch (Exception e) {
			System.err.println("FATAL ERROR in SymbolConverter.main: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
