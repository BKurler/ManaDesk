/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - ensureSetSymbolExists()/renderWithBackground(): the
 *                         icon was painted on a SOLID rarity-colour square
 *                         (filled the whole 19x19 canvas) with the raw,
 *                         un-tinted SVG glyph drawn on top - it displayed as
 *                         a blocky coloured square with a plain black shape
 *                         cut into it, not the usual thin rarity-tinted
 *                         symbol on a transparent background. Switched to
 *                         the same render-then-tint-on-transparent approach
 *                         getImageCachedFile() already used correctly.
 *     Rémi Dutil (2026) - createSetImageRemoteURL(): null-check the looked-up
 *                         Edition before calling getIconAbbr() on it -
 *                         getEditionByAbbr() legitimately returns null for an
 *                         unresolved abbreviation or before the edition DB
 *                         has loaded, which was throwing an uncaught NPE (its
 *                         only try/catch here only catches
 *                         MalformedURLException) on every set-icon paint
 *                         until the DB finished loading - callers already
 *                         handle a null return (no icon yet) gracefully
 *     Rémi Dutil (2026) - RarityColor.COMMON: a prior "brighten the rarity
 *                         tints" pass had accidentally flipped this one to
 *                         pure white (0xFFFFFF) while every other entry got
 *                         a genuine brightening tweak - its own "// black"
 *                         comment never changed, only the value did, which
 *                         is how it went unnoticed. Restored to black
 *                         (0x000000), matching the comment and every other
 *                         Common-rarity set symbol Scryfall itself renders.
 *     Rémi Dutil (2026) - ensureSetSymbolExists(): no stack trace when the
 *                         web is not accessible (no icon for now, retried
 *                         later).
 *     Rémi Dutil (2026) - isStaleCommonIcon(): Common icons cached while
 *                         COMMON was white are never regenerated (a cached
 *                         file is always reused), so they stayed white on
 *                         white after the fix - e.g. a 0.9.6 cache carried
 *                         over to 0.9.7. An all-white Common icon is now
 *                         deleted and re-rendered black.
 */

package com.reflexit.magiccards.core.sync;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import javax.imageio.ImageIO;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.Rarity;

public class EditionFileCache {
	private Edition edition;
	private HashMap<String, CachedFile> map = new HashMap<>(3);

	public EditionFileCache(Edition ed) {
		this.edition = ed;
	}

	/**
	 * Ensures that the set symbol PNG exists locally. Downloads the SVG,
	 * rasterizes it at 19x19 on a transparent background, tints it per
	 * rarity, and saves it as a PNG.
	 */
	public File ensureSetSymbolExists(String rarity) {
		try {
			rarity = normalizeRarity(rarity);

			String localPath = createLocalSetImageFilePath(rarity);
			File localFile = new File(localPath);

			if (localFile.exists() && !isStaleCommonIcon(rarity, localFile)) {
				return localFile;
			}

			String editionAbbr = edition.getMainAbbreviation();
			if (editionAbbr == null || editionAbbr.isBlank()) {
				return null;
			}

			URL svgUrl = EditionFileCache.createSetImageRemoteURL(editionAbbr);
			if (svgUrl == null) {
				return null;
			}

			RarityColor rc = RarityColor.valueOf(rarity.toUpperCase());

			BufferedImage bi = SvgRasterizer.renderSvg(svgUrl, 19, 19);
			bi = SvgRasterizer.tint(bi, rc.color);

			localFile.getParentFile().mkdirs();
			ImageIO.write(bi, "PNG", localFile);

			return localFile;

		} catch (Exception e) {
			if (!WebUtils.isWebUnavailable(e)) // no web: just no icon for now, retried later
				e.printStackTrace();
			return null;
		}
	}

	public String normalizeRarity(String rarity) {
		if ("Land".equals(rarity))
			return "Common";
		return rarity;
	}

	public CachedFile getImageCachedFile(String rarity, boolean forceRemote) throws IOException {
		if ("Land".equals(rarity))
			rarity = "Common";
		CachedFile cachedFile = map.get(rarity);
		if (cachedFile != null) {
			if (forceRemote == false || cachedFile.getRemoteURL() != null)
				return cachedFile;
		}
		String path = createLocalSetImageFilePath(rarity);
		if (forceRemote == false) {
			cachedFile = new CachedFile(null, new File(path));
			if (cachedFile.isImage() && !isStaleCommonIcon(rarity, new File(path))) {
				map.put(rarity, cachedFile);
				return cachedFile;
			}
		}
		String tryRarity = rarity;
		while (tryRarity != null) {
			String[] abbreviations = edition.getAbbreviations();
			for (int i = 0; i < abbreviations.length; i++) {
				String editionAbbr = abbreviations[i];
				URL url = createSetImageRemoteURL(editionAbbr);

				if (url == null) {
					// Normal if the iconAbbr is unknown
					continue;
				}

				if (url.getPath().toLowerCase().endsWith(".svg")) {

					try {
						// Rasterize directly from URL
						BufferedImage bi = SvgRasterizer.renderSvg(url, 19, 19);

						// Apply rarity tint
						Color tint = RarityColor.valueOf(rarity.toUpperCase()).color;
						bi = SvgRasterizer.tint(bi, tint);

						// Save PNG
						File pngFile = new File(path);
						File parent = pngFile.getParentFile();
						if (!parent.exists()) {
							parent.mkdirs();
						}

						ImageIO.write(bi, "PNG", pngFile);

						cachedFile = new CachedFile(pngFile.toURI().toURL(), pngFile);
						map.put(rarity, cachedFile);
						return cachedFile;

					} catch (Exception e) {
						MagicLogger.log("Failed to rasterize SVG: " + url + ": " + e.getMessage());
						return null;
					}
				}

				cachedFile.recache();
				map.put(rarity, cachedFile);
				return cachedFile;
			}
			tryRarity = Rarity.getMoreRare(tryRarity);
		}
		return null;
	}

	/**
	 * True for a Common icon rendered while RarityColor.COMMON was white
	 * (every visible pixel white) - deletes it so the caller re-renders it.
	 */
	private static boolean isStaleCommonIcon(String rarity, File file) {
		if (!"Common".equalsIgnoreCase(rarity) || !file.exists())
			return false;
		try {
			BufferedImage bi = ImageIO.read(file);
			if (bi == null)
				return false;
			boolean any = false;
			for (int y = 0; y < bi.getHeight(); y++)
				for (int x = 0; x < bi.getWidth(); x++) {
					int argb = bi.getRGB(x, y);
					if ((argb >>> 24) == 0)
						continue;
					if ((argb & 0xFFFFFF) != 0xFFFFFF)
						return false;
					any = true;
				}
			if (any)
				file.delete();
			return any;
		} catch (IOException e) {
			return false;
		}
	}

	public enum RarityColor {
		COMMON(new Color(0x000000)), // black
		UNCOMMON(new Color(0xC8C8C8)), // brighter silver
		RARE(new Color(0xF4C542)), // brighter gold
		MYTHIC(new Color(0xE84A1A)), // vivid mythic orange
		SPECIAL(new Color(0x9BE89B)); // brighter green

		public final Color color;

		RarityColor(Color c) {
			this.color = c;
		}
	}

	public URL getLocalURL(String rarity, boolean upload) throws IOException {
		if (upload == false)
			return createSetImageLocalURL(rarity);
		CachedFile onefile = getImageCachedFile(rarity, false);
		if (onefile != null)
			return onefile.getLocalURL(false);
		return createSetImageLocalURL(rarity);
	}

	public URL createSetImageLocalURL(String rarity) {
		String path = createLocalSetImageFilePath(rarity);
		URL localUrl = null;
		try {
			localUrl = new File(path).toURI().toURL();
		} catch (MalformedURLException e) {
			// should not happen
		}
		return localUrl;
	}

	// !!! RD Should be rework to get this from parseScryfall class but acceptable for now
	public static URL createSetImageRemoteURL(String editionAbbr) throws MalformedURLException {
		Editions editions = Editions.getInstance();
		Edition ed = editions.getEditionByAbbr(editionAbbr);
		if (ed == null)
			return null; // unresolved abbreviation, or the edition DB isn't loaded yet
		try {
			if (ed.getIconAbbr() != null && !ed.getIconAbbr().equals("null") && !ed.getIconAbbr().isBlank()) {
				return new URL("https://svgs.scryfall.io/sets/" + ed.getIconAbbr() + ".svg?1770008400");
			}
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private String createLocalSetImageFilePath(String rarity) {
		if ("Land".equals(rarity))
			rarity = "Common";
		File loc = FileUtils.getStateLocationFile();

		// RD Modification to use the common "icon" set for special sets like Arts, Tokens, Promos, etc
		String name = edition.getIconBaseFileName() + "-" + rarity;
		String part = "Sets/" + name + ".png";
		String file = new File(loc, part).getPath();

		return file;
	}
}
