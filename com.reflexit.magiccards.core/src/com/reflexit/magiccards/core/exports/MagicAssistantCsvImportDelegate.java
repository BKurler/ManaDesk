/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.exports;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.exports.DeckBoxExportDelegate.ExtraFields;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;

/**
 * Import of a Magic Assistant / MTG Family collection CSV.
 * <p>
 * Magic Assistant writes human-readable, mixed-case column names ("Count",
 * "Card Number", "Language", "Edition", "id" - lower-case) - never ManaDesk's
 * rigid all-caps field ids. The distinctive column is <b>id</b>, a Scryfall UUID
 * (optionally {@code scry_}-prefixed), the same value as ManaDesk's "Card Id"
 * ({@link MagicCardField#ID}); files are often just that one column.
 * <p>
 * To stay clearly separate from {@link ManaDeskCsvImportDelegate}, a header row
 * that is entirely ManaDesk-shaped (every column an exact upper-case
 * {@link MagicCardField} name, or {@code SET/EDITION_ABBR}) is rejected here so
 * the ManaDesk importer handles it.
 */
public class MagicAssistantCsvImportDelegate extends CsvImportDelegate {
	private static final String SCRYFALL_UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

	/** Magic Assistant column names -> field, keyed by {@link #normalize} */
	private final Map<String, ICardField> maHeaders = new LinkedHashMap<>();

	public MagicAssistantCsvImportDelegate() {
		put("name", MagicCardField.NAME);
		put("count", MagicCardField.COUNT);
		put("qty", MagicCardField.COUNT);
		put("quantity", MagicCardField.COUNT);
		put("set", MagicCardField.SET);
		put("edition", MagicCardField.SET);
		put("editionname", MagicCardField.SET);
		put("setname", MagicCardField.SET);
		put("setcode", MagicCardField.EDITION_ABBR);
		put("editionabbr", MagicCardField.EDITION_ABBR);
		put("abbr", MagicCardField.EDITION_ABBR);
		put("language", MagicCardField.LANG);
		put("lang", MagicCardField.LANG);
		put("cardnumber", MagicCardField.COLLNUM);
		put("collectornumber", MagicCardField.COLLNUM);
		put("collectorsnumber", MagicCardField.COLLNUM);
		put("collnum", MagicCardField.COLLNUM);
		// "id" -> resolved in setFieldValue (Scryfall UUID / scry_ / Gatherer id)
		put("id", MagicCardField.GATHERERID);
		put("scryfallid", MagicCardField.GATHERERID);
		put("scryid", MagicCardField.GATHERERID);
		put("gathererid", MagicCardField.GATHERERID);
		put("gatherercardid", MagicCardField.GATHERERID);
		put("multiverseid", MagicCardField.GATHERERID);
		put("manadeskid", MagicCardField.ID); // ManaDesk's Card Id, when MA re-exports it
		put("foil", MagicCardField.SPECIAL);
		put("special", MagicCardField.SPECIAL);
		put("comment", MagicCardField.COMMENT);
		put("comments", MagicCardField.COMMENT);
		put("notes", MagicCardField.COMMENT);
		put("ownership", MagicCardField.OWNERSHIP);
		put("own", MagicCardField.OWNERSHIP);
		put("owned", MagicCardField.OWNERSHIP);
		put("price", MagicCardField.PRICE);
	}

	private void put(String key, ICardField field) {
		maHeaders.put(normalize(key), field);
	}

	/** lower-case, keep only letters and digits */
	private static String normalize(String s) {
		if (s == null)
			return "";
		StringBuilder b = new StringBuilder();
		for (char c : s.trim().toLowerCase(Locale.ENGLISH).toCharArray())
			if (Character.isLetterOrDigit(c))
				b.append(c);
		return b.toString();
	}

	/** true when {@code raw} is exactly an upper-case {@link MagicCardField} name (ManaDesk style) */
	private static boolean isManaDeskHeader(String raw) {
		String s = raw.trim();
		if (s.equals("SET/EDITION_ABBR"))
			return true;
		if (!s.equals(s.toUpperCase(Locale.ENGLISH)))
			return false;
		try {
			MagicCardField.valueOf(s);
			return true;
		} catch (IllegalArgumentException notAField) {
			return false;
		}
	}

	@Override
	protected int getMinFields() {
		return 1;
	}

	@Override
	public String getExample() {
		return "id,Name,Set,Card Number,Count,Language,Foil\n"
				+ "ce711943-c1a1-43a0-8b89-8d169cfb8e11,Lightning Bolt,Limited Edition Alpha,161,4,English,\n"
				+ "0c4eaecf-dd4c-45ab-9b50-2abe987d35d4,Counterspell,Ice Age,64,2,English,foil\n";
	}

	@Override
	protected void setHeaderFields(List<String> list) {
		ICardField[] fields = new ICardField[list.size()];
		int i = 0;
		int recognized = 0;
		boolean allManaDesk = true;
		boolean hasIdentity = false;
		for (String raw : list) {
			if (raw == null || raw.trim().isEmpty()) {
				fields[i++] = null;
				continue;
			}
			if (!isManaDeskHeader(raw))
				allManaDesk = false;
			ICardField f = maHeaders.get(normalize(raw));
			if (f == null && isCardField(raw)) {
				fields[i++] = null; // a card-database column - recognised, ignored
				recognized++;
				continue;
			}
			if (f == null)
				throw new MagicException(
						"Not a Magic Assistant CSV: unexpected column header '" + raw.trim() + "'");
			fields[i++] = f;
			recognized++;
			hasIdentity |= f == MagicCardField.NAME || f == MagicCardField.GATHERERID || f == MagicCardField.ID;
		}
		if (recognized == 0)
			throw new MagicException("Not a Magic Assistant CSV: header row has no recognised column (" + list + ")");
		if (allManaDesk)
			throw new MagicException(
					"This looks like a ManaDesk CSV (all-caps field ids), not a Magic Assistant CSV (" + list + ")");
		if (!hasIdentity)
			throw new MagicException(
					"Not a Magic Assistant CSV: the header row has no name / id column (" + list + ")");
		setFields(fields);
	}

	private static boolean isCardField(String raw) {
		try {
			MagicCardField.valueOf(raw.trim().toUpperCase(Locale.ENGLISH));
			return true;
		} catch (IllegalArgumentException notAField) {
			return false;
		}
	}

	@Override
	public void setFieldValue(MagicCardPhysical card, ICardField field, int i, String value) {
		if (field == MagicCardField.GATHERERID) {
			if (value == null || value.isEmpty())
				return;
			if (value.startsWith("scry_"))
				value = value.substring("scry_".length());
			if (value.matches(SCRYFALL_UUID))
				card.set(MagicCardField.ID, value); // Scryfall UUID == ManaDesk Card Id
			else
				card.set(MagicCardField.GATHERERID, value); // numeric Gatherer id
			return;
		}
		if (field instanceof ExtraFields) {
			((ExtraFields) field).importInto(card, value);
			return;
		}
		if (field == null)
			return;
		super.setFieldValue(card, field, i, value);
	}
}
