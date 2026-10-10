/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *    Rémi Dutil (2026) - Side (DECK / COLLECTION) helpers: sideOf / containerFor
 *    Rémi Dutil (2026) - move(): carry the sideboard AND the extra list along
 *    Rémi Dutil (2026) - the main collection is now Collections/Main.xml
 *                        (shown as "Main"); an existing Collections/main.xml
 *                        is renamed once, before it is opened
 *                        with the main deck (was sideboard-only)
 *******************************************************************************/
package com.reflexit.magiccards.core.model.nav;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.Location;

/**
 * Model Root contains access to all deck, collections and magic db container
 * (displayed in the navigator)
 *
 * @author Alena
 *
 */
public class ModelRoot extends CardOrganizer {
	private CollectionsContainer fDecks;
	private MagicDbContainter db;
	private CollectionsContainer fLib;
	private CardCollection fLibFile;
	private CollectionsContainer fMyCards;
	private File rootDir;

	/**
	 * @param name
	 * @param parent2
	 */
	private ModelRoot(File dir) {
		super("Root", null);
		resetRoot(dir);
	}

	public void resetRoot(File dir) {
		CardOrganizer root = this;
		rootDir = dir;
		removeChildren();
		this.fMyCards = new CollectionsContainer(root.getPath(), root) {
			@Override
			public String getLabel() {
				return "My Cards";
			}
		};
		this.fLib = new CollectionsContainer("Collections", fMyCards);
		this.fDecks = new CollectionsContainer("Decks", fMyCards);
		this.db = new MagicDbContainter(root);
		this.fLibFile = new CardCollection(prepareMainCollectionFile(), this.fLib);
		refresh();
	}

	public void refresh() {
		this.fMyCards.loadChildren();
		// this.fLib.loadChildren();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.reflexit.magiccards.core.model.nav.CardElement#getPath()
	 */
	@Override
	public LocationPath getPath() {
		return LocationPath.ROOT;
	}

	@Override
	public boolean isRoot() {
		return true;
	}

	/** Which half of the navigator tree an element belongs to. */
	public enum Side {
		DECK, COLLECTION
	}

	/**
	 * {@link Side#DECK} when {@code el} is the Decks container or anything under
	 * it, {@link Side#COLLECTION} for the Collections container and its subtree,
	 * {@code null} for anything else (the card database, the bare root).
	 */
	public Side sideOf(CardElement el) {
		if (el == null)
			return null;
		if (el == this.fDecks || el.isAncestor(this.fDecks))
			return Side.DECK;
		if (el == this.fLib || el.isAncestor(this.fLib))
			return Side.COLLECTION;
		return null;
	}

	/** The root container ("Decks" / "Collections") for a given side. */
	public CollectionsContainer containerFor(Side side) {
		return side == Side.DECK ? this.fDecks : this.fLib;
	}

	public CollectionsContainer getDeckContainer() {
		return this.fDecks;
	}

	public MagicDbContainter getMagicDBContainer() {
		return this.db;
	}

	public CollectionsContainer getCollectionsContainer() {
		return this.fLib;
	}

	public CollectionsContainer getMyCardsContainer() {
		return this.fMyCards;
	}

	/**
	 * @return
	 */
	public static ModelRoot getInstance(File dir) {
		return new ModelRoot(dir);
	}

	/**
	 * @param temp
	 *
	 */
	public void clear() {
		getDeckContainer().removeChildren();
		getCollectionsContainer().removeChildren();
		this.fLibFile = new CardCollection(prepareMainCollectionFile(), this.fLib);
	}

	/** The main collection's file name, under Collections. */
	public static final String MAIN_COLLECTION_FILE = "Main.xml";
	private static final String OLD_MAIN_COLLECTION_FILE = "main.xml";

	/**
	 * Renames an existing Collections/main.xml (the old name) to
	 * {@link #MAIN_COLLECTION_FILE}. Goes through a temporary name because on
	 * Windows a rename that only changes the case is a no-op. Cards inside get
	 * their location from the file they are loaded from, so nothing else needs
	 * to change. Returns the file name to use.
	 */
	private String prepareMainCollectionFile() {
		try {
			File dir = this.fLib.getFile();
			String[] names = dir == null ? null : dir.list();
			if (names == null)
				return MAIN_COLLECTION_FILE;
			boolean hasOld = false;
			for (String n : names) {
				if (n.equals(MAIN_COLLECTION_FILE))
					return MAIN_COLLECTION_FILE; // already migrated
				if (n.equals(OLD_MAIN_COLLECTION_FILE))
					hasOld = true;
			}
			if (hasOld) {
				File old = new File(dir, OLD_MAIN_COLLECTION_FILE);
				File tmp = new File(dir, OLD_MAIN_COLLECTION_FILE + ".renaming");
				if (old.renameTo(tmp) && !tmp.renameTo(new File(dir, MAIN_COLLECTION_FILE))) {
					tmp.renameTo(old); // put it back - keep using the old name
					return OLD_MAIN_COLLECTION_FILE;
				}
				if (!new File(dir, MAIN_COLLECTION_FILE).exists())
					return OLD_MAIN_COLLECTION_FILE;
			}
		} catch (RuntimeException e) {
			// fall through to the new name
		}
		return MAIN_COLLECTION_FILE;
	}

	/**
	 * @return
	 */
	public CardCollection getDefaultLib() {
		return this.fLibFile;
	}

	/**
	 * @return map from string location to tree element
	 */
	public Map<Location, CardElement> getLocationsMap() {
		LinkedHashMap<Location, CardElement> map = new LinkedHashMap<Location, CardElement>();
		fillLocations(map, this);
		return map;
	}

	public CardElement getCardElement(Location location) {
		return getLocationsMap().get(location);
	}

	/**
	 * @param map
	 * @param modelRoot
	 */
	private void fillLocations(LinkedHashMap<Location, CardElement> map, CardElement root) {
		if (root instanceof CardCollection) {
			map.put(root.getLocation(), root);
		}
		if (root instanceof CardOrganizer) {
			CardOrganizer org = (CardOrganizer) root;
			if (org.getChildren() != null) {
				for (Object element : org.getChildren()) {
					CardElement el = (CardElement) element;
					fillLocations(map, el);
				}
			}
			map.put(root.getLocation(), root);
		}
	}

	public void move(CardElement[] elements, CardOrganizer newParent) {
		if (newParent instanceof MagicDbContainter)
			throw new MagicException("Cannot move to db");
		if (newParent == getMyCardsContainer())
			throw new MagicException("Cannot move into My Cards folder");
		ArrayList<CardElement> norm = new ArrayList<CardElement>();
		for (CardElement el : elements) {
			if (el == null)
				continue;
			if (norm.contains(el.getParent()))
				continue;
			if (isAlreadyCovered(norm, el))
				continue;
			norm.add(el);
		}
		for (CardElement no : norm) {
			// the sideboard AND the extra list (whichever exist) always follow the
			// main deck/collection they belong to - look them up BEFORE reparenting
			// "no" itself, since getRelatedElements() searches its (current) parent
			java.util.List<CardElement> related = no.getRelatedElements();
			no.newParent(newParent);
			for (CardElement r : related)
				r.newParent(newParent);
		}
		// System.err.println("drop to " + newParent);
	}

	/** True when {@code el} is the sideboard/extra (or main deck) of something
	 *  already queued in {@code norm} - it will be carried along, so it must not
	 *  also be queued (and moved) on its own. */
	private static boolean isAlreadyCovered(ArrayList<CardElement> norm, CardElement el) {
		for (CardElement related : el.getRelatedElements())
			if (norm.contains(related))
				return true;
		return false;
	}

	@Override
	public File getRootDir() {
		if (rootDir == null)
			throw new NullPointerException();
		return rootDir;
	}
}
