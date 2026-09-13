/*
 * Contributors:
 *     Rémi Dutil (2026) - dropped the RATING exclusion (the field itself is
 *                         gone - community rating is not a concept this app
 *                         tracks anymore)
 */
package com.reflexit.magiccards.core.sync;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICardField;

public class TextPrinter {
	public final static String SEPARATOR = "|";
	public final static char SEPARATOR_CHAR = '|';
	static final Collection<ICardField> magicCardExportFields;
	static {
		ICardField[] values = MagicCardField.allNonTransientFields(false);
		LinkedHashSet<ICardField> list = new LinkedHashSet<>();
		for (ICardField magicCardField : values) {
			list.add(magicCardField);
		}
		list.remove(MagicCardField.RULINGS);
		list.remove(MagicCardField.DBPRICE);
		list.remove(MagicCardField.ENID);
		magicCardExportFields = list;
	}

	public static Collection<Object> values(IMagicCard card, Collection<ICardField> fields) {
		Collection<Object> list = new ArrayList<>();
		for (ICardField magicCardField : fields) {
			Object field = card.get(magicCardField);
			if (field instanceof String && ((String) field).trim().isEmpty())
				field = "";
			if (field instanceof String && ((String) field).contains(SEPARATOR)) {
				System.err.println("error: separator in card " + card.getName());
			}
			list.add(field);
		}
		return list;
	};

	private static Collection<String> headers(Collection<ICardField> fields) {
		Collection<String> list = new ArrayList<>();
		for (ICardField magicCardField : fields) {
			list.add(magicCardField.toString());
		}
		return list;
	};

	public static void printHeader(PrintStream out) {
		out.println(getHeader());
	}

	public static String getHeader() {
		return join(headers(magicCardExportFields), SEPARATOR_CHAR);
	}

	public static String getString(MagicCard card) {
		return join(values(card, magicCardExportFields), SEPARATOR_CHAR);
	}

	public static void print(MagicCard card, PrintStream out) {
		out.println(getString(card));
	}

	public static String join(Collection<?> list, char sep) {
		StringBuffer buf = new StringBuffer();
		for (Iterator<?> iter = list.iterator(); iter.hasNext();) {
			Object element = iter.next();
			if (element != null) {
				String value = element.toString();
				value = value.replace("\n", "<br>");
				if (value.contains(SEPARATOR)) {
					value = "\"" + value + "\"";
				}
				buf.append(value);
			}
			if (iter.hasNext()) {
				buf.append(sep);
			}
		}
		return buf.toString();
	}
}
