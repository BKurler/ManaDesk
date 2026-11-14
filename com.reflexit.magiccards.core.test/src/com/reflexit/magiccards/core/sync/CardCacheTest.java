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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.CachedImageNotFoundException;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;

/**
 * How card images are saved to and found in the local cache: the path comes from a provider
 * (set by the UI), the "remote" image is a {@code file:} URL here so nothing leaves the
 * machine, and the web being down is simulated. Everything lives in a temp folder, deleted in
 * tearDown. Works as plain JUnit.
 */
public class CardCacheTest extends TestCase {
	private File dir;
	private File source;
	private MagicCard card;

	@Override
	protected void setUp() throws Exception {
		dir = Files.createTempDirectory("cardcache-test").toFile();
		source = new File(dir, "remote.png");
		write(source, "image bytes v1");
		card = new MagicCard();
		card.setName("Cache Test Card");
		card.setProperty(MagicCardField.IMAGE_URL, source.toURI().toURL().toExternalForm());
		CardCache.setPathProvider(new ICardImagePathProvider() {
			@Override
			public File getLocalImagePath(IMagicCard c) {
				return new File(new File(dir, "cache"), "card.png");
			}
		});
	}

	@Override
	protected void tearDown() throws Exception {
		CardCache.setPathProvider(null);
		WebUtils.setSimulateWebDown(false);
		FileUtils.deleteTree(dir);
	}

	private static void write(File f, String text) throws IOException {
		Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private static String read(File f) throws IOException {
		return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
	}

	private File local() {
		return new File(new File(dir, "cache"), "card.png");
	}

	// ---- the provider

	public void testNeedsAPathProvider() {
		CardCache.setPathProvider(null);
		try {
			CardCache.isImageCached(card);
			fail();
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("not set"));
		}
	}

	public void testProviderMustAnswer() {
		CardCache.setPathProvider(new ICardImagePathProvider() {
			@Override
			public File getLocalImagePath(IMagicCard c) {
				return null;
			}
		});
		try {
			CardCache.isImageCached(card);
			fail();
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("returned null"));
		}
	}

	// ---- is it cached

	public void testNotCachedUntilSaved() throws Exception {
		assertFalse(CardCache.isImageCached(card));
		CardCache.downloadAndSaveImage(card, true, false);
		assertTrue(CardCache.isImageCached(card));
	}

	// ---- downloading into the cache

	public void testDownloadSavesTheImageUnderTheProvidedPath() throws Exception {
		File saved = CardCache.downloadAndSaveImage(card, true, false);
		assertEquals(local(), saved);
		assertEquals("image bytes v1", read(saved));
	}

	public void testNoDownloadWhenOnlyTheCacheMayBeUsed() throws Exception {
		try {
			CardCache.downloadAndSaveImage(card, false, false);
			fail();
		} catch (CachedImageNotFoundException e) {
			assertTrue(e.getMessage().contains("Cache Test Card"));
		}
		assertFalse(local().exists());
	}

	public void testACachedImageIsNotDownloadedAgain() throws Exception {
		CardCache.downloadAndSaveImage(card, true, false);
		write(source, "image bytes v2");
		File again = CardCache.downloadAndSaveImage(card, true, false);
		assertEquals("the cached file wins", "image bytes v1", read(again));
	}

	public void testForcingTheDownloadReplacesTheCachedImage() throws Exception {
		CardCache.downloadAndSaveImage(card, true, false);
		write(source, "image bytes v2");
		File again = CardCache.downloadAndSaveImage(card, true, true);
		assertEquals("image bytes v2", read(again));
	}

	public void testNoDownloadWithoutAnImageAddress() throws Exception {
		card.setProperty(MagicCardField.IMAGE_URL, null);
		try {
			CardCache.downloadAndSaveImage(card, true, false);
			fail();
		} catch (CachedImageNotFoundException e) {
			assertTrue(e.getMessage().contains("no URL"));
		}
	}

	// ---- the save itself

	public void testSaveReplacesAnExistingFileAndLeavesNoPartFile() throws Exception {
		File target = new File(dir, "out/image.png");
		CardCache.saveCachedFile(target, source.toURI().toURL());
		write(source, "second");
		CardCache.saveCachedFile(target, source.toURI().toURL());
		assertEquals("second", read(target));
		String[] left = target.getParentFile().list();
		assertEquals("only the image remains, no .part file", 1, left.length);
	}

	public void testSaveRefusesAnEmptyFile() throws Exception {
		File empty = new File(dir, "empty.png");
		write(empty, "");
		File target = new File(dir, "out2/image.png");
		try {
			CardCache.saveCachedFile(target, empty.toURI().toURL());
			fail();
		} catch (IOException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("Cannot save file"));
		}
		assertFalse(target.exists());
		assertEquals(0, target.getParentFile().list().length);
	}

	public void testSaveReportsAnUnreachableSource() throws Exception {
		File target = new File(dir, "out3/image.png");
		try {
			CardCache.saveCachedFile(target, new File(dir, "does-not-exist.png").toURI().toURL());
			fail();
		} catch (IOException e) {
			assertTrue(e.getMessage(), e.getMessage().startsWith("Cannot connect"));
		}
		assertEquals(0, target.getParentFile().list().length);
	}

	// ---- which address to show

	public void testShowsTheCachedFileWhenThereIsOne() throws Exception {
		CardCache.downloadAndSaveImage(card, true, false);
		URL url = CardCache.getImageURL(card);
		assertEquals(local().toURI().toURL(), url);
	}

	public void testShowsTheRemoteAddressWhenNotCached() throws Exception {
		URL url = CardCache.getImageURL(card);
		assertEquals(source.toURI().toURL(), url);
	}

	public void testNoAddressWhenNotCachedAndTheWebIsDown() throws Exception {
		card.setProperty(MagicCardField.IMAGE_URL, "https://cards.example.invalid/image.png");
		WebUtils.setSimulateWebDown(true);
		assertNull(CardCache.getImageURL(card));
	}

	public void testRemoteAddressOfACard() throws Exception {
		assertEquals(source.toURI().toURL(), CardCache.createRemoteImageURL(card));
		card.setProperty(MagicCardField.IMAGE_URL, null);
		assertNull(CardCache.createRemoteImageURL(card));
	}

	// ---- loading offline

	public void testACachedImageLoadsAtOnce() throws Exception {
		CardCache.downloadAndSaveImage(card, true, false);
		assertTrue(CardCache.loadCardImageOffline(card, false));
	}

	public void testLoadingIsRefusedWhenTheWebIsDownAndNothingIsCached() throws Exception {
		card.setProperty(MagicCardField.IMAGE_URL, "https://cards.example.invalid/image.png");
		WebUtils.setSimulateWebDown(true);
		try {
			CardCache.loadCardImageOffline(card, false);
			fail();
		} catch (CachedImageNotFoundException e) {
			assertTrue(e.getMessage().contains("Cache Test Card"));
		}
	}
}
