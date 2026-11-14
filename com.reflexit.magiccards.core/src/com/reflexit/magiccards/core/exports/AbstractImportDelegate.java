/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - importCard(): into a For Trade collection the file's trade count is ignored, every card is tagged
 *     Rémi Dutil (2026) - importCard() applies ImportData#isOwnershipFixed()
 */
package com.reflexit.magiccards.core.exports;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.ForTradeRules;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.monitor.ICoreRunnableWithProgress;

public abstract class AbstractImportDelegate implements ICoreRunnableWithProgress, IImportDelegate {
	private InputStream stream;
	protected ImportData importData;
	protected int lineNum = 0;
	private ReportType type;

	public AbstractImportDelegate() {
	}

	public InputStream getStream() {
		return stream;
	}

	@Override
	public void setReportType(ReportType reportType) {
		this.type = reportType;
	}

	@Override
	public ReportType getType() {
		return type;
	}

	@Override
	public void init(ImportData result) {
		this.importData = result;
		this.stream = new ByteArrayInputStream(result.getText().getBytes(FileUtils.CHARSET_UTF_8));
		importData.clear();
		importData.setType(getType());
		importData.setFields(getNonTransientFeilds());
		lineNum = 0;
		setFromCode.clear();
	}

	public Location getSideboardLocation() {
		if (getLocation() == null)
			return Location.createLocation("sideboard");
		Location sideboard = getLocation().toSideboard();
		return sideboard;
	}

	@Override
	public void run(ICoreProgressMonitor monitor) {
		monitor.beginTask("Importing...", 100);
		try {
			doRun(monitor);
		} catch (Exception e) {
			importData.setError(e);
		} finally {
			monitor.done();
		}
	}

	protected abstract void doRun(ICoreProgressMonitor monitor) throws IOException;

	@Override
	public ImportData getResult() {
		return importData;
	}

	protected MagicCardPhysical createDefaultCard() {
		MagicCardPhysical card = new MagicCardPhysical(new MagicCard(), getLocation());
		card.setOwn(!isVirutal());
		return card;
	}

	protected boolean isVirutal() {
		return importData.isVirtual();
	}

	protected void importCard(MagicCardPhysical card) {
		if (card == null)
			return;
		// the destination decides ownership, not the file (see ImportData)
		importData.applyFixedOwnership(card);
		if (importData.isForTradeDestination()) {
			// a For Trade collection: every card is for trade, whatever the file's trade count says (0 or any other
			// value): no split of the pile, and the tag is always set (a proxy is never for trade: left alone)
			card.setProperty(MagicCardField.FORTRADECOUNT, null);
			ForTradeRules.tag(card);
		} else if (!card.isMigrated()) {
			MagicCardPhysical ncard = card.tradeSplit(card.getCount(), card.getForTrade());
			if (ncard != null) {
				importData.add(ncard);
				importData.rememberDeclared(ncard);
			}
		}
		importData.add(card);
		importData.rememberDeclared(card);
	}

	@Override
	public void setFieldValue(MagicCardPhysical card, ICardField field, int i, String value) {
		if (field == MagicCardField.EDITION_ABBR) {
			setEditionCode(card, value);
		} else if (field == MagicCardField.SET) {
			// a set name never replaces a set found from a (known) set code
			if (!setFromCode.contains(card))
				card.set(MagicCardField.SET, value);
		} else if (field == MagicCardField.SIDEBOARD) {
			if (Boolean.valueOf(value).booleanValue()) {
				card.setLocation(getSideboardLocation());
			}
		} else if (field == MagicCardField.LOCATION) {
			// ignore
		} else if (field.isTransient()) {
			if (field == MagicCardField.FORTRADECOUNT || field == MagicCardField.LEGALITY
					|| field == MagicCardField.IMAGE_URL) {
				// special handling for transient fields, we will set it
				card.set(field, value);
			} else {
				// ignore
			}
		} else {
			card.set(field, value);
		}
	}

	/** The cards whose set was found from a known set code in this run (see {@link #setEditionCode}). */
	private final Set<Object> setFromCode = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());

	/**
	 * The set of a card read from a "set code" column of a file that has no Scryfall id. The rule, whatever the
	 * order of the columns: a known code (matched ignoring the case) wins over a set name; an unknown code never
	 * replaces a name that is a known set; and a value that is not a known code is taken as a set name (the
	 * header may also stand for a set name, as in the ManaDesk CSV's SET/EDITION_ABBR column).
	 */
	protected void setEditionCode(MagicCardPhysical card, String value) {
		String v = value == null ? "" : value.trim();
		if (v.isEmpty())
			return;
		String name = setNameByCode(v);
		if (name != null) {
			card.set(MagicCardField.SET, name);
			setFromCode.add(card);
			return;
		}
		if (setFromCode.contains(card))
			return; // a known code already decided
		String current = card.getSet();
		if (current != null && !current.isEmpty() && !isSetName(v)) {
			// an unknown code never replaces a name: neither a known set, nor any text when the value has no
			// space (a code can't have one - a value with a space is a name, e.g. in a SET/EDITION_ABBR column)
			if (isSetName(current) || v.indexOf(' ') < 0)
				return;
		}
		card.set(MagicCardField.SET, v);
	}

	/** The name of the set whose abbreviation is {@code code} (ignoring the case), or {@code null}. */
	protected String setNameByCode(String code) {
		Edition ed = ImportUtils.findEditionByCode(code);
		return ed == null ? null : ed.getName();
	}

	/** True when {@code name} is the name (or a known alias) of a set. */
	protected boolean isSetName(String name) {
		return ImportUtils.resolveSet(name) != null;
	}

	/**
	 * Sets the grade read from the file. A text that is not blank but gives no grade is not lost silently: the
	 * copy stays ungraded and the text is remembered, for the preview's warning and its yellow cell.
	 */
	protected void setConditionOrUnknown(MagicCardPhysical card, String value, CardCondition grade) {
		card.setCondition(grade);
		if (grade == null && value != null && !value.trim().isEmpty())
			importData.declareUnknownCondition(card, value);
	}

	protected Location getLocation() {
		return importData.getLocation();
	}

	static ICardField[] getNonTransientFeilds() {
		return MagicCardField.allNonTransientFields(true);
	}

	public void setLocation(Location location) {
		this.importData.setLocation(location);
	}

	@Override
	public String getExample() {
		return null;
	}
}
