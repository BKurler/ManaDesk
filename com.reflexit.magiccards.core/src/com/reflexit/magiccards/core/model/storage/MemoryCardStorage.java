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
 */

package com.reflexit.magiccards.core.model.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.reflexit.magiccards.core.model.Location;


public class MemoryCardStorage<T> extends AbstractStorage<T> {
	protected List<T> list;
	private Location location = Location.NO_WHERE;

	/**
	 * creates empty card store
	 */
	public MemoryCardStorage() {
		super();
		this.list = Collections.synchronizedList(new ArrayList<T>());
	}

	@Override
	public Iterator<T> iterator() {
		synchronized (list) {
			ArrayList x = new ArrayList(list);
			return x.iterator();
		}
	}

	@Override
	public int size() {
		return this.getList().size();
	}

	@Override
	public boolean doRemoveCard(T card) {
		// Remove by identity first: MagicCardPhysical.equals() is value based, so
		// List.remove(Object) would happily delete a *different* copy that has the
		// same name / set / count (which reorders the rest of the collection and
		// leaves the intended card behind). Only fall back to equals when the
		// exact instance is not in the list.
		List<T> l = getList();
		synchronized (l) {
			for (int i = 0; i < l.size(); i++) {
				if (l.get(i) == card) {
					l.remove(i);
					return true;
				}
			}
			return l.remove(card);
		}
	}

	@Override
	public boolean doAddCard(T card) {
		return this.getList().add(card);
	}

	protected boolean doUpdate(@SuppressWarnings("unused") T card) {
		return true;
	}

	/**
	 * @return the list
	 */
	public List<T> getList() {
		return this.list;
	}

	protected void doSetList(List<T> list) {
		this.list = Collections.synchronizedList(list);
	}

	@Override
	public void clearCache() {
		list.clear();
		setLoaded(false);
	}

	@Override
	protected void doLoad() {
		// nothing
	}

	/**
	 * @throws IOException
	 */
	@Override
	protected void doSave() throws IOException {
		// nothing
	}

	@Override
	public Location getLocation() {
		return location;
	}

	@Override
	public void setLocation(Location location) {
		this.location = location;
	}

	@Override
	public String getComment() {
		return null;
	}

	@Override
	public String getName() {
		return location.getName();
	}

	@Override
	public boolean isVirtual() {
		return true;
	}

	@Override
	public boolean isUnsorted() {
		return false;
	}

	@Override
	public String toString() {
		return list.toString();
	}
}