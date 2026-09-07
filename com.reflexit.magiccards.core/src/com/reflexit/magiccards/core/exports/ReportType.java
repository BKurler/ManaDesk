/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.exports;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

@SuppressWarnings("rawtypes")
public class ReportType {
	private String label;
	private Properties properties;
	private boolean custom;
	Object exportWorker;
	Object importWorker;
	public static final String EXT_PROP = "ext";
	public static final String XML_PROP = "xml";

	ReportType(String label, boolean xml, String extension) {
		this.label = label;
		properties = new Properties();
		setXml(xml);
		setExtension(extension == null ? "txt" : extension);
	}

	private void setXml(boolean xml) {
		if (xml)
			properties.setProperty(XML_PROP, String.valueOf(xml));
	}

	/**
	 * Return true if given format is table format. Table format can have
	 * header.
	 */
	public boolean isXmlFormat() {
		return Boolean.valueOf(properties.getProperty(XML_PROP));
	}

	@Override
	public String toString() {
		return label;
	}

	public String getLabel() {
		return label;
	}

	public String getExtension() {
		return properties.getProperty(EXT_PROP);
	}

	public Properties getProperties() {
		return properties;
	}

	public void setProperty(String key, int value) {
		this.properties.setProperty(key, String.valueOf(value));
	}

	public void setProperty(String key, String value) {
		this.properties.setProperty(key, value);
	}

	public String getProperty(String key) {
		return properties.getProperty(key);
	}

	public void setExtension(String ext) {
		properties.setProperty(EXT_PROP, ext);
	}

	public void setCustom(boolean b) {
		this.custom = b;
	}

	public boolean isCustom() {
		return custom;
	}

	public File getFile() {
		File dir = ReportType.getStorageFile();
		String name = getLabel() + ".ini";
		return new File(dir, name);
	}

	public void save() throws IOException {
		if (!isCustom())
			throw new IOException("Cannot save non-custom type");
		FileOutputStream fs = new FileOutputStream(getFile());
		getProperties().store(fs, "export/import " + getLabel());
		try {
			fs.close();
		} catch (Exception e) {
			// ignore
		}
	}

	public void delete() throws IOException {
		if (!isCustom())
			throw new IOException("Cannot delete non-custom type");
		getFile().delete();
		ImportExportFactory.remove(getLabel());
	}

	public void load() throws IOException {
		File file = getFile();
		FileInputStream fs = new FileInputStream(file);
		getProperties().load(fs);
		fs.close();
		setCustom(true);
		setExportDelegate(new CustomExportDelegate(this));
	}

	public static ReportType load(File file) throws IOException {
		String name = file.getName().replaceAll("\\.ini$", "");
		ReportType old = ImportExportFactory.getByLabel(name);
		if (old != null && !old.isCustom())
			throw new IOException("Cannot override non-custom type");
		ReportType type = ImportExportFactory.createReportType(name);
		type.load();
		return type;
	}

	public static File getStorageFile() {
		File file = new File(FileUtils.getMagicCardsDir(), ".settings/exporters");
		file.mkdirs();
		File oldFile = new File(FileUtils.getStateLocationFile(), "exporters");
		try {
			FileUtils.migrate(file, oldFile);
		} catch (IOException e) {
			MagicLogger.log(e);
		}
		return file;
	}

	public IExportDelegate getExportDelegate() {
		Object className = exportWorker;
		if (className instanceof IExportDelegate) {
			return (IExportDelegate) className;
		}
		if (className instanceof String) {
			try {
				Class loadClass = getClass().getClassLoader().loadClass((String) className);
				IExportDelegate newInstance = (IExportDelegate) loadClass.newInstance();
				newInstance.setReportType(this);
				exportWorker = newInstance;
				return newInstance;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public IImportDelegate getImportDelegate() {
		Object className = importWorker;
		if (className instanceof IImportDelegate) {
			return (IImportDelegate) className;
		}
		if (className instanceof String) {
			try {
				Class loadClass = getClass().getClassLoader().loadClass((String) className);
				IImportDelegate newInstance = (IImportDelegate) loadClass.newInstance();
				newInstance.setReportType(this);
				importWorker = newInstance;
				return newInstance;
			} catch (Throwable e) {
				MagicLogger.log(e);
			}
		}
		return null;
	}

	public void setExportDelegate(IExportDelegate delegate) {
		exportWorker = delegate;
	}

	public void setExportDelegate(String delegate) {
		exportWorker = delegate;
	}

	public void setImportDelegate(String delegate) {
		importWorker = delegate;
	}

	public void setImportDelegate(IImportDelegate delegate) {
		importWorker = delegate;
	}

	/** How many leading lines of the source we sniff when guessing the format. */
	private static final int SNIFF_LINES = 50;

	public static ReportType autoDetectType(File file, Collection<ReportType> types) {
		String fileName = file.getPath();
		if (fileName == null || fileName.trim().length() == 0)
			return null;
		Collection<ReportType> extCandidates = new ArrayList<ReportType>();
		int k = fileName.lastIndexOf('.');
		String ext = "";
		if (k > 0 && k < fileName.length() - 1) {
			ext = fileName.substring(k + 1, fileName.length());
		}
		for (ReportType reportType : types) {
			if (ext.equalsIgnoreCase(reportType.getExtension())) {
				extCandidates.add(reportType);
			}
		}
		MagicLogger.info("[detect] file=" + fileName + " ext=" + ext + " extCandidates=" + labels(extCandidates));
		// a single unambiguous extension match is almost always right
		if (extCandidates.size() == 1) {
			MagicLogger.info("[detect] single extension match -> " + extCandidates.iterator().next().getLabel());
			return extCandidates.iterator().next();
		}
		if (file.exists()) {
			try {
				String contents = sniff(FileUtils.readFileAsString(file));
				// score every importer against the real content; a type whose
				// extension matches the file gets a small edge on a tie
				ReportType byContent = autoDetectType(contents, types, extCandidates);
				if (byContent != null)
					return byContent;
			} catch (IOException e) {
				// fall through
			}
		}
		ReportType fallback = extCandidates.isEmpty() ? null : extCandidates.iterator().next();
		MagicLogger.info("[detect] no content match -> fallback " + (fallback == null ? "(none)" : fallback.getLabel()));
		return fallback;
	}

	private static String labels(Collection<ReportType> types) {
		StringBuilder sb = new StringBuilder("[");
		for (ReportType t : types) {
			if (sb.length() > 1)
				sb.append(", ");
			sb.append(t.getLabel());
		}
		return sb.append(']').toString();
	}

	private static String sniff(String contents) {
		String[] split = contents.split("\n");
		if (split.length > SNIFF_LINES) {
			String[] ar = new String[SNIFF_LINES];
			System.arraycopy(split, 0, ar, 0, SNIFF_LINES);
			contents = String.join("\n", ar);
		}
		return contents;
	}

	public static ReportType autoDetectType(URL url, Collection<ReportType> types) {
		if (url == null || url.getPath().isEmpty())
			return null;
		Collection<ReportType> candidates = new ArrayList<ReportType>();
		for (ReportType reportType : types) {
			reportType.getImportDelegate(); // instanciate delegate // deelgate
			String regex = reportType.getProperty("url_regex");
			if (regex == null)
				continue;
			if (url.toExternalForm().matches(regex)) {
				candidates.add(reportType);
			}
		}
		if (candidates.size() > 0)
			return candidates.iterator().next();
		return null;
	}

	public static ReportType autoDetectType(String contents, Collection<ReportType> candidates) {
		return autoDetectType(contents, candidates, null);
	}

	private static ReportType autoDetectType(String contents, Collection<ReportType> candidates,
			Collection<ReportType> extensionMatch) {
		if (contents == null || contents.trim().isEmpty() || candidates == null)
			return null;
		contents = sniff(contents);
		ReportType selected = null;
		long bestScore = Long.MIN_VALUE;
		for (ReportType reportType : candidates) {
			IImportDelegate id = reportType.getImportDelegate();
			if (id == null)
				continue;
			ImportData importData = new ImportData();
			importData.setText(contents);
			id.init(importData);
			try {
				id.run(ICoreProgressMonitor.NONE);
				ImportData result = id.getResult();
				if (result.getError() != null) {
					MagicLogger.info("[detect]   " + reportType.getLabel() + " -> error: "
							+ result.getError().getMessage());
					continue;
				}
				int cards = result.getList() == null ? 0 : result.getList().size();
				if (cards == 0) {
					MagicLogger.info("[detect]   " + reportType.getLabel() + " -> 0 cards");
					continue;
				}
				// resolve against the DB so a format that "parses" but produces
				// garbage names (an id glued into the name, a whole line as a
				// name, ...) scores below one that yields cards the DB recognises
				try {
					ImportUtils.resolve(result.getList());
				} catch (RuntimeException ignore) {
					// resolution is only a quality signal here
				}
				int errs = result.getErrorCount();
				// favour "many cards, few errors": every clean card is +2, every
				// error is -3, so a format that mangles the input scores below one
				// that recognises fewer cards cleanly
				long score = 2L * (cards - errs) - 3L * errs;
				if (extensionMatch != null && extensionMatch.contains(reportType))
					score += 5;
				MagicLogger.info("[detect]   " + reportType.getLabel() + " -> cards=" + cards + " errors=" + errs
						+ " score=" + score + (extensionMatch != null && extensionMatch.contains(reportType)
								? " (+ext)" : ""));
				if (score > bestScore) {
					bestScore = score;
					selected = reportType;
				}
			} catch (InvocationTargetException | InterruptedException e) {
				MagicLogger.info("[detect]   " + reportType.getLabel() + " -> threw " + e.getCause());
			}
		}
		MagicLogger.info("[detect] winner: " + (selected == null ? "(none)" : selected.getLabel())
				+ " score=" + bestScore);
		return selected;
	}

	/**
	 * Slug for a proposed export file name, so each export type suggests its
	 * own name ({@code <deck>-<slug>.<ext>}). Uses the delegate's
	 * {@link IExportDelegate#getContentSlug()} when it defines one, otherwise a
	 * slugified label ("ManaDesk Minimum CSV" &rarr; "minimum-csv").
	 */
	public String getFileNameSlug() {
		IExportDelegate del = getExportDelegate();
		String slug = del != null ? del.getContentSlug() : null;
		if (slug != null && !slug.isEmpty())
			return slug;
		String label = getLabel();
		if (label == null)
			return "";
		label = label.replaceFirst("(?i)^manadesk\\s+", "");
		return label.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
	}

	public String getExample() {
		IExportDelegate export = getExportDelegate();
		if (export != null) {
			return export.getExample();
		}
		if (getImportDelegate() != null) {
			return getImportDelegate().getExample();
		}
		return null;
	}
}
