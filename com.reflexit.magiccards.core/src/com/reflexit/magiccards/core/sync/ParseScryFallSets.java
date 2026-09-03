
/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.sync;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.sync.ParserHtmlHelper.ILoadCardHander;
import com.reflexit.magiccards.core.sync.ParserHtmlHelper.OutputHandler;

public class ParseScryFallSets extends AbstractParseJson {
	public static final String BASE_SEARCH_URL = "https://api.scryfall.com/sets";
	private Collection<Edition> newSets = new ArrayList<>();
	private Collection<Edition> allSets = new ArrayList<>();

	// RD Set to true when we're generating the flat file. This is required to know
	// if we add the sets right away
	private boolean generateFlat = false;

	@Override
	public String processFromReader(BufferedReader st, ILoadCardHander handler) throws IOException {
		try {
			JSONObject top = (JSONObject) new JSONParser().parse(st);
			Boolean has_more = (Boolean) top.get("has_more");
			JSONArray data = (JSONArray) top.get("data");
			for (Object elem : data) {
				parseRecord((JSONObject) elem, handler);
			}
			if (has_more) {
				return getString(top, "next_page");
			}
		} catch (ParseException e) {
			MagicLogger.log(e);
			throw new RuntimeException("No results");
		}
		return null;
	}

	private static String extractSetIconToken(String iconSvgUri) {
		if (iconSvgUri == null)
			return null;

		// Remove query parameters
		int q = iconSvgUri.indexOf('?');
		String clean = (q >= 0) ? iconSvgUri.substring(0, q) : iconSvgUri;

		// Extract filename
		int lastSlash = clean.lastIndexOf('/');
		int dot = clean.lastIndexOf(".svg");

		if (lastSlash < 0 || dot < 0 || dot <= lastSlash) {
			return null; // unexpected format
		}

		return clean.substring(lastSlash + 1, dot);
	}

	private void parseRecord(JSONObject elem, ILoadCardHander handler) {
		if (elem == null)
			return;

		String abbr = getString(elem, "code");
		String abbr2 = getAltCode(abbr);
		String set = getString(elem, "name");
		String setType = getString(elem, "set_type");
		Edition ed = new Edition(set, abbr.toUpperCase(Locale.ENGLISH));

		if (abbr2 != null) {
			ed.addAbbreviation(abbr2);
		}

		ed.setType(setType);
		ed.setBlock(getString(elem, "block"));
		try {
			String date = getString(elem, "released_at");
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
			Date d = formatter.parse(date);
			ed.setReleaseDate(d);
		} catch (java.text.ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		ed.setIconAbbr(extractSetIconToken(getString(elem, "icon_svg_uri")));

		Boolean digital = getBool(elem, "digital");
		int cardCount = getInt(elem, "card_count");

		// If the date is in the future, skip the set to prevent issue while parsing the
		// cards
		LocalDate threshold = LocalDate.now();

		// Convert to LocalDate
		LocalDate setDate = ed.getReleaseDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

		// RD Skip digital set and set with no card (typically future sets)
		if (!digital && cardCount > 0 && setDate.compareTo(threshold) <= 0) {

			Editions currentEd = Editions.getInstance();

			if (!currentEd.containsName(set)) {
				newSets.add(ed);
			}
			allSets.add(ed);

			if (generateFlat) {
				handler.handleEdition(ed);
			}
		}
		// else: digital-only / empty / future set - silently skipped
	}

	// Scryfall doesn't provide alternate code. This is typically for older sets, so
	// the list is pretty fixed
	private String getAltCode(String code) {

		String altCode = null;

		// Source: https://mtg.wiki/page/List_of_Magic_sets
		switch (code.toLowerCase()) {
		case "lea":
			altCode = "1E";
			break;
		case "leb":
			altCode = "2E";
			break;
		case "2ed":
			altCode = "2U";
			break;
		case "arn":
			altCode = "AN";
			break;
		case "atq":
			altCode = "AQ";
			break;
		case "3ed":
			altCode = "3E";
			break;
		case "leg":
			altCode = "LE";
			break;
		case "drk":
			altCode = "DK";
			break;
		case "fem":
			altCode = "FE";
			break;
		case "4ed":
			altCode = "4E";
			break;
		case "ice":
			altCode = "IA";
			break;
		case "chr":
			altCode = "CH";
			break;
		case "hml":
			altCode = "HM";
			break;
		case "all":
			altCode = "AL";
			break;
		case "mir":
			altCode = "MI";
			break;
		case "vis":
			altCode = "VI";
			break;
		case "5ed":
			altCode = "5E";
			break;
		case "por":
			altCode = "PO";
			break;
		case "wth":
			altCode = "WL";
			break;
		case "tmp":
			altCode = "TE";
			break;
		case "sth":
			altCode = "ST";
			break;
		case "exo":
			altCode = "EX";
			break;
		case "p02":
			altCode = "P2";
			break;
		case "ugl":
			altCode = "UG";
			break;
		case "usg":
			altCode = "UZ";
			break;
		case "ath":
			altCode = "AT"; // Added myself
			break;
		case "ulg":
			altCode = "UL";
			break;
		case "6ed":
			altCode = "6E";
			break;
		case "ptk":
			altCode = "PK";
			break;
		case "uds":
			altCode = "UD";
			break;
		case "s99":
			altCode = "P3";
			break;
		case "mmq":
			altCode = "MM";
			break;
		case "brb":
			altCode = "BR"; // Added myself
			break;
		case "nem":
			altCode = "NE";
			break;
		case "s00":
			altCode = "P4"; // Added myself
			break;
		case "pcy":
			altCode = "PR";
			break;
		case "inv":
			altCode = "IN";
			break;
		case "btd":
			altCode = "BD"; // Added myself
			break;
		case "pls":
			altCode = "PS";
			break;
		case "7ed":
			altCode = "7E";
			break;
		case "apc":
			altCode = "AP";
			break;
		case "ody":
			altCode = "OD";
			break;
		case "tor":
			altCode = "TO"; // Added myself
			break;
		case "jud":
			altCode = "JU"; // Added myself
			break;
		case "ons":
			altCode = "ON"; // Added myself
			break;
		case "8ed":
			altCode = "8E"; // Added myself
			break;
		case "gk1":
			altCode = "GK1_BOROS,GK1_DIMIR,GK1_GOLGAR,GK1_IZZET,GK1_SELESN"; // Added myself
			break;
		case "gk2":
			altCode = "GK2_AZORIU,GK2_GRUUL,GK2_ORZHOV,GK2_RAKDOS,GK2_SIMIC"; // Added myself
			break;
		case "dvd":
			altCode = "DD3_DVD"; // Added myself
			break;
		case "evg":
			altCode = "DD3_EVG"; // Added myself
			break;
		case "gvl":
			altCode = "DD3_GVL"; // Added myself
			break;
		case "jvc":
			altCode = "DD3_JVC"; // Added myself
			break;
		case "med":
			altCode = "MPS_GRN,MPS_RNA,MPS_WAR"; // Added myself
			break;
		case "puma":
			altCode = "UMA_BOX"; // Added myself
			break;
		case "ugin":
			altCode = "FRF_UGIN"; // Added myself
			break;
		case "mps":
			altCode = "MPS_KLD"; // Added myself
			break;
		case "mp2":
			altCode = "MPS_AKH"; // Added myself
			break;
		case "unf":
			altCode = "UNA"; // Added myself
			break;
		default:
			break;
		}

		return altCode;
	}

	public Collection<Edition> getNew() {
		return newSets;
	}

	public Collection<Edition> getAll() {
		return allSets;
	}

	public ILoadCardHander loadSets(boolean flat) throws IOException {
		generateFlat = flat;
		OutputHandler handler = new OutputHandler(System.out, true, true);
		this.loadSingleUrl(this.getSearchQuery(BASE_SEARCH_URL), handler);

		return handler;
	}

	@Override
	public URL getSearchQuery(String set) {
		try {
			return new URL(BASE_SEARCH_URL);
		} catch (MalformedURLException e) {
			return null; // not possible
		}
	}

	public static void main(String[] args) throws MalformedURLException, IOException {
		// Not really useful...
		ILoadCardHander handler = new ParseScryFallSets().loadSets(true);
		System.err.println("Total " + handler.getCardCount());
	}
}
