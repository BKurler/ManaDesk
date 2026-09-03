/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;

// Name|Abbr|otherAbbr|ReleaseDate|Type|Block|iconAbbr|Unused(Name aliases)
// field 6 (iconAbbr) is the set-symbol SVG token, e.g. "star" for the generic
// star icon - it is NOT a legality value and NOT a set code.
// Limited Edition Alpha|LEA|1E|August 1993|Core|Core Set|lea|

public class Editions implements ISearchableProperty {
	public static final String EDITIONS_FILE = "editions.txt";
	private static Editions instance;
	private LinkedHashMap<String, Edition> name2ed;
	private LinkedHashMap<String, Edition> nameAliases;
	private Edition unknown;
	static int idcounter = 0;
	private static final SimpleDateFormat formatter = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);

	private Editions() {
		init();
	}

	private Editions(boolean emptyList) {
		init(emptyList);
	}

	/**
	 * This is not public API, only called by tests
	 */
	public void init() {
		init(false);
	}

	public void init(boolean emptyList) {
		this.name2ed = new LinkedHashMap<>();
		this.nameAliases = new LinkedHashMap<>();

		if (!emptyList) {
			try {
				load();
			} catch (Exception e) {
				MagicLogger.log(e);
			}
		}
		unknown = new Edition("Unknown", "???") {
			@Override
			public boolean isUnknown() {
				return true;
			}
		};
		unknown.setBlock("Unknown");
		unknown.setReleaseDate(new Date());
	}

	public synchronized static Editions getInstance() {
		if (instance == null)
			instance = new Editions();
		return instance;
	}

	// When generating the flat files, we want to be sure to always start with an
	// empty Editions list
	// Using that call, we can force that behavior
	public synchronized static Editions getInstance(boolean emptyList) {
		if (instance == null)
			instance = new Editions(emptyList);
		return instance;
	}

	public static Date parseReleaseDate(String date) throws ParseException {
		if (date == null || date.length() == 0 || date.equals("?"))
			return null;
		else
			return formatter.parse(date);
	}

	public Collection<Edition> getEditions() {
		return new ArrayList<>(this.name2ed.values());
	}

	public String getNameByAbbr(String abbr) {
		Edition ed = getEditionByAbbr(abbr);
		if (ed != null)
			return ed.getName();
		return null;
	}

	public Edition getEditionByAbbr(String abbr) {
		if (abbr == null)
			return null;
		for (Edition value : new ArrayList<>(name2ed.values())) {
			if (value != null && value.abbreviationOf(abbr)) {
				return value;
			}
		}
		return null;
	}

	public void addAbbreviation(String name, String abbr) {
		Edition ed = getEditionByName(name);
		if (ed != null)
			ed.addAbbreviation(abbr);
	}

	public void removeEdition(String name, String abbr) {
		Edition edition = name2ed.get(name);

		if (edition != null) {
			// Remove it
			this.remove(edition);
		}
		edition = getEditionByAbbr(abbr);
		if (edition != null) {
			// Remove it
			this.remove(edition);
		}
	}

	public synchronized Edition addEdition(String name, String abbr) {
		if (name.length() == 0)
			throw new IllegalArgumentException();

		// We must override found Editions, to be sure to refresh all the data with the
		// latest info
		// Preventive code because the editions.txt file was broken with previous
		// versions
		removeEdition(name, abbr);

		// Create it, we know it doesn't exist anymore
		Edition edition = new Edition(name, abbr);
		this.name2ed.put(name, edition);

		return edition;
	}

	public synchronized Edition addEdition(Edition set) {
		String name = set.getName();
		if (name.length() == 0)
			throw new IllegalArgumentException();

		// We must override found Editions, to be sure to refresh all the data with the
		// latest info
		// Preventive code because the editions.txt file was broken with previous
		// versions
		removeEdition(name, set.getAbbreviations()[0]);

		Edition edition = set;

		edition = set;
		this.name2ed.put(name, edition);

		return edition;
	}

	public synchronized boolean containsName(String name) {
		return getEditionByName(name) != null;
	}

	public String getAbbrByName(String name) {
		Edition edition = getEditionByName(name);
		if (edition == null)
			return null;
		return edition.getMainAbbreviation();
	}

	public Edition getEditionByName(String name) {
		Edition ed = this.name2ed.get(name);
		if (ed != null)
			return ed;
		return nameAliases.get(name);
	}

	public Edition getEditionByNameAlways(String name) {
		Edition ed = getEditionByName(name);
		if (ed != null)
			return ed;
		return unknown;
	}

	private synchronized void load() throws IOException {
		File oldFile = new File(FileUtils.getStateLocationFile(), EDITIONS_FILE);
		File newFile = getStoreFile();
		if (oldFile.exists() && !newFile.exists()) {
			oldFile.renameTo(newFile);
		}
		File file = newFile;
		try {
			initializeEditions();
		} catch (Exception e) {
			// ignore
		}
		if (!file.exists()) {
			save();
		} else {
			InputStream st = new FileInputStream(file);
			loadEditions(st);
			st.close();
		}
	}

	public static File getStoreFile() {
		return new File(DataManager.getInstance().getTablesDir(), EDITIONS_FILE);
	}

	private void initializeEditions() throws IOException, FileNotFoundException {
		if (false) {
			// Magic 2012|M12||July 2011|Core|
			Edition ed = addEdition("Magic 2012", "M12");
			try {
				ed.setReleaseDate("July 2011");
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			ed.setType("Core");
		} else {
			InputStream ist = FileUtils.loadDbResource(EDITIONS_FILE);
			if (ist != null) {
				loadEditions(ist);
				ist.close();
			}
		}
	}

	private synchronized void loadEditions(InputStream st) throws IOException {
		try (BufferedReader r = new BufferedReader(new InputStreamReader(st))) {
			String line;
			while ((line = r.readLine()) != null) {
				try {
					String[] attrs = line.split("\\|");
					String name = attrs[0].trim();
					String abbr1 = attrs[1].trim();
					Edition set = addEdition(name, abbr1);
					if (attrs.length < 3)
						continue; // old style
					String abbrOther = attrs[2].trim();
					if (abbrOther.equals("en-us") || abbrOther.equals("EN"))
						continue; // old style
					if (abbrOther.length() > 0) {
						String[] abbrs = abbrOther.trim().split(",");
						for (String string : abbrs) {
							set.addAbbreviation(string.trim());
						}
					}
					String releaseDate = attrs[3].trim();
					if (releaseDate != null && releaseDate.length() > 0)
						set.setReleaseDate(releaseDate);
					else
						MagicLogger.log("Missing release date " + line);
					String type = attrs[4].trim();
					if (type != null && type.length() > 0)
						set.setType(type);
					else
						MagicLogger.log("Missing type " + line);
					if (attrs.length <= 5)
						continue;
					// Block
					String block = attrs[5].trim();
					if (block != null && block.length() > 0)
						set.setBlock(block);
					else if ("Expansion".equals(type))
						MagicLogger.log("Missing block " + line);
					if (attrs.length <= 6)
						continue;
					// iconAbbr (set-symbol SVG token)
					String iconAbbr = attrs[6].trim();
					set.setIconAbbr(iconAbbr);
					if (attrs.length <= 7)
						continue;
					// Name aliases
					String[] aliases = attrs[7].trim().split(",");
					for (int i = 0; i < aliases.length; i++) {
						String alias = aliases[i].trim();
						aliases[i] = alias;
						nameAliases.put(alias, set);
					}
					set.setNameAliases(aliases);
					set.setIconAbbr(iconAbbr);
				} catch (Exception e) {
					MagicLogger.log("bad editions record: " + line);
					MagicLogger.log(e);
				}
			}
		}
	}

	public synchronized void save() throws FileNotFoundException {
		save(getStoreFile());
	}

	public synchronized void save(File file) throws FileNotFoundException {
		try (PrintStream st = new PrintStream(file)) {

			// Sort editions by release date (oldest first) then by name (case-insensitive)
			List<Edition> sorted = new ArrayList<>(this.name2ed.values());

			sorted.sort(Comparator.comparing(Edition::getReleaseDate, Comparator.nullsLast(Comparator.naturalOrder()))
					.thenComparing(Edition::getName, String.CASE_INSENSITIVE_ORDER));

			String editions = "";

			for (Edition ed : sorted) {
				String name = ed.getName();

				String rel = "";
				if (ed.getReleaseDate() != null) {
					rel = formatter.format(ed.getReleaseDate());
				}

				String type = ed.getType() != null ? ed.getType() : "";

				editions += name + "|" + ed.getMainAbbreviation() + "|" + ed.getExtraAbbreviations() + "|" + rel + "|"
						+ type + "|" + (ed.getBlock() == null ? "" : ed.getBlock()) + "|" + ed.getIconAbbr() + "|"

						+ ed.getExtraAliases() + "\n";
			}

			st.print(editions);

			System.err.println("Editions.txt file save under " + file.getAbsolutePath());
		}
	}

	@Override
	public String getIdPrefix() {
		return getFilterField().toString();
	}

	@Override
	public FilterField getFilterField() {
		return FilterField.EDITION;
	}

	@Override
	public Collection<String> getIds() {
		ArrayList<String> list = new ArrayList<>();
		for (Edition ed : this.name2ed.values()) {
			String abbr = ed.getMainAbbreviation();
			list.add(getPrefConstant(abbr));
		}
		return list;
	}

	public String getPrefConstant(String abbr) {
		return FilterField.getPrefConstant(getIdPrefix(), abbr);
	}

	public String getPrefConstantByName(String name) {
		String abbr = getAbbrByName(name);
		return FilterField.getPrefConstant(getIdPrefix(), abbr);
	}

	@Override
	public String getNameById(String id) {
		HashMap<String, String> idToName = new HashMap<>();

		// Snapshot avoids ConcurrentModificationException
		for (String name : new ArrayList<>(this.name2ed.keySet())) {
			String id1 = getPrefConstantByName(name);
			idToName.put(id1, name);
		}

		return idToName.get(id);
	}

	public Collection<String> getNames() {
		return new ArrayList<>(this.name2ed.keySet());
	}

	public synchronized void remove(Edition ed) {
		name2ed.remove(ed.getName());
	}

	public Edition getEditionByNameIgnoreCase(String ed) {
		for (String name : this.name2ed.keySet()) {
			if (name.equalsIgnoreCase(ed))
				return name2ed.get(name);
		}
		for (String name : this.nameAliases.keySet()) {
			if (name.equalsIgnoreCase(ed))
				return nameAliases.get(name);
		}
		return null;
	}
}
