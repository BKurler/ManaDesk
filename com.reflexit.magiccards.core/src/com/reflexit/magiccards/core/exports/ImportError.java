

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - SET_NOT_DIGITAL_ERROR / SET_NOT_PAPER_ERROR: the set has no digital / paper version of the card
 */

package com.reflexit.magiccards.core.exports;

import java.text.MessageFormat;

import com.reflexit.magiccards.core.model.abs.ICardField;

public class ImportError {
	enum Type {
		SET_NOT_FOUND("Set not found"),
		NAME_NOT_FOUND_IN_SET("Name not found in the set"),
		ID_NOT_FOUND_IN_DB("ID not found in db"),
		NO_DB("No DB"),
		CANNOT_SET_FIELD("Cannot set field {0} to value {1}"),
		NO_LANG("Language is not loaded in DB"),
		NO_VARIANT("Card variant is not loaded in DB"),
		SET_NOT_DIGITAL("This set has no digital (MTGO / Arena) version of the card"),
		SET_NOT_PAPER("This set has no paper version of the card"),
		// end
		;

		String msg;

		Type(String s) {
			msg = s;
		}
	}

	public static final ImportError SET_NOT_FOUND_ERROR = new ImportError(Type.SET_NOT_FOUND);
	public static final ImportError NAME_NOT_FOUND_IN_SET_ERROR = new ImportError(Type.NAME_NOT_FOUND_IN_SET);
	public static final ImportError NAME_NOT_FOUND_IN_DB_ERROR = new ImportError(Type.ID_NOT_FOUND_IN_DB);
	public static final ImportError NO_DB_ERROR = new ImportError(Type.NO_DB);
	public static final ImportError NO_LANG = new ImportError(Type.NO_LANG);
	public static final ImportError NO_VARIANT = new ImportError(Type.NO_VARIANT);
	/** The card exists in the set, but not on MTGO / Arena (a digital list is imported into). */
	public static final ImportError SET_NOT_DIGITAL_ERROR = new ImportError(Type.SET_NOT_DIGITAL);
	/** The card exists in the set, but only digitally (a paper list is imported into). */
	public static final ImportError SET_NOT_PAPER_ERROR = new ImportError(Type.SET_NOT_PAPER);
	private String[] arg;
	private ImportError.Type type;

	public ImportError(ImportError.Type type) {
		this.type = type;
	}

	public ImportError(ImportError.Type type, String[] arg) {
		this.type = type;
		this.arg = arg;
	}

	public ImportError.Type getType() {
		return type;
	}

	@Override
	public String toString() {
		return MessageFormat.format(type.msg, (Object[]) arg);
	}

	public static ImportError createFieldNotSetError(ICardField f, Exception e) {
		return new ImportError(Type.CANNOT_SET_FIELD, new String[] { f.toString(), e.getMessage() });
	}
}