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

import javax.imageio.ImageIO;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.FileUtils;

/**
 * A remote file kept in a local file: when it is downloaded, when the local copy is enough, and
 * how a file is recognized as a picture. The "remote" file is a {@code file:} address and
 * everything lives in a temp folder, so nothing goes on the web. Works as plain JUnit.
 */
public class CachedFileTest extends TestCase {
	private File dir;
	private File remote;
	private File local;

	@Override
	protected void setUp() throws Exception {
		dir = Files.createTempDirectory("cachedfile-test").toFile();
		remote = new File(dir, "remote.txt");
		local = new File(dir, "cache/local.txt");
		write(remote, "version 1");
	}

	@Override
	protected void tearDown() throws Exception {
		FileUtils.deleteTree(dir);
	}

	private static void write(File f, String text) throws IOException {
		f.getParentFile().mkdirs();
		Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private static String read(File f) throws IOException {
		return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
	}

	private CachedFile cached() throws Exception {
		return new CachedFile(remote.toURI().toURL(), local);
	}

	// ---- addresses

	public void testTheTwoAddresses() throws Exception {
		CachedFile cf = cached();
		assertEquals(remote.toURI().toURL(), cf.getURL());
		assertEquals(remote.toURI().toURL(), cf.getRemoteURL());
		assertEquals(local.toURI().toURL(), cf.getLocalURL(false));
	}

	public void testLookingWithoutUploadingNeverDownloads() throws Exception {
		assertEquals(local.toURI().toURL(), cached().get(false));
		assertFalse(local.exists());
	}

	// ---- downloading

	public void testUploadDownloadsWhenTheLocalFileIsMissing() throws Exception {
		assertEquals(local.toURI().toURL(), cached().get(true));
		assertEquals("version 1", read(local));
	}

	public void testAnExistingLocalFileIsNotDownloadedAgain() throws Exception {
		CachedFile cf = cached();
		cf.get(true);
		write(remote, "version 2");
		cf.get(true);
		assertEquals("version 1", read(local));
	}

	public void testRecacheReplacesTheLocalFile() throws Exception {
		CachedFile cf = cached();
		cf.get(true);
		write(remote, "version 2");
		cf.recache();
		assertEquals("version 2", read(local));
	}

	public void testGetFileDownloadsAndGivesTheFile() throws Exception {
		assertEquals(local, cached().getFile());
		assertEquals("version 1", read(local));
	}

	public void testAMissingRemoteFileIsAnError() throws Exception {
		CachedFile cf = new CachedFile(new File(dir, "nothing.txt").toURI().toURL(), local);
		try {
			cf.get(true);
			fail();
		} catch (IOException e) {
			// expected
		}
		assertFalse(local.exists());
	}

	// ---- is it a picture

	public void testAPngIsAPicture() throws Exception {
		File png = new File(dir, "a.png");
		ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", png);
		assertTrue(new CachedFile(null, png).isImage());
	}

	public void testAGifIsAPicture() throws Exception {
		File gif = new File(dir, "a.gif");
		write(gif, "GIF89a and some more bytes");
		assertTrue(new CachedFile(null, gif).isImage());
	}

	public void testTextTooShortOrMissingIsNotAPicture() throws Exception {
		File text = new File(dir, "a.txt");
		write(text, "just some text, not a picture");
		assertFalse(new CachedFile(null, text).isImage());
		File tiny = new File(dir, "tiny");
		write(tiny, "PNG");
		assertFalse("10 bytes or less", new CachedFile(null, tiny).isImage());
		assertFalse(new CachedFile(null, new File(dir, "missing.png")).isImage());
	}
}
