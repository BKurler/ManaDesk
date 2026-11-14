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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.monitor.ProgressMonitorWrapper;

/**
 * The local copy of Scryfall's bulk card file: when a download is needed, when the copy is
 * reused, the user import, a cancelled download, and the index being unreachable. The bulk-data
 * index and the "download" are {@code file:} addresses and the files live in a temp folder
 * (see {@code ScryfallBulkCache.useForTests}), so nothing goes on the web and the real ManaDesk
 * folder is not touched. Works as plain JUnit.
 */
public class ScryfallBulkCacheTest extends TestCase {
	private File dir;
	private File remote;
	private File index;

	@Override
	protected void setUp() throws Exception {
		dir = Files.createTempDirectory("bulkcache-test").toFile();
		remote = new File(dir, "remote.jsonl.gz");
		index = new File(dir, "bulk-data.json");
		write(remote, "cards v1");
		publish("2026-01-01T00:00:00.000+00:00", "jsonl_download_uri");
	}

	@Override
	protected void tearDown() throws Exception {
		ScryfallBulkCache.useForTests(null, null);
		FileUtils.deleteTree(dir);
	}

	private static void write(File f, String text) throws IOException {
		Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private static String read(File f) throws IOException {
		return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
	}

	/** The bulk-data index as Scryfall publishes it, pointing at the "remote" file; the cache forgets the old index. */
	private void publish(String updatedAt, String uriKey) throws Exception {
		String uri = remote.toURI().toURL().toExternalForm();
		write(index, "{\"data\":[{\"type\":\"oracle_cards\",\"updated_at\":\"1999-01-01T00:00:00.000+00:00\",\"" + uriKey
				+ "\":\"" + uri + "x\"},{\"type\":\"default_cards\",\"updated_at\":\"" + updatedAt + "\",\"" + uriKey
				+ "\":\"" + uri + "\",\"size\":5242880}]}");
		use();
	}

	private void use() throws Exception {
		ScryfallBulkCache.useForTests(index.toURI().toURL().toExternalForm(), new File(dir, "state"));
	}

	private File saved() {
		return new File(new File(dir, "state"), "default-cards.jsonl.gz");
	}

	// ---- the index

	public void testTheIndexTellsTheSizeAndThatTheServiceIsThere() {
		assertTrue(ScryfallBulkCache.isReachable());
		assertEquals(5, ScryfallBulkCache.remoteBulkSizeMB());
	}

	public void testTheDefaultCardsEntryIsTheOneUsedNotTheOthers() throws Exception {
		assertEquals("cards v1", read(ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE)));
	}

	public void testTheOlderDownloadAddressIsUsedWhenThereIsNoJsonLinesOne() throws Exception {
		publish("2026-01-01T00:00:00.000+00:00", "download_uri");
		assertEquals("cards v1", read(ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE)));
	}

	public void testAnIndexWithoutTheDefaultCardsIsAnUnreachableService() throws Exception {
		write(index, "{\"data\":[{\"type\":\"oracle_cards\",\"updated_at\":\"x\"}]}");
		use();
		assertFalse(ScryfallBulkCache.isReachable());
		assertFalse(ScryfallBulkCache.isRemoteBulkNewer());
		assertEquals(-1, ScryfallBulkCache.remoteBulkSizeMB());
	}

	public void testAnIndexThatIsNotJsonIsAnUnreachableService() throws Exception {
		write(index, "<html>maintenance</html>");
		use();
		assertFalse(ScryfallBulkCache.isReachable());
		assertEquals(-1, ScryfallBulkCache.remoteBulkSizeMB());
	}

	public void testNoSizeInTheIndexIsNotASize() throws Exception {
		String uri = remote.toURI().toURL().toExternalForm();
		write(index, "{\"data\":[{\"type\":\"default_cards\",\"updated_at\":\"x\",\"jsonl_download_uri\":\"" + uri + "\"}]}");
		use();
		assertEquals(-1, ScryfallBulkCache.remoteBulkSizeMB());
		assertTrue("still downloadable", ScryfallBulkCache.isReachable());
	}

	// ---- download and reuse

	public void testNothingLocalMeansANewerRemote() {
		assertFalse(ScryfallBulkCache.hasLocalBulk());
		assertTrue(ScryfallBulkCache.isRemoteBulkNewer());
	}

	public void testTheFirstCallDownloadsAndMarksTheCopyCurrent() throws Exception {
		File file = ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE);
		assertEquals(saved(), file);
		assertEquals("cards v1", read(file));
		assertTrue(ScryfallBulkCache.hasLocalBulk());
		assertFalse("the copy is the published one", ScryfallBulkCache.isRemoteBulkNewer());
		assertEquals("no part file left", 2, saved().getParentFile().list().length); // the file and its marker
	}

	public void testACurrentCopyIsNotDownloadedAgain() throws Exception {
		ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE);
		write(remote, "cards v2");
		use(); // a fresh look at the index, which still says the same date
		assertEquals("cards v1", read(ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE)));
	}

	public void testANewerPublicationIsDownloaded() throws Exception {
		ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE);
		write(remote, "cards v2");
		publish("2026-02-02T00:00:00.000+00:00", "jsonl_download_uri");
		assertTrue(ScryfallBulkCache.isRemoteBulkNewer());
		assertEquals("cards v2", read(ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE)));
		assertFalse(ScryfallBulkCache.isRemoteBulkNewer());
	}

	public void testTheLocalCopyIsKeptWhenTheServiceCannotBeReached() throws Exception {
		ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE);
		index.delete();
		use();
		assertFalse(ScryfallBulkCache.isRemoteBulkNewer());
		assertEquals("cards v1", read(ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE)));
	}

	public void testNoCopyAndNoServiceIsAnError() throws Exception {
		index.delete();
		use();
		try {
			ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE);
			fail();
		} catch (IOException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("no local copy"));
		}
		assertFalse(ScryfallBulkCache.hasLocalBulk());
	}

	// ---- a cancelled download

	public void testACancelledDownloadLeavesNothingBehind() throws Exception {
		ICoreProgressMonitor cancelled = new ProgressMonitorWrapper(ICoreProgressMonitor.NONE) {
			@Override
			public boolean isCanceled() {
				return true;
			}
		};
		try {
			ScryfallBulkCache.getDefaultCardsFile(cancelled);
			fail();
		} catch (IOException e) {
			assertEquals("Cancelled", e.getMessage());
		}
		assertFalse(ScryfallBulkCache.hasLocalBulk());
		assertEquals("no part file left", 0, saved().getParentFile().list().length);
	}

	public void testLeftoverPartialDownloadsAreSweptBeforeANewOne() throws Exception {
		File stale = new File(new File(dir, "state"), "default-cards-old.part");
		stale.getParentFile().mkdirs();
		write(stale, "half a file");
		ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE);
		assertFalse(stale.exists());
	}

	// ---- the file imported by the user

	public void testAnImportedFileIsUsedAsIsOnceThenTheNormalChecksResume() throws Exception {
		File mine = new File(dir, "mine.jsonl");
		write(mine, "my cards");
		ScryfallBulkCache.installLocalBulk(mine);
		assertTrue(ScryfallBulkCache.hasLocalBulk());
		assertTrue(ScryfallBulkCache.isLocalImportPending());
		assertEquals("my cards", read(ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE)));
		assertFalse("only the next update uses it as is", ScryfallBulkCache.isLocalImportPending());
		// the import was stamped with the date of the index read at that time, so it counts as current
		assertEquals("my cards", read(ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE)));
	}

	public void testAnImportedFileIsReplacedWhenTheRemoteMovedOn() throws Exception {
		File mine = new File(dir, "mine.jsonl");
		write(mine, "my cards");
		ScryfallBulkCache.installLocalBulk(mine);
		ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE);
		write(remote, "cards v3");
		publish("2026-03-03T00:00:00.000+00:00", "jsonl_download_uri");
		assertEquals("cards v3", read(ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE)));
	}

	public void testAnImportWithoutTheWebIsStillUsedAndDoesNotNeedTheIndex() throws Exception {
		index.delete();
		use();
		File mine = new File(dir, "mine.jsonl");
		write(mine, "my cards");
		ScryfallBulkCache.installLocalBulk(mine);
		assertTrue(ScryfallBulkCache.isLocalImportPending());
		assertEquals("my cards", read(ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE)));
		assertEquals("and again later, the web being still down", "my cards",
				read(ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE)));
	}

	public void testNothingToImportIsRefused() throws Exception {
		File empty = new File(dir, "empty.jsonl");
		write(empty, "");
		for (File bad : new File[] { null, new File(dir, "missing.jsonl"), empty, dir }) {
			try {
				ScryfallBulkCache.installLocalBulk(bad);
				fail("" + bad);
			} catch (IOException e) {
				assertTrue(e.getMessage(), e.getMessage().startsWith("Not a usable"));
			}
		}
		assertFalse(ScryfallBulkCache.hasLocalBulk());
		assertFalse(ScryfallBulkCache.isLocalImportPending());
	}
}
