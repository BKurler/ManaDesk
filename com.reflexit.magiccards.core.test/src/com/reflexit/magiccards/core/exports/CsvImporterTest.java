/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: regression test for the
 *                         "lineNum.separator" typo in CsvImporter.java - a
 *                         quoted field spanning several physical lines used to
 *                         get the literal text "null" spliced in where the line
 *                         break belonged, since System.getProperty() of a
 *                         non-existent key returns null and
 *                         StringBuffer.append((String) null) appends the text
 *                         "null" rather than throwing or being a no-op
 */
package com.reflexit.magiccards.core.exports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** {@link CsvImporter} is the low-level record reader behind the CSV import
 *  delegates (quoting / escaping / multi-line fields) - exercised directly here,
 *  without the heavier {@code AbstarctImportTest} delegate-level fixtures. */
public class CsvImporterTest {
	private List parse(String csv) throws IOException {
		CsvImporter importer = new CsvImporter(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), ',');
		try {
			return importer.readLine();
		} finally {
			importer.close();
		}
	}

	@Test
	public void testSimpleFields() throws IOException {
		assertEquals(Arrays.asList("a", "b", "c"), parse("a,b,c"));
	}

	@Test
	public void testQuotedFieldWithEmbeddedComma() throws IOException {
		assertEquals(Arrays.asList("a,b", "c"), parse("\"a,b\",c"));
	}

	@Test
	public void testEscapedQuoteInsideQuotedField() throws IOException {
		assertEquals(Arrays.asList("a\"b", "c"), parse("\"a\"\"b\",c"));
	}

	/** The regression test: a quoted field spanning two physical lines must be
	 *  rejoined with a real line break, never the literal text "null". */
	@Test
	public void testQuotedFieldSpansMultipleLines() throws IOException {
		List fields = parse("\"line1\nline2\",c");
		assertEquals(2, fields.size());
		String rejoined = (String) fields.get(0);
		assertFalse("line separator was replaced by the literal text \"null\" - see CsvImporter's lineSep bug",
				rejoined.contains("null"));
		assertTrue("expected \"line1\" and \"line2\" to still both be present",
				rejoined.contains("line1") && rejoined.contains("line2"));
		assertEquals("c", fields.get(1));
	}
}
