/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - ownershipFixed: importing into a deck / collection
 *                         pins every card's ownership to the destination (own
 *                         for a non-virtual list, virtual otherwise), whatever
 *                         the file's own Ownership column says -
 *                         applyFixedOwnership(). A file-virtual card going
 *                         into a non-virtual list is also marked Proxy; both
 *                         kinds of change are counted for the preview warning
 *     Rémi Dutil (2026) - declareLanguage(): a non-English language the file
 *                         gives for a card that is imported as English (the
 *                         card database only has English printings), for the
 *                         preview's warning / yellow Language cell
 */
package com.reflexit.magiccards.core.exports;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardField;

public class ImportData {
	private ArrayList<ICard> toImport = new ArrayList<ICard>();
	private ICardField[] fields = new ICardField[0];
	private ReportType type = ImportExportFactory.TEXT_DECK_CLASSIC;
	private Throwable error = null;
	private String text = "";
	private Location location = Location.createLocation("preview");
	private boolean virtual = false;
	/** When true, every imported card takes ownership {@code !virtual} - see the header. */
	private boolean ownershipFixed = false;
	/** Copies the file said were Own, imported as virtual (virtual target). */
	private int forcedVirtual = 0;
	/** Copies the file said were virtual, imported as Own + Proxy (non-virtual target). */
	private int forcedOwnProxy = 0;
	/** card -&gt; the non-English language the file gave it (imported as English) */
	private final IdentityHashMap<Object, String> declaredLanguage = new IdentityHashMap<>();
	private ImportSource importSource;
	private LinkedHashMap<String, Object> props = new LinkedHashMap<>();
	/** what the file declared for each card (before resolution swaps the base): [name,set,collnum,id,gathererId,tcgId] */
	private final IdentityHashMap<Object, String[]> declared = new IdentityHashMap<>();

	public ImportData(boolean virtual2, Location location2, String line) {
		virtual = virtual2;
		location = location2;
		text = line;
	}

	public ImportData() {
	}

	/**
	 * @param error
	 *            the error to set
	 */
	public void setError(Throwable error) {
		this.error = error;
	}

	/**
	 * @return the error
	 */
	public Throwable getError() {
		return error;
	}

	/**
	 * @param fields
	 *            the fields to set
	 */
	public void setFields(ICardField[] fields) {
		this.fields = fields;
	}

	/**
	 * @return the fields
	 */
	public ICardField[] getFields() {
		return fields;
	}

	/**
	 * @param type
	 *            the type to set
	 */
	public void setType(ReportType type) {
		this.type = type;
	}

	/**
	 * @return the type
	 */
	public ReportType getType() {
		return type;
	}

	/**
	 * @param toImport
	 *            the toImport to set
	 */
	public void add(ICard card) {
		toImport.add(card);
	}

	/**
	 * @return the toImport
	 */
	public List<? extends ICard> getList() {
		return toImport;
	}

	public int size() {
		return toImport.size();
	}

	public int getErrorCount() {
		int errorCount = 0;
		if (toImport.size() > 0) {
			for (Iterator<ICard> iterator = toImport.iterator(); iterator.hasNext();) {
				ICard card = iterator.next();
				if (card instanceof MagicCardPhysical && ((MagicCardPhysical) card).getError() != null) {
					errorCount++;
				}
			}
		}
		return errorCount;
	}

	public void clear() {
		toImport.clear();
		declared.clear();
		error = null;
		forcedVirtual = 0;
		forcedOwnProxy = 0;
		declaredLanguage.clear();
	}

	/** Snapshot what the file said about this card, before resolution rewrites its base. */
	public void rememberDeclared(MagicCardPhysical c) {
		declared.put(c, new String[] { c.getName(), c.getSet(), c.getCollectorId(), c.getCardId(),
				c.getCard().getGathererCardId(), c.getCard().getTcgCardId() });
	}

	/** [name,set,collnum,id,gathererId,tcgId] as declared by the file, or null. */
	public String[] getDeclared(Object card) {
		return declared.get(card);
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public boolean isOk() {
		return getError() == null && toImport.size() > 0;
	}

	public Location getLocation() {
		return location;
	}

	public void setLocation(Location location) {
		this.location = location;
	}

	public boolean isVirtual() {
		return virtual;
	}

	public void setVirtual(boolean virtual) {
		this.virtual = virtual;
	}

	public boolean isOwnershipFixed() {
		return ownershipFixed;
	}

	public void setOwnershipFixed(boolean ownershipFixed) {
		this.ownershipFixed = ownershipFixed;
	}

	/**
	 * When {@link #isOwnershipFixed()}: gives {@code card} the destination's
	 * ownership. A card the file marked virtual going into a non-virtual list
	 * becomes Own AND Proxy (the user can untick Proxy in the preview); a card
	 * the file marked Own going into a virtual list becomes virtual. Both are
	 * counted for the preview warning.
	 */
	public void applyFixedOwnership(MagicCardPhysical card) {
		if (!ownershipFixed || card == null)
			return;
		boolean own = !virtual;
		if (card.isOwn() == own)
			return;
		if (own) {
			card.setProxy(true);
			forcedOwnProxy += card.getCount();
		} else {
			forcedVirtual += card.getCount();
		}
		card.setOwn(own);
	}

	/** The file gives {@code card} the non-English {@code language}, but it is imported as English. */
	public void declareLanguage(Object card, String language) {
		if (card != null && language != null && !language.trim().isEmpty() && !"English".equalsIgnoreCase(language))
			declaredLanguage.put(card, language);
	}

	/** {@code card} is in its file language after all - stop reporting it. */
	public void clearDeclaredLanguage(Object card) {
		declaredLanguage.remove(card);
	}

	/** The non-English language the file gave {@code card}, or {@code null}. */
	public String getDeclaredLanguage(Object card) {
		return declaredLanguage.get(card);
	}

	/** Cards whose language from the file was replaced by English. */
	public java.util.Collection<Object> getLanguageOverridden() {
		return declaredLanguage.keySet();
	}

	/** Copies the file marked Own that are imported as virtual. */
	public int getForcedVirtual() {
		return forcedVirtual;
	}

	/** Copies the file marked virtual that are imported as Own + Proxy. */
	public int getForcedOwnProxy() {
		return forcedOwnProxy;
	}

	public Map<String, Object> getProperties() {
		return props;
	}

	public <T> T getProperty(String key) {
		return (T) getProperties().get(key);
	}

	public void setProperty(String key, Object value) {
		getProperties().put(key, value);
	}

	public ImportSource getImportSource() {
		return importSource;
	}

	public void setImportSource(ImportSource importSource) {
		this.importSource = importSource;
	}
}
