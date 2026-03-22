/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.sync;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import com.reflexit.magiccards.core.CachedImageNotFoundException;
import com.reflexit.magiccards.core.CannotDetermineSetAbbriviation;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;

public class CardCache {

	/* ============================================================
	 *  NEW: Path provider injected by UI
	 * ============================================================ */
	private static ICardImagePathProvider pathProvider;

	public static void setPathProvider(ICardImagePathProvider provider) {
		pathProvider = provider;
	}

	private static File requireLocalPath(IMagicCard card) {
		if (pathProvider == null) {
			throw new IllegalStateException("ICardImagePathProvider not set");
		}
		File f = pathProvider.getLocalImagePath(card);
		if (f == null) {
			throw new IllegalStateException("ICardImagePathProvider returned null for " + card);
		}
		return f;
	}

	/* ============================================================
	 *  Existing code (unchanged)
	 * ============================================================ */

	public static URL createSetImageURL(IMagicCard card, boolean upload) throws IOException {
		String edition = card.getSet();
		String rarity = card.getRarity();
		return createSetImageURL(edition, rarity, upload);
	}

	private static URL createSetImageURL(String editionName, String rarity, boolean upload)
			throws MalformedURLException, IOException {
		Edition edition = Editions.getInstance().getEditionByName(editionName);
		if (edition == null)
			return null;
		return edition.getImageFiles().getLocalURL(rarity, upload);
	}

	public static URL createRemoteImageURL(IMagicCard card) throws MalformedURLException {
		String strUrl = (String) card.get(MagicCardField.IMAGE_URL);
		if (strUrl == null)
			return null;
		return new URL(strUrl);
	}

	private static boolean isLoadingEnabled() {
		return !WebUtils.isWorkOffline();
	}

	private static ArrayList<IMagicCard> cardImageQueue = new ArrayList<>();
	private static Thread cardImageLoadingJob = null;

	static synchronized void initCardImageLoading() {
		if (cardImageLoadingJob != null)
			return;
		cardImageLoadingJob = new Thread("Loading card images") {
			@Override
			public void run() {
				while (true) {
					IMagicCard card = null;
					synchronized (cardImageQueue) {
						if (!cardImageQueue.isEmpty()) {
							card = cardImageQueue.remove(0);
							cardImageQueue.notifyAll();
						} else {
							try {
								cardImageQueue.wait(10000);
							} catch (InterruptedException e) {
								break;
							}
							if (cardImageQueue.isEmpty())
								break;
							continue;
						}
					}
					if (card == null)
						continue;
					synchronized (card) {
						try {
							downloadAndSaveImage(card, isLoadingEnabled(), true);
						} catch (Exception e) {
							// ignore
						} finally {
							card.notifyAll();
						}
					}
				}
				synchronized (CardCache.class) {
					cardImageLoadingJob = null;
				}
			}
		};
		cardImageLoadingJob.start();
	}

	private static void queueImageLoading(IMagicCard card) {
		initCardImageLoading();
		synchronized (cardImageQueue) {
			if (!cardImageQueue.contains(card)) {
				cardImageQueue.add(card);
				cardImageQueue.notifyAll();
			} else {
				card.notifyAll();
			}
		}
	}

	/* ============================================================
	 *  UPDATED: downloadAndSaveImage now uses pathProvider
	 * ============================================================ */
	public static File downloadAndSaveImage(IMagicCard card, boolean remote, boolean forceRemote) throws IOException {

		synchronized (card) {

			File file = requireLocalPath(card);

			if (!forceRemote && file.exists()) {
				return file;
			}

			if (!remote) {
				throw new CachedImageNotFoundException("Cannot find cached image for " + card.getName());
			}

			URL url = createRemoteImageURL(card);
			if (url == null) {
				throw new CachedImageNotFoundException("Cannot find image for " + card.getName() + " (no URL)");
			}

			return saveCachedFile(file, url);
		}
	}

	/* ============================================================
	 *  Existing save logic (unchanged)
	 * ============================================================ */
	public static File saveCachedFile(File file, URL url) throws IOException {
		File dir = file.getParentFile();
		dir.mkdirs();
		File file2 = File.createTempFile(file.getName(), ".part", dir);
		try {
			InputStream st = null;
			try {
				st = WebUtils.openUrl(url);
			} catch (IOException e) {
				throw new IOException("Cannot connect: " + e.getMessage());
			}
			try {
				FileUtils.saveStream(st, file2);
			} catch (IOException e) {
				throw new IOException("Cannot save tmp file: " + file2 + ": " + e.getMessage());
			} finally {
				st.close();
			}
			if (file2.exists() && file2.length() > 0) {
				if (file.exists() && !file.delete())
					throw new IOException("failed to delete " + file);
				if (!file2.renameTo(file))
					throw new IOException("failed to rename into " + file);
				return file;
			} else {
				throw new IOException("Cannot save file: " + file);
			}
		} finally {
			file2.delete();
		}
	}

	/* ============================================================
	 *  UPDATED: getImageURL now uses provider indirectly
	 * ============================================================ */
	public static URL getImageURL(IMagicCard card) throws MalformedURLException {
		File file = requireLocalPath(card);
		if (file.exists()) {
			return file.toURI().toURL();
		}
		return createRemoteImageURL(card);
	}

	/* ============================================================
	 *  UPDATED: loadCardImageOffline uses provider
	 * ============================================================ */
	public static boolean loadCardImageOffline(IMagicCard card, boolean forceUpdate)
			throws IOException, CannotDetermineSetAbbriviation {

		File file = requireLocalPath(card);

		if (file.exists() && !forceUpdate) {
			return true;
		}

		if (!isLoadingEnabled()) {
			throw new CachedImageNotFoundException("Cannot find cached image for " + card.getName());
		}

		queueImageLoading(card);
		return false;
	}

	public static boolean isImageCached(IMagicCard card) {
		File file = requireLocalPath(card);
		return file.exists();
	}
}
