/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - dropped the "Work Offline" pre-check -
 *                         WebUtils.openUrl() now fails fast when the web is
 *                         not accessible.
 */
package com.reflexit.magiccards.core.sync;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import com.reflexit.magiccards.core.FileUtils;

public class CachedFile {
	private URL url;
	private File file;

	public CachedFile(URL url) {
		this.url = url;
		String path = url.getHost() + "/" + url.getFile();
		this.file = new File(FileUtils.getStateLocationFile(), path);
	}

	public CachedFile(URL url, File file) {
		this.url = url;
		this.file = file;
	}

	public void recache() throws IOException {
		try (InputStream st = WebUtils.openUrl(url)) {
			FileUtils.saveStream(st, file);
		}
	}

	public File getFile() throws IOException {
		get(true);
		return file;
	}

	public URL getLocalURL(boolean upload) throws IOException {
		return get(upload);
	}

	public URL getRemoteURL() throws IOException {
		return url;
	}

	public URL get(boolean upload) throws IOException {
		URL localUrl = file.toURI().toURL();
		if (upload == false)
			return localUrl;
		if (file.exists()) {
			return localUrl;
		}
		try (InputStream st = WebUtils.openUrl(url)) {
			FileUtils.saveStream(st, file);
		}
		return localUrl;
	}

	public URL getURL() {
		return url;
	}

	public boolean isImage() {
		if (file.exists() && file.length() > 10) {
			byte header[] = new byte[10];
			try {
				FileUtils.readFileAsBytes(file, header);
				String str = new String(header, FileUtils.CHARSET_UTF_8);
				if (str.substring(1, 4).equals("PNG"))
					return true;
				if (str.substring(0, 3).equals("GIF"))
					return true;
			} catch (IOException e) {
				// ignore
			}
		}
		return false;
	}
}
