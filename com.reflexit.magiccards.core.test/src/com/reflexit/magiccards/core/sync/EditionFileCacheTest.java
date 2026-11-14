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
import java.net.URL;
import java.nio.file.Files;

import javax.imageio.ImageIO;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.sync.EditionFileCache.RarityColor;

/**
 * The set icons kept per set and rarity: where the file goes, which rarity is used for a land,
 * the address of the icon, what happens to a cached icon (kept, or thrown away when it is the old
 * all-white Common one) and a set with no icon. Icons live in a temp folder and the sets are
 * not registered, so nothing goes on the web and the real ManaDesk folder is not touched. Works
 * as plain JUnit.
 */
public class EditionFileCacheTest extends TestCase {
	private File dir;
	private Edition edition;
	private EditionFileCache cache;
	private Edition registered;

	@Override
	protected void setUp() throws Exception {
		dir = Files.createTempDirectory("editioncache-test").toFile();
		EditionFileCache.useDirForTests(dir);
		edition = new Edition("Icon Cache Test Set", "ICT");
		cache = new EditionFileCache(edition);
	}

	@Override
	protected void tearDown() throws Exception {
		EditionFileCache.useDirForTests(null);
		if (registered != null)
			Editions.getInstance().remove(registered);
		FileUtils.deleteTree(dir);
	}

	private File icon(String rarity) {
		return new File(new File(dir, "Sets"), "ICT-" + rarity + ".png");
	}

	private File saveIcon(String rarity, int argb) throws IOException {
		BufferedImage img = new BufferedImage(19, 19, BufferedImage.TYPE_INT_ARGB);
		for (int y = 4; y < 15; y++)
			for (int x = 4; x < 15; x++)
				img.setRGB(x, y, argb);
		File f = icon(rarity);
		f.getParentFile().mkdirs();
		ImageIO.write(img, "png", f);
		return f;
	}

	// ---- rarity

	public void testALandUsesTheCommonIcon() {
		assertEquals("Common", cache.normalizeRarity("Land"));
		assertEquals("Rare", cache.normalizeRarity("Rare"));
		assertNull(cache.normalizeRarity(null));
	}

	public void testRarityColors() {
		assertEquals(0x000000, RarityColor.COMMON.color.getRGB() & 0xFFFFFF);
		assertEquals(0xF4C542, RarityColor.RARE.color.getRGB() & 0xFFFFFF);
		assertEquals(5, RarityColor.values().length);
	}

	// ---- where the icon is

	public void testTheLocalAddressIsInTheSetsFolderNamedBySetAndRarity() throws Exception {
		assertEquals(icon("Rare").toURI().toURL(), cache.createSetImageLocalURL("Rare"));
	}

	public void testALandIconIsTheCommonOne() throws Exception {
		assertEquals(icon("Common").toURI().toURL(), cache.createSetImageLocalURL("Land"));
	}

	public void testWithoutUploadTheLocalAddressIsGivenEvenIfThereIsNoFile() throws Exception {
		assertEquals(icon("Rare").toURI().toURL(), cache.getLocalURL("Rare", false));
		assertFalse(icon("Rare").exists());
	}

	public void testWithUploadACachedIconGivesItsAddress() throws Exception {
		saveIcon("Rare", 0xFFF4C542);
		assertEquals(icon("Rare").toURI().toURL(), cache.getLocalURL("Rare", true));
	}

	// ---- the remote address

	public void testNoRemoteAddressForAnUnknownSet() throws Exception {
		assertNull(EditionFileCache.createSetImageRemoteURL("NOSUCHSET"));
	}

	public void testTheRemoteAddressComesFromTheIconCode() throws Exception {
		registered = Editions.getInstance().addEdition("Icon Remote Test Set", "IRT");
		registered.setIconAbbr("m21");
		URL url = EditionFileCache.createSetImageRemoteURL("IRT");
		assertNotNull(url);
		assertTrue(url.toString(), url.toString().startsWith("https://svgs.scryfall.io/sets/m21.svg"));
	}

	public void testNoRemoteAddressWithoutAUsableIconCode() throws Exception {
		registered = Editions.getInstance().addEdition("Icon Remote Test Set", "IRT");
		for (String code : new String[] { null, "", "  ", "null" }) {
			registered.setIconAbbr(code);
			assertNull("icon code <" + code + ">", EditionFileCache.createSetImageRemoteURL("IRT"));
		}
	}

	// ---- the cached icon

	public void testACachedIconIsKept() throws Exception {
		File png = saveIcon("Rare", 0xFFF4C542);
		assertEquals(png, cache.ensureSetSymbolExists("Rare"));
		assertTrue(png.exists());
	}

	public void testACachedBlackCommonIconIsKept() throws Exception {
		File png = saveIcon("Common", 0xFF000000);
		assertEquals(png, cache.ensureSetSymbolExists("Common"));
		assertEquals("a Land uses it too", png, cache.ensureSetSymbolExists("Land"));
	}

	public void testAnOldAllWhiteCommonIconIsThrownAway() throws Exception {
		File png = saveIcon("Common", 0xFFFFFFFF);
		// the set is not registered, so there is no address to render it from again: no icon for now
		assertNull(cache.ensureSetSymbolExists("Common"));
		assertFalse("the stale icon is deleted", png.exists());
	}

	public void testAnAllWhiteIconOfAnotherRarityIsKept() throws Exception {
		File png = saveIcon("Rare", 0xFFFFFFFF);
		assertEquals(png, cache.ensureSetSymbolExists("Rare"));
	}

	public void testNoIconWhenThereIsNothingToRenderItFrom() throws Exception {
		assertNull(cache.ensureSetSymbolExists("Rare"));
		assertFalse(icon("Rare").exists());
	}

	// ---- the cached file object

	public void testACachedPictureIsReturnedAndRemembered() throws Exception {
		saveIcon("Rare", 0xFFF4C542);
		CachedFile first = cache.getImageCachedFile("Rare", false);
		assertNotNull(first);
		assertTrue(first.isImage());
		assertSame(first, cache.getImageCachedFile("Rare", false));
	}

	public void testALandIsLookedUpAsCommon() throws Exception {
		saveIcon("Common", 0xFF000000);
		assertNotNull(cache.getImageCachedFile("Land", false));
	}

	public void testNothingCachedAndNothingToDownloadGivesNull() throws Exception {
		assertNull(cache.getImageCachedFile("Rare", false));
	}

	public void testAnOldAllWhiteCommonPictureIsNotReturned() throws Exception {
		File png = saveIcon("Common", 0xFFFFFFFF);
		assertNull(cache.getImageCachedFile("Common", false));
		assertFalse(png.exists());
	}
}
