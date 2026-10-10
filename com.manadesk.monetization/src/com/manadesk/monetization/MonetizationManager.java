/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil
 * All rights reserved.
 *
 * This file is NOT open-source.
 * Permission is granted to use this file ONLY as part of the ManaDesk application.
 * Modification, redistribution, or reuse of this file or its contents is prohibited.
 * You may NOT replace affiliate identifiers, ad URLs, or donation links.
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: loads / caches / refreshes the
 *                  monetization configuration.
 *     Rémi Dutil (2026) - a hosted file that is not published (HTTP 404) is not
 *                  reported - the built-in configuration is used quietly;
 *                  at most one fetch per session unless forced.
 *******************************************************************************/
package com.manadesk.monetization;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com.reflexit.magiccards.core.sync.WebUtils;

/**
 * Where the monetization configuration comes from, in order:
 * <ol>
 * <li>the copy of the hosted file cached by the last successful refresh
 * (plug-in state location, {@value #CACHE_FILE});</li>
 * <li>the built-in default shipped in this plug-in
 * ({@value #BUILTIN_RESOURCE}).</li>
 * </ol>
 * {@link #refreshInBackground(boolean)} fetches the hosted file (at most once
 * per {@link #REFRESH_INTERVAL_MS}) so links / partners can change without a
 * ManaDesk release. With no web it fails silently - the cached or built-in
 * configuration keeps working.
 */
public final class MonetizationManager {
	/** Hosted configuration (GitHub Pages). */
	public static final String DEFAULT_CONFIG_URL = "https://bkurler.github.io/manadesk-config/monetization.json";
	/** System property overriding {@link #DEFAULT_CONFIG_URL} (testing). */
	public static final String CONFIG_URL_PROPERTY = "manadesk.monetization.configUrl";
	static final String CACHE_FILE = "monetization.json";
	static final String BUILTIN_RESOURCE = "resources/monetization-default.json";
	static final long REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000;

	private static final MonetizationManager INSTANCE = new MonetizationManager();

	private volatile MonetizationConfig config;
	private volatile boolean refreshScheduled;
	/** Set once a refresh was attempted this session - at most one fetch per run unless forced. */
	private volatile boolean attemptedThisSession;

	private MonetizationManager() {
	}

	public static MonetizationManager getInstance() {
		return INSTANCE;
	}

	/** The current configuration - never {@code null}, never blocks on the web. */
	public MonetizationConfig getConfig() {
		MonetizationConfig c = config;
		if (c == null) {
			c = loadLocal();
			config = c;
		}
		return c;
	}

	/**
	 * Fetch the hosted configuration in a background job, unless the cached copy
	 * is younger than {@link #REFRESH_INTERVAL_MS} ({@code force} skips that
	 * check). Silent with no web.
	 */
	public void refreshInBackground(final boolean force) {
		if (refreshScheduled || (attemptedThisSession && !force))
			return;
		final File cache = cacheFile();
		if (!force && cache != null && cache.isFile()
				&& System.currentTimeMillis() - cache.lastModified() < REFRESH_INTERVAL_MS)
			return;
		refreshScheduled = true;
		attemptedThisSession = true;
		Job job = new Job("Updating ManaDesk support information") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					refreshNow(cache);
				} finally {
					refreshScheduled = false;
				}
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		job.schedule();
	}

	private void refreshNow(File cache) {
		String url = System.getProperty(CONFIG_URL_PROPERTY, DEFAULT_CONFIG_URL);
		try {
			String json = WebUtils.openUrlText(new URL(url), 1);
			MonetizationConfig fresh = MonetizationConfig.parse(json); // reject anything unusable
			config = fresh;
			if (cache != null) {
				File part = new File(cache.getPath() + ".part");
				Files.write(part.toPath(), json.getBytes(StandardCharsets.UTF_8));
				Files.move(part.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception e) {
			// no web, or no hosted file published (yet): keep the cached / built-in config quietly
			if (!WebUtils.isWebUnavailable(e) && !isNotFound(e))
				MonetizationPlugin.warn("Could not refresh the monetization configuration from " + url, e);
		}
	}

	/** HTTP 404 - https reports it as FileNotFoundException, http as "Connection error 404". */
	private static boolean isNotFound(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause())
			if (t instanceof java.io.FileNotFoundException
					|| (t.getMessage() != null && t.getMessage().contains(" 404")))
				return true;
		return false;
	}

	private MonetizationConfig loadLocal() {
		File cache = cacheFile();
		if (cache != null && cache.isFile()) {
			try {
				return MonetizationConfig
						.parse(new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8));
			} catch (Exception e) {
				MonetizationPlugin.warn("Ignoring the cached monetization configuration " + cache, e);
			}
		}
		return loadBuiltin();
	}

	static MonetizationConfig loadBuiltin() {
		try (InputStream in = MonetizationManager.class.getClassLoader().getResourceAsStream(BUILTIN_RESOURCE)) {
			if (in == null)
				throw new IOException("missing " + BUILTIN_RESOURCE);
			return MonetizationConfig.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (Exception e) {
			MonetizationPlugin.warn("Could not read the built-in monetization configuration", e);
			return MonetizationConfig.EMPTY;
		}
	}

	private static File cacheFile() {
		MonetizationPlugin p = MonetizationPlugin.getDefault();
		if (p == null)
			return null;
		try {
			return new File(p.getStateLocation().toFile(), CACHE_FILE);
		} catch (Exception e) { // no instance location (e.g. tests)
			return null;
		}
	}
}
