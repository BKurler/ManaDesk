package com.reflexit.magiccards.core.sync;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.simple.JSONObject;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.sync.ParserHtmlHelper.ILoadCardHander;

public abstract class AbstractParseJson {
	protected URL url;

	private static long lastWebDownloadTime = 0;
	private static final long SCRYFALL_MIN_DELAY_MS = 500; // 2 req/sec

	public boolean loadSet(String set, ILoadCardHander handler, ICoreProgressMonitor monitor) throws IOException {
		try {
			monitor.beginTask("Downloading " + set + " checklist", 100);
			return loadSingleUrl(getSearchQuery(set), handler);
		} finally {
			monitor.done();
		}
	}

	protected abstract URL getSearchQuery(String set) throws MalformedURLException;

	public boolean loadSingleUrl(URL url, ILoadCardHander handler) throws IOException {
		try {
			this.url = url;

			while (true) {

				// 1- Throttle BEFORE calling WebUtils.openUrlText()
				long now = System.currentTimeMillis();
				long elapsed = now - lastWebDownloadTime;

				if (elapsed < SCRYFALL_MIN_DELAY_MS) {
					long sleep = SCRYFALL_MIN_DELAY_MS - elapsed;

					String ts = java.time.LocalTime.now()
							.format(java.time.format.DateTimeFormatter.ofPattern("mm:ss.SSS"));

					System.out.println(ts + "  === Url sleep === " + sleep + "ms ===");

					try {
						Thread.sleep(sleep);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}

				// 2- Perform the actual download
				String text = WebUtils.openUrlText(this.url);

				lastWebDownloadTime = System.currentTimeMillis();

				// 3- Parse the JSON
				String nextUrl = processFromReader(FileUtils.openBufferedReader(text), handler);

				if (nextUrl == null)
					break;

				this.url = new URL(nextUrl);
			}

			return true;

		} catch (IOException e) {
			MagicLogger.log("Loading url exception: " + url + ": " + e.getMessage());
			throw e;
		}
	}

	public int getInt(JSONObject object, String key) {
		Object value = object.get(key);
		return ((Long) value).intValue();
	};

	public Boolean getBool(JSONObject object, String key) {
		Object value = object.get(key);
		if (value.toString().contains("true")) {
			return true;
		}
		return false;
	};

	public String getString(JSONObject object, String key) {
		String name = (String) object.get(key);
		if (name != null)
			return name.trim();
		return name;
	}

	public void loadFile(File file, ILoadCardHander handler) throws IOException {
		BufferedReader st = FileUtils.openBufferedReader(file);
		processFromReader(st, handler);
		st.close();
	}

	abstract public String processFromReader(BufferedReader st, ILoadCardHander handler) throws IOException;
}
