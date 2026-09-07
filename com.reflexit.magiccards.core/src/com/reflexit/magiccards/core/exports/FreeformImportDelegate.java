/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk
 */
package com.reflexit.magiccards.core.exports;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * A deliberately forgiving text importer. It copes with the kind of loosely
 * formatted deck lists people paste around, in particular the output of a custom
 * "{@code %d %s}" ManaDesk exporter:
 *
 * <pre>
 * # deck name              &lt;- comment / deck name, ignored
 * 1 Karn, the Great Creator Legendary Planeswalker - Karn   ee243dbe-...
 * Sideboard                &lt;- section marker
 * 1 Plains Basic Land - Plains   18a72e75-...
 * </pre>
 *
 * Per line it looks for:
 * <ul>
 * <li>a leading count ({@code 3}, {@code 3x}, {@code 3 x}) - defaults to 1</li>
 * <li>a Scryfall id anywhere on the line - if present, the card is resolved by
 * that id and the rest of the line is ignored</li>
 * <li>otherwise a {@code Name (Set)} / {@code Name x3} shape, falling back to
 * "the whole line is the name"</li>
 * </ul>
 */
public class FreeformImportDelegate extends AbstractImportDelegate {

	private static final Pattern SCRYFALL_ID = Pattern.compile(
			"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
	private static final Pattern LEADING_COUNT = Pattern.compile("^\\s*(\\d{1,4})\\s*[xX]?\\s+(.*\\S)\\s*$");
	private static final Pattern TRAILING_COUNT = Pattern.compile("^(.*\\S)\\s+[xX]\\s*(\\d{1,4})\\s*$");
	private static final Pattern NAME_SET = Pattern.compile("^(.*?)\\s*\\(([^)]+)\\)\\s*$");
	private static final Pattern SIDEBOARD = Pattern.compile("^side\\s*board\\b.*", Pattern.CASE_INSENSITIVE);
	private static final Pattern MAINDECK = Pattern.compile("^(main\\s*deck|deck|maindeck)\\b.*",
			Pattern.CASE_INSENSITIVE);

	@Override
	protected void doRun(ICoreProgressMonitor monitor) throws IOException {
		// only these come from the file; the preview column set is built from this
		importData.setFields(new com.reflexit.magiccards.core.model.abs.ICardField[] { MagicCardField.COUNT,
				MagicCardField.NAME, MagicCardField.SET, MagicCardField.COLLNUM, MagicCardField.ID });
		boolean sideboard = false;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(getStream(), FileUtils.CHARSET_UTF_8))) {
			String raw;
			while ((raw = r.readLine()) != null) {
				lineNum++;
				String line = raw.trim();
				if (line.isEmpty() || line.startsWith("#") || line.startsWith("//"))
					continue;
				if (SIDEBOARD.matcher(line).matches()) {
					sideboard = true;
					continue;
				}
				if (MAINDECK.matcher(line).matches()) {
					sideboard = false;
					continue;
				}
				MagicCardPhysical card = parseLine(line);
				if (card == null)
					continue;
				if (sideboard)
					card.setLocation(getSideboardLocation());
				importCard(card);
				monitor.worked(1);
			}
		}
	}

	private MagicCardPhysical parseLine(String line) {
		int count = 1;
		String body = line;

		Matcher lc = LEADING_COUNT.matcher(body);
		if (lc.matches()) {
			count = parseCount(lc.group(1));
			body = lc.group(2).trim();
		} else {
			Matcher tc = TRAILING_COUNT.matcher(body);
			if (tc.matches()) {
				body = tc.group(1).trim();
				count = parseCount(tc.group(2));
			}
		}

		MagicCardPhysical card = createDefaultCard();
		card.setCount(count);

		Matcher id = SCRYFALL_ID.matcher(body);
		if (id.find()) {
			// the id is authoritative - resolution fills in name / set / etc
			card.set(MagicCardField.ID, id.group());
			return card;
		}

		String name = body;
		Matcher ns = NAME_SET.matcher(body);
		if (ns.matches() && !ns.group(1).trim().isEmpty()) {
			name = ns.group(1).trim();
			card.set(MagicCardField.SET, ns.group(2).trim());
		}
		if (name.isEmpty()) {
			card.setError("Cannot read a card from: " + line);
			return card;
		}
		card.set(MagicCardField.NAME, name);
		return card;
	}

	private static int parseCount(String s) {
		try {
			int n = Integer.parseInt(s.trim());
			return n <= 0 ? 1 : n;
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	@Override
	public String getExample() {
		return "# My deck\n"
				+ "4 Lightning Bolt\n"
				+ "2 Counterspell (Ice Age)\n"
				+ "1 Karn, the Great Creator   ee243dbe-83ee-40af-8e26-c1528166ef5a\n"
				+ "Sideboard\n"
				+ "2 Reliquary Tower x 1\n";
	}
}
