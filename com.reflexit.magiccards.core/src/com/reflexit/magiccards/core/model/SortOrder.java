/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.model;

import java.util.Arrays;
import java.util.Comparator;

import com.reflexit.magiccards.core.model.abs.ICardField;

@SuppressWarnings("serial")
public class SortOrder implements Comparator {
	private static final int MAX = 9;
	private static final int MIN = 2;
	private final MagicCardComparator order[] = new MagicCardComparator[MAX];
	private int curSize;

	public SortOrder() {
		// Always-present tie-breakers, lowest priority last. compare() walks
		// order[] from high index to low, so order[0] is the very last word:
		// ID only separates two entries that are otherwise identical *including
		// name*. NAME must outrank ID - otherwise a loose card (unique id) gets
		// ordered against sibling name groups (id "*") or other loose cards by
		// id instead of name, which is both non-transitive and visibly wrong.
		order[0] = (new MagicCardComparator(MagicCardField.ID, true));
		order[1] = (new MagicCardComparator(MagicCardField.NAME, true));
		curSize = MIN;
	}

	@Override
	public int compare(Object o1, Object o2) {
		if (o1 == o2)
			return 0; // this is only case it is 0

		for (int i = curSize - 1; i >= 0; i--) {
			MagicCardComparator elem = order[i];
			int d = elem.compare(o1, o2);
			if (d != 0)
				return d; // no "dir" since comparator has it already
		}
		int dir = isAccending() ? 1 : -1;
		// Every sort field (incl. NAME and ID) tied. Only now keep a CardGroup
		// and a loose card of the same name in a stable, deterministic order -
		// never earlier, or the loose card jumps past unrelated groups.
		if (o1 != null && o2 != null && o1.getClass() != o2.getClass())
			return dir * o1.getClass().getName().compareTo(o2.getClass().getName());
		// Integer.compare, NOT subtraction: identity hashes span the whole int
		// range, so (hashA - hashB) overflows and flips sign -> compare(a,b) and
		// compare(b,a) can both be > 0 -> "Comparison method violates its general
		// contract" from TimSort (hit hard by collections full of duplicate cards
		// that tie on every real field).
		return dir * Integer.compare(System.identityHashCode(o1), System.identityHashCode(o2));
	}

	public Comparator getComparator() {
		return this;
	}

	public synchronized void setFrom(SortOrder other) {
		curSize = other.curSize;
		for (int i = MIN; i < MAX; i++) {
			order[i] = other.order[i];
		}
	}

	public synchronized void setSortField(ICardField sortField, boolean accending) {
		MagicCardComparator elem = new MagicCardComparator(sortField, accending);
		for (int i = MIN; i < curSize; i++) {
			if (elem.equals(order[i])) {
				remove(i);
				break;
			}
		}
		while (curSize >= MAX) {
			remove(MIN);
		}
		add(elem);
	}

	public synchronized MagicCardComparator getComparator(ICardField sortField) {
		int size = curSize;
		for (int i = MIN; i < size; i++) {
			MagicCardComparator elem = order[i];
			if (elem != null && sortField.equals(elem.getField())) {
				return elem;
			}
		}
		return null;
	}

	public synchronized int getPosition(ICardField sortField) {
		int size = curSize;
		for (int i = MIN; i < size; i++) {
			MagicCardComparator elem = order[i];
			if (sortField.equals(elem.getField())) {
				return size - i;
			}
		}
		return -1;
	}

	public boolean hasSortField(ICardField sortField) {
		return getComparator(sortField) != null;
	}

	public boolean isAccending(ICardField sortField) {
		MagicCardComparator comparator = getComparator(sortField);
		if (comparator != null)
			return comparator.isAccending();
		return false; // default to false
	}

	public boolean isAccending() {
		return peek().isAccending();
	}

	public boolean isTop(ICardField sortField) {
		MagicCardComparator elem = peek();
		return elem.getField().equals(sortField);
	}

	public synchronized int size() {
		return curSize;
	}

	public synchronized MagicCardComparator peek() {
		return order[curSize - 1];
	}

	private synchronized boolean add(MagicCardComparator e) {
		order[curSize] = e;
		curSize++;
		return true;
	}

	public synchronized boolean isEmpty() {
		return curSize <= MIN;
	}

	public synchronized void clear() {
		for (; curSize > MIN; curSize--) {
			order[curSize - 1] = null;
		}
	}

	private synchronized MagicCardComparator remove(int index) {
		if (index < MIN)
			return null;
		MagicCardComparator c = order[index];
		for (; index < curSize - 1; index++) {
			order[index] = order[index + 1];
		}
		order[index] = null;
		curSize--;
		return c;
	}

	@Override
	public String toString() {
		return getStringValue();
	}

	@Override
	public synchronized int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + curSize;
		result = prime * result + Arrays.hashCode(order);
		return result;
	}

	@Override
	public synchronized boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof SortOrder))
			return false;
		SortOrder other = (SortOrder) obj;
		if (curSize != other.curSize)
			return false;
		if (!Arrays.equals(order, other.order))
			return false;
		if (isAccending() != other.isAccending())
			return false;
		return true;
	}

	public synchronized String getStringValue() {
		String res = "";
		for (int index = curSize - 1; index >= MIN; index--) {
			res += order[index] + "/";
		}
		return res;
	}

	public static SortOrder valueOf(String value) {
		SortOrder res = new SortOrder();
		if (value == null || value.trim().isEmpty())
			return res;
		String elems[] = value.split("/");
		for (int i = elems.length - 1; i >= 0; i--) {
			String string = elems[i].trim();
			if (string.isEmpty())
				continue;
			if (string.endsWith("^")) {
				res.setSortField(MagicCardField.valueOf(string.substring(0, string.length() - 1)), true);
			} else if (string.endsWith("v")) {
				res.setSortField(MagicCardField.valueOf(string.substring(0, string.length() - 1)), false);
			} else {
				res.setSortField(MagicCardField.valueOf(string), true);
			}
		}
		return res;
	}
}
