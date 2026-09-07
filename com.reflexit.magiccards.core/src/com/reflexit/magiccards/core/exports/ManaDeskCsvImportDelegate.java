/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */
package com.reflexit.magiccards.core.exports;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.exports.DeckBoxExportDelegate.ExtraFields;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;

/**
 * The ManaDesk CSV importer. It is deliberately <b>strict</b>: every header must
 * be a column name ManaDesk itself writes.
 * <ul>
 * <li>The deck / collection columns are imported:
 * {@code NAME, ID, GATHERERID, SET, EDITION_ABBR, COLLNUM, LANG, COUNT,
 * OWNERSHIP, SPECIAL, COMMENT, PRICE}.</li>
 * <li>Card-database columns a "Full CSV" also carries ({@code COST, TYPE, POWER,
 * TOUGHNESS, ORACLE, TEXT, RARITY, COLOR, COLOR_IDENTITY, CTYPE, ARTIST,
 * DBPRICE, ...} - any real {@link MagicCardField}) are recognised and their
 * values ignored.</li>
 * <li>Anything else ("Card Name", "Qty", "Multiverse ID", lower-case "name",
 * ...) means the file was produced by something else - the importer rejects it
 * so a different importer gets a go.</li>
 * </ul>
 * Headers must match ManaDesk's spelling <b>exactly</b> (case-sensitive). The
 * one column that has a choice is the set: its header is {@code SET/EDITION_ABBR}
 * (or plain {@code SET} / {@code EDITION_ABBR}) and the value may be either a
 * full set name or its abbreviation. Order-free; empty trailing columns are ok.
 */
public class ManaDeskCsvImportDelegate extends CsvImportDelegate {

	/** the exact headers a ManaDesk CSV may carry, case-sensitive */
	protected final Map<String, ICardField> headers = new LinkedHashMap<>();

	public ManaDeskCsvImportDelegate() {
		// exactly the CAPS field ids ManaDesk writes; SET and EDITION_ABBR share
		// one header, "SET/EDITION_ABBR" (the value may be a name or an abbr)
		headers.put("NAME", MagicCardField.NAME);
		headers.put("ID", MagicCardField.ID);
		headers.put("GATHERERID", MagicCardField.GATHERERID);
		headers.put("SET/EDITION_ABBR", MagicCardField.EDITION_ABBR);
		headers.put("COLLNUM", MagicCardField.COLLNUM);
		headers.put("LANG", MagicCardField.LANG);
		headers.put("COUNT", MagicCardField.COUNT);
		headers.put("OWNERSHIP", MagicCardField.OWNERSHIP);
		headers.put("SPECIAL", MagicCardField.SPECIAL);
		headers.put("COMMENT", MagicCardField.COMMENT);
		headers.put("PRICE", MagicCardField.PRICE);
	}

	private static final ICardField[] EXAMPLE_FIELDS = { MagicCardField.NAME, MagicCardField.SET,
			MagicCardField.COLLNUM, MagicCardField.GATHERERID, MagicCardField.ID, MagicCardField.LANG,
			MagicCardField.COUNT, MagicCardField.OWNERSHIP, MagicCardField.SPECIAL, MagicCardField.COMMENT };

	@Override
	protected ICardField[] getExampleFields() {
		return EXAMPLE_FIELDS;
	}

	@Override
	protected void setHeaderFields(List<String> list) {
		ICardField[] fields = new ICardField[list.size()];
		int i = 0;
		boolean hasIdentity = false;
		for (String raw : list) {
			String key = raw == null ? "" : raw.trim();
			if (key.isEmpty()) {
				fields[i++] = null; // tolerate an empty (e.g. trailing) column
				continue;
			}
			ICardField f = headers.get(key);
			if (f == null && (key.equals("SET") || key.equals("EDITION_ABBR")))
				f = MagicCardField.EDITION_ABBR; // separate SET / EDITION_ABBR columns (e.g. Full CSV) also ok
			if (f != null) {
				fields[i++] = f;
				hasIdentity |= f == MagicCardField.NAME || f == MagicCardField.ID || f == MagicCardField.GATHERERID;
				continue;
			}
			if (isDatabaseColumn(key)) {
				fields[i++] = null; // a card-database column ManaDesk also exports - ignore its value
				continue;
			}
			throw new MagicException("Not a " + label() + ": unexpected column header '" + key
					+ "'. ManaDesk writes " + headers.keySet() + " (plus card-database columns).");
		}
		if (!hasIdentity)
			throw new MagicException(
					"Not a " + label() + ": the header row has no NAME / ID / GATHERERID column (" + list + ")");
		setFields(fields);
	}

	/** true when {@code key} is exactly a {@link MagicCardField} name (COST, RARITY, COLOR_IDENTITY, ...) */
	private static boolean isDatabaseColumn(String key) {
		try {
			MagicCardField.valueOf(key);
			return true;
		} catch (IllegalArgumentException notAField) {
			return false;
		}
	}

	/** what to call this format in error messages */
	protected String label() {
		return "ManaDesk CSV";
	}

	@Override
	public void setFieldValue(MagicCardPhysical card, ICardField field, int i, String value) {
		if (field instanceof ExtraFields) {
			((ExtraFields) field).importInto(card, value);
			return;
		}
		if (field == null)
			return;
		super.setFieldValue(card, field, i, value);
	}
}
