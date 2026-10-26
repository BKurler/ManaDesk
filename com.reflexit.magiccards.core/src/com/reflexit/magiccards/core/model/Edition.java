
/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - digital flag (a digital-only set)
 */

package com.reflexit.magiccards.core.model;

import java.text.ParseException;
import java.util.Date;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.legality.Format;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.sync.EditionFileCache;

public class Edition {
	private String name;
	private String abbrs[];
	private String aliases[];
	private Date release;
	private String type = "?";
	private LegalityMap legalityMap = LegalityMap.EMPTY;
	private String block;
	private int id;
	private EditionFileCache imageFiles;
	private String iconAbbr;
	private boolean digital;

	public Edition(String name, String abbr) {
		if (name.contains("#")) {
			name = name.replace("&#39;", "'");
		}
		this.name = name;
		if (abbr != null && abbr.contains(" ")) {
			System.err.println("Bad abbreviation " + abbr + " for " + this);
			abbr = null;
		}
		this.abbrs = new String[] { abbr == null ? fakeAbbr(name) : abbr };
		this.id = ++Editions.idcounter;
		this.aliases = new String[0];
		this.imageFiles = new EditionFileCache(this);
	}

	private String fakeAbbr(String xname) {
		return "_" + xname.replaceAll("\\W", "_");
	}

	@Override
	public String toString() {
		return name;
	}

	public void setReleaseDate(String date) throws ParseException {
		release = Editions.parseReleaseDate(date);
	}

	public Date getReleaseDate() {
		return release;
	}

	public boolean abbreviationOf(String abbr) {
		if (abbr == null || abbrs.length == 0)
			return false;
		for (String a : abbrs) {
			if (abbr.equals(a))
				return true;
		}
		return false;
	}

	public void addAbbreviation(String abbr) {
		if (abbr == null)
			return;
		if (abbr.contains(" ")) {
			System.err.println("Bad abbreviation " + abbr + " for " + this);
			return;
		}
		if (isAbbreviationFake()) {
			abbrs[0] = abbr;
		} else {
			for (String abbr2 : abbrs) {
				if (abbr2.equals(abbr))
					return;
			}
			String[] arr = new String[abbrs.length + 1];
			System.arraycopy(abbrs, 0, arr, 0, abbrs.length);
			arr[abbrs.length] = abbr;
			abbrs = arr;
		}
	}

	private boolean isAbbreviationFake() {
		return getMainAbbreviation().startsWith("_");
	}

	public String getMainAbbreviation() {
		return abbrs[0];
	}
	
	// RD Modification to automatically remove the first letter of the SET abbreviation 
	// for those "special" sets to be able to use standard set "icon" 
	public String getIconAbbreviation() {
		String iAbb = this.abbrs[0];
		if (this.name.contains(" Promos") || // P
				this.name.contains(" Tokens") || // T
				this.name.contains(" Art Series") || // A
				this.name.contains(" Minigames") || // M
				this.name.contains(" Schemes") || // O
				this.name.contains(" Planes") || // O
				this.name.contains(" Sheets") // S
		) {
			iAbb = iAbb.substring(1);
		}
		return iAbb;
	}

	public void setMainAbbreviation(String abbr) {
		if (abbr.contains(" ")) {
			System.err.println("Bad abbreviation " + abbr + " for " + this);
			return;
		}
		if (isAbbreviationFake()) {
			abbrs[0] = abbr;
		} else {
			for (int i = 0; i < abbrs.length; i++) {
				if (abbrs[i].equals(abbr)) {
					if (i == 0)
						return;
					String temp = abbrs[0];
					abbrs[0] = abbr;
					abbrs[i] = temp;
					return;
				}
			}
			String[] arr = new String[abbrs.length + 1];
			System.arraycopy(abbrs, 0, arr, 1, abbrs.length);
			arr[0] = abbr;
			abbrs = arr;
		}
	}

	public String getExtraAbbreviations() {
		if (abbrs.length > 1) {
			String line = abbrs[1];
			for (int i = 2; i < abbrs.length; i++) {
				line += "," + abbrs[i];
			}
			return line;
		}
		return "";
	}

	public String[] getAbbreviations() {
		return abbrs;
	}

	public String getExtraAliases() {
		if (aliases.length > 0) {
			String line = aliases[0];
			for (int i = 1; i < aliases.length; i++) {
				line += "," + aliases[i];
			}
			return line;
		}
		return "";
	}

	public String[] getAliases() {
		return aliases;
	}

	public String getIconAbbr() {
		return iconAbbr;
	}

	public void setIconAbbr(String abbr) {
		iconAbbr = abbr;
	}

	/** True for a digital-only set (MTGO / Arena): every card of it is digital-only. */
	public boolean isDigital() {
		return digital;
	}

	public void setDigital(boolean digital) {
		this.digital = digital;
	}

	public void setType(String type) {
		this.type = parseSetType(type);
	}

	public static String parseSetType(String type) {
		if (type == null)
			return "?";
		switch (type) {
		case "Core":
		case "core":
			return "Core";
		case "Expansion":
		case "expansion":
			return "Expansion";
		case "Reprint":
		case "reprint":
			return "Reprint";
		case "Starter":
		case "starter":
			return "Starter";
		case "Online":
		case "online":
			return "Online";
		case "Modifiers":
		case "modifiers":
			return "Modifiers";
		case "Un_set":
		case "un_set":
			return "Un_set";
		case "Token":
		case "token":
			return "Token";
		case "promo":
		case "Promo":
			return "Promo";
		case "?":
		case "":
			return "?";
		default:
			return "Other";
		}
	}

	public String getName() {
		return name;
	}

	public void setReleaseDate(Date time) {
		release = time;
	}

	public String getType() {
		return type;
	}

	public String getBlock() {
		if (block == null)
			return name;
		return block;
	}

	public String getBaseFileName() {
		String a = getMainAbbreviation();
		return FileUtils.getAgnosticFilename(a);
	}

	public String getIconBaseFileName() {
		String a = this.getIconAbbreviation();
		return FileUtils.getAgnosticFilename(a);
	}

	public LegalityMap getLegalityMap() {
		return legalityMap;
	}

	public String getFormatString() {
		String string = legalityMap.legalFormats();
		return string;
	}

	public Format getFormat() {
		if (legalityMap.isEmpty())
			return Format.LEGACY;
		return legalityMap.getFirstLegal();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Edition other = (Edition) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	public void setFormats(String legality) {
		legalityMap = LegalityMap.createFromLegal(legality);
	}

	public void setBlock(String block) {
		this.block = block;
	}

	public int getId() {
		return id;
	}

	public boolean isUsedByPrintings() {
		ICardStore<IMagicCard> magicDb = DataManager.getInstance().getMagicDBStore();
		for (IMagicCard card : magicDb) {
			if (name.equals(card.getSet())) {
				return true;
			}
		}
		return false;
	}

	public boolean isUsedByInstances() {
		ICardStore<IMagicCard> store = DataManager.getInstance().getLibraryCardStore();
		for (IMagicCard card : store) {
			if (name.equals(card.getSet())) {
				return true;
			}
		}
		return false;
	}

	public void addNameAlias(String alias) {
		for (String a : aliases) {
			if (a.equals(alias))
				return;
		}
		String[] naliases = new String[aliases.length + 1];
		System.arraycopy(naliases, 0, aliases, 0, aliases.length);
		naliases[aliases.length] = alias;
		aliases = naliases;
	}

	public void setNameAliases(String aliases[]) {
		this.aliases = new String[aliases.length];
		System.arraycopy(aliases, 0, this.aliases, 0, aliases.length);
	}

	public void setLegalityMap(LegalityMap lm) {
		legalityMap = lm;
	}

	public boolean isUnknown() {
		return false;
	}

	public boolean isHidden() {
		return getType().contains("--");
	}

	public void setHidden(boolean hidden) {
		if (hidden == isHidden())
			return;
		if (hidden)
			setType("--" + getType());
		else
			setType(getType().replace("--", ""));
	}

	public EditionFileCache getImageFiles() {
		return imageFiles;
	}
}