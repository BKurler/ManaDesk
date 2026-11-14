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
package com.reflexit.magiccards.core.exports;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * {@link ReportType}: properties, slug, custom-type guards and the import format
 * auto-detection (by extension, by URL, by scoring the content). Pure logic; works
 * as plain JUnit. Uses private (unregistered) report types so the global factory is
 * left untouched.
 */
public class ReportTypeTest extends TestCase {
	private final List<File> tmp = new ArrayList<File>();

	@Override
	protected void tearDown() throws Exception {
		for (File f : tmp)
			f.delete();
		tmp.clear();
	}

	private File tempFile(String suffix, String text) throws IOException {
		File f = File.createTempFile("reporttype-test", suffix);
		tmp.add(f);
		if (text != null) {
			FileWriter w = new FileWriter(f);
			w.write(text);
			w.close();
		}
		return f;
	}

	private static ReportType type(String label, String ext) {
		return new ReportType(label, false, ext);
	}

	/** Importer that "recognises" a fixed number of cards, some of them in error. */
	private static class FakeImport implements IImportDelegate {
		private final int cards;
		private final int errors;
		private final boolean fail;
		private ReportType type;
		private ImportData data;

		FakeImport(int cards, int errors, boolean fail) {
			this.cards = cards;
			this.errors = errors;
			this.fail = fail;
		}

		@Override
		public ReportType getType() {
			return type;
		}

		@Override
		public void init(ImportData result) {
			data = result;
		}

		@Override
		public void run(ICoreProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
			if (fail)
				throw new InvocationTargetException(new RuntimeException("not my format"));
			for (int i = 0; i < cards; i++) {
				MagicCardPhysical c = new MagicCardPhysical(new MagicCard(), Location.NO_WHERE);
				if (i < errors)
					c.setError("bad card");
				data.add(c);
			}
		}

		@Override
		public ImportData getResult() {
			return data;
		}

		@Override
		public void setFieldValue(MagicCardPhysical card, ICardField field, int i, String value) {
			// not needed
		}

		@Override
		public void setReportType(ReportType reportType) {
			type = reportType;
		}

		@Override
		public String getExample() {
			return "fake import example";
		}
	}

	private static class FakeExport implements IExportDelegate<Object> {
		private final String slug;

		FakeExport(String slug) {
			this.slug = slug;
		}

		@Override
		public ReportType getType() {
			return null;
		}

		@Override
		public void init(OutputStream st, boolean header, IFilteredCardStore<Object> cards) {
		}

		@Override
		public void run(ICoreProgressMonitor monitor) {
		}

		@Override
		public void setColumns(ICardField[] columnsForExport) {
		}

		@Override
		public void setReportType(ReportType reportType) {
		}

		@Override
		public boolean isSideboardSupported() {
			return false;
		}

		@Override
		public boolean isColumnChoiceSupported() {
			return false;
		}

		@Override
		public boolean isMultipleLocationSupported() {
			return false;
		}

		@Override
		public String getExample() {
			return "fake export example";
		}

		@Override
		public String getContentSlug() {
			return slug;
		}
	}

	private static ReportType importer(String label, String ext, int cards, int errors, boolean fail) {
		ReportType t = type(label, ext);
		t.setImportDelegate(new FakeImport(cards, errors, fail));
		t.getImportDelegate().setReportType(t);
		return t;
	}

	// ---- basics

	public void testDefaults() {
		ReportType t = type("Plain", null);
		assertEquals("Plain", t.getLabel());
		assertEquals("Plain", t.toString());
		assertEquals("txt", t.getExtension());
		assertFalse(t.isXmlFormat());
		assertFalse(t.isCustom());
		assertFalse(t.isApplication());
	}

	public void testXmlFormatAndProperties() {
		ReportType t = new ReportType("Xml", true, "xml");
		assertTrue(t.isXmlFormat());
		assertEquals("xml", t.getExtension());
		t.setExtension("abc");
		assertEquals("abc", t.getProperty(ReportType.EXT_PROP));
		t.setProperty("n", 42);
		assertEquals("42", t.getProperty("n"));
		t.setProperty("k", "v");
		assertEquals("v", t.getProperties().getProperty("k"));
		assertNull(t.getProperty("missing"));
	}

	public void testFlags() {
		ReportType t = type("Flags", "txt");
		t.setCustom(true);
		t.setApplication(true);
		assertTrue(t.isCustom());
		assertTrue(t.isApplication());
	}

	public void testNonCustomCannotBeSavedOrDeleted() {
		ReportType t = type("Builtin", "txt");
		try {
			t.save();
			fail("save of a non-custom type must fail");
		} catch (IOException e) {
			assertTrue(e.getMessage().contains("non-custom"));
		}
		try {
			t.delete();
			fail("delete of a non-custom type must fail");
		} catch (IOException e) {
			assertTrue(e.getMessage().contains("non-custom"));
		}
	}

	// ---- delegates / example / slug

	public void testDelegatesAbsentByDefault() {
		ReportType t = type("None", "txt");
		assertNull(t.getExportDelegate());
		assertNull(t.getImportDelegate());
		assertNull(t.getExample());
	}

	public void testUnknownDelegateClassNameGivesNull() {
		ReportType t = type("Broken", "txt");
		t.setImportDelegate("no.such.Clazz");
		t.setExportDelegate("no.such.Clazz");
		assertNull(t.getImportDelegate());
		assertNull(t.getExportDelegate());
	}

	public void testExampleComesFromExportThenImport() {
		ReportType t = type("Ex", "txt");
		t.setImportDelegate(new FakeImport(0, 0, false));
		assertEquals("fake import example", t.getExample());
		t.setExportDelegate(new FakeExport(""));
		assertEquals("fake export example", t.getExample());
	}

	public void testSlugFromLabel() {
		assertEquals("minimum-csv", type("ManaDesk Minimum CSV", "csv").getFileNameSlug());
		assertEquals("print-proxies-html", type("Print Proxies HTML", "html").getFileNameSlug());
		assertEquals("a-b-c", type("  A / B & C!! ", "txt").getFileNameSlug());
	}

	public void testSlugFromDelegateWins() {
		ReportType t = type("Whatever Label", "txt");
		t.setExportDelegate(new FakeExport("my-slug"));
		assertEquals("my-slug", t.getFileNameSlug());
	}

	public void testEmptyDelegateSlugFallsBackToLabel() {
		ReportType t = type("Some Label", "txt");
		t.setExportDelegate(new FakeExport(""));
		assertEquals("some-label", t.getFileNameSlug());
	}

	// ---- autoDetectType(File)

	public void testDetectByFileNameBlank() {
		assertNull(ReportType.autoDetectType(new File(""), new ArrayList<ReportType>()));
	}

	public void testDetectSingleExtensionMatch() {
		ReportType csv = type("Csv", "csv");
		ReportType xml = type("Xml", "xml");
		Collection<ReportType> types = Arrays.asList(csv, xml);
		// the file need not exist: a unique extension decides
		File f = new File("some-folder", "deck.CSV");
		assertSame(csv, ReportType.autoDetectType(f, types));
	}

	public void testDetectNoExtensionNoMatch() {
		Collection<ReportType> types = Arrays.asList(type("Csv", "csv"));
		assertNull(ReportType.autoDetectType(new File("noextension"), types));
		// a trailing dot is not an extension
		assertNull(ReportType.autoDetectType(new File("deck."), types));
	}

	public void testDetectAmbiguousExtensionMissingFileFallsBackToFirst() {
		ReportType a = type("A", "txt");
		ReportType b = type("B", "txt");
		File f = new File("nowhere", "missing-file.txt");
		assertSame(a, ReportType.autoDetectType(f, Arrays.asList(a, b)));
	}

	public void testDetectAmbiguousExtensionUsesContent() throws IOException {
		// two importers share the extension: the first one cannot read the content, so
		// the choice must come from the content, not from the "first by extension" fallback
		ReportType cannotRead = importer("CannotRead", "txt", 5, 0, true);
		ReportType reads = importer("Reads", "txt", 1, 0, false);
		File f = tempFile(".txt", "4 Island\n");
		assertSame(reads, ReportType.autoDetectType(f, Arrays.asList(cannotRead, reads)));
	}

	public void testDetectEmptyContentFallsBackToFirstByExtension() throws IOException {
		ReportType a = type("A", "txt");
		ReportType b = type("B", "txt");
		File f = tempFile(".txt", "   \n");
		assertSame(a, ReportType.autoDetectType(f, Arrays.asList(a, b)));
	}

	// ---- autoDetectType(String): content scoring

	public void testDetectContentNullEmptyOrNoCandidates() {
		Collection<ReportType> types = Arrays.asList(importer("X", "txt", 3, 0, false));
		assertNull(ReportType.autoDetectType((String) null, types));
		assertNull(ReportType.autoDetectType("   ", types));
		assertNull(ReportType.autoDetectType("4 Island", null));
		assertNull(ReportType.autoDetectType("4 Island", new ArrayList<ReportType>()));
	}

	public void testDetectContentErrorsAreHeavilyPenalised() {
		// 10 cards but 6 of them in error scores below 3 clean cards. (Resolving the fake,
		// nameless cards against the DB may flag more of them as errors; the mangled
		// importer loses either way, which is what this guards.)
		ReportType mangled = importer("Mangled", "txt", 10, 6, false);
		ReportType clean = importer("Clean", "txt", 3, 0, false);
		assertSame(clean, ReportType.autoDetectType("text", Arrays.asList(mangled, clean)));
	}

	public void testDetectContentIgnoresFailingAndEmptyImporters() {
		ReportType failing = importer("Failing", "txt", 5, 0, true);
		ReportType none = importer("None", "txt", 0, 0, false);
		ReportType ok = importer("Ok", "txt", 1, 0, false);
		assertSame(ok, ReportType.autoDetectType("text", Arrays.asList(failing, none, ok)));
		assertNull(ReportType.autoDetectType("text", Arrays.asList(failing, none)));
	}

	public void testDetectContentSkipsTypesWithoutImporter() {
		ReportType exportOnly = type("ExportOnly", "txt");
		ReportType ok = importer("Ok", "txt", 1, 0, false);
		assertSame(ok, ReportType.autoDetectType("text", Arrays.asList(exportOnly, ok)));
	}

	// ---- autoDetectType(URL)

	public void testDetectByUrl() throws Exception {
		ReportType site = type("Site", "txt");
		site.setProperty("url_regex", "https://example\\.com/deck/.*");
		ReportType other = type("Other", "txt");
		other.setProperty("url_regex", "https://other\\.org/.*");
		ReportType noRegex = type("NoRegex", "txt");
		Collection<ReportType> types = Arrays.asList(noRegex, other, site);
		assertSame(site, ReportType.autoDetectType(URI.create("https://example.com/deck/123").toURL(), types));
		assertSame(other, ReportType.autoDetectType(URI.create("https://other.org/x").toURL(), types));
		assertNull(ReportType.autoDetectType(URI.create("https://nothing.net/x").toURL(), types));
	}

	public void testDetectByUrlNullOrEmptyPath() throws Exception {
		Collection<ReportType> types = Arrays.asList(type("T", "txt"));
		assertNull(ReportType.autoDetectType((URL) null, types));
		assertNull(ReportType.autoDetectType(URI.create("https://example.com").toURL(), types));
	}
}
