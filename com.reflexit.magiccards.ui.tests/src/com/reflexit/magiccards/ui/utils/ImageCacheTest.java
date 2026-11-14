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
package com.reflexit.magiccards.ui.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.junit.Test;

import com.reflexit.magiccards.core.model.CardGroup;

/**
 * The card picture cache: what is kept and found again, what is asked for that is not a card, the
 * limit on pictures loading at once, and the oldest pictures thrown away (and freed) when the
 * cache is full. JUnit Plug-in Test (pictures need a display).
 */
public class ImageCacheTest {
	private final ImageCache cache = ImageCache.INSTANCE;

	private static Image image() {
		return new Image(Display.getDefault(), 1, 1);
	}

	@Test
	public void testAPictureIsKeptAndFoundAgain() {
		Image img = image();
		cache.setCachedImage("imagecache-test-kept", img);
		assertSame(img, cache.getCachedImage("imagecache-test-kept"));
		assertSame(img, cache.getImage("imagecache-test-kept", null));
	}

	@Test
	public void testAnUnknownKeyHasNoPicture() {
		assertNull(cache.getCachedImage("imagecache-test-unknown"));
	}

	@Test
	public void testACachedPictureDoesNotCallBack() {
		Image img = image();
		cache.setCachedImage("imagecache-test-nocallback", img);
		final AtomicInteger calls = new AtomicInteger();
		assertSame(img, cache.getImage("imagecache-test-nocallback", new Runnable() {
			@Override
			public void run() {
				calls.incrementAndGet();
			}
		}));
		assertEquals(0, calls.get());
	}

	@Test
	public void testNothingIsLoadedForAGroupOrSomethingThatIsNotACard() {
		assertNull(cache.getImage(new CardGroup(null, "group"), null));
		assertNull(cache.getImage("text", null));
		assertNull(cache.getImage(null, null));
	}

	@Test
	public void testOnlySomeLoadsRunAtOnce() {
		int allowed = 0;
		while (cache.in() && allowed < 1000)
			allowed++;
		try {
			assertTrue("a limit exists", allowed < 1000);
			assertTrue(allowed > 0);
			assertFalse("still full", cache.in());
		} finally {
			for (int i = 0; i < allowed; i++)
				cache.out();
		}
		assertTrue("free again once they are done", cache.in());
		cache.out();
	}

	@Test
	public void testTheOldestPicturesAreFreedWhenTheCacheIsFull() {
		List<Image> images = new ArrayList<>();
		try {
			for (int i = 0; i < 700; i++) {
				Image img = image();
				images.add(img);
				cache.setCachedImage("imagecache-test-fill-" + i, img);
			}
			assertTrue("the oldest one was freed", images.get(0).isDisposed());
			assertNull(cache.getCachedImage("imagecache-test-fill-0"));
			assertFalse("the newest is kept", images.get(699).isDisposed());
			assertSame(images.get(699), cache.getCachedImage("imagecache-test-fill-699"));
		} finally {
			for (Image img : images)
				if (!img.isDisposed())
					img.dispose();
		}
	}
}
