/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *******************************************************************************/
package com.reflexit.magiccards.ui.views.columns;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.ui.views.lib.DeckColumnCollection;

/**
 * The list of columns of a view: the saved layout ("Name:120,-Set:60,...": order, visibility,
 * width) read back and written out, the order set from the viewer, moving a column to the front,
 * and finding a column by name, field or position. Uses the real columns of a deck tab. JUnit
 * Plug-in Test (the columns read the workbench colors).
 */
public class ColumnCollectionTest {
	private ColumnCollection cc;
	private String[] names;
	/** The columns that can be hidden (the Name column is always shown). */
	private String[] plain;
	private int fixed;

	@Before
	public void setUp() {
		cc = new DeckColumnCollection("test.columns.page");
		names = cc.getColumnNames();
		assertTrue("the test needs a few columns", names.length >= 4);
		List<String> hideable = new ArrayList<>();
		for (AbstractColumn c : cc.getColumns())
			if (!(c instanceof GroupColumn))
				hideable.add(c.getColumnFullName());
		plain = hideable.toArray(new String[0]);
		fixed = names.length - plain.length;
		assertTrue(plain.length >= 4);
	}

	private List<String> orderedNames() {
		List<String> res = new ArrayList<>();
		for (String part : cc.getColumnLayoutProperty().split(",")) {
			String s = part.startsWith("-") ? part.substring(1) : part;
			res.add(s.substring(0, s.lastIndexOf(':')));
		}
		return res;
	}

	@Test
	public void testTheColumnsAreCountedAndNamed() {
		assertEquals(names.length, cc.getColumnsNumber());
		assertEquals(names.length, cc.getColumns().size());
		assertEquals(names.length, cc.getColumnIds().length);
		assertEquals(names[0], cc.getColumn(0).getColumnFullName());
	}

	@Test
	public void testAColumnIsFoundByNameFieldOrPosition() {
		AbstractColumn first = cc.getColumn(0);
		assertSame(first, cc.getColumn(names[0]));
		assertSame(first, cc.getColumn(first.getDataField()));
		assertNull(cc.getColumn("no such column"));
		assertEquals(0, first.getColumnIndex());
		assertEquals(1, cc.getColumn(1).getColumnIndex());
	}

	@Test
	public void testTheSavedLayoutSetsOrderVisibilityAndWidth() {
		cc.updateColumnsFromPropery(plain[2] + ":55,-" + plain[0] + ":70," + plain[1]);
		List<String> order = orderedNames();
		assertEquals(plain[2], order.get(0));
		assertEquals(plain[0], order.get(1));
		assertEquals(plain[1], order.get(2));
		assertTrue(cc.getColumn(plain[2]).isVisible());
		assertFalse(cc.getColumn(plain[0]).isVisible());
		assertTrue(cc.getColumn(plain[1]).isVisible());
		assertEquals(55, cc.getColumn(plain[2]).getUserWidth());
		assertEquals(70, cc.getColumn(plain[0]).getUserWidth());
	}

	@Test
	public void testTheColumnsLeftOutOfTheLayoutAreHidden() {
		cc.updateColumnsFromPropery(plain[1] + "," + plain[0]);
		for (int i = 2; i < plain.length; i++)
			assertFalse(plain[i], cc.getColumn(plain[i]).isVisible());
		assertEquals(2 + fixed, cc.getColumnFields().length);
	}

	@Test
	public void testPlusShowsEveryColumnAndMinusHidesThem() {
		cc.updateColumnsFromPropery("-");
		assertEquals(fixed, cc.getColumnFields().length);
		cc.updateColumnsFromPropery("+");
		assertEquals(names.length, cc.getColumnFields().length);
	}

	@Test
	public void testAnEmptyLayoutChangesNothing() {
		String before = cc.getColumnLayoutProperty();
		cc.updateColumnsFromPropery("");
		assertEquals(before, cc.getColumnLayoutProperty());
	}

	@Test
	public void testAColumnThatNoLongerExistsOrAnEmptyEntryIsSkipped() {
		cc.updateColumnsFromPropery("No Such Column:10,, ," + plain[1] + ":40");
		assertEquals(plain[1], orderedNames().get(0));
		assertEquals(40, cc.getColumn(plain[1]).getUserWidth());
		assertEquals(names.length, cc.getColumnsNumber());
		assertEquals(names.length, orderedNames().size());
	}

	@Test
	public void testABadWidthIsIgnored() {
		cc.updateColumnsFromPropery(plain[1] + ":wide");
		assertEquals(names.length, orderedNames().size());
		assertTrue(orderedNames().contains(plain[1]));
	}

	@Test
	public void testTheLayoutIsWrittenBackAndReadBack() {
		cc.updateColumnsFromPropery(plain[3] + ":33,-" + plain[1] + ":44," + plain[0] + ":22");
		String saved = cc.getColumnLayoutProperty();
		assertTrue(saved, saved.startsWith(plain[3] + ":33,-" + plain[1] + ":44," + plain[0] + ":22,"));
		ColumnCollection other = new DeckColumnCollection("test.columns.other");
		other.updateColumnsFromPropery(saved);
		assertEquals(saved, other.getColumnLayoutProperty());
	}

	@Test
	public void testTheSelectedFieldsFollowTheLayout() {
		ICardField[] fields = cc.getSelectedColumnFields(plain[2] + "," + plain[0]);
		assertEquals(2 + fixed, fields.length);
		assertTrue(java.util.Arrays.asList(fields).contains(cc.getColumn(plain[2]).getDataField()));
		assertTrue(java.util.Arrays.asList(fields).contains(cc.getColumn(plain[0]).getDataField()));
	}

	@Test
	public void testTheOrderCanBeSetFromPositions() {
		String third = cc.getColumn(2).getColumnFullName();
		String first = cc.getColumn(0).getColumnFullName();
		cc.setColumnOrder(new int[] { 2, 0, 9999 });
		List<String> order = orderedNames();
		assertEquals(third, order.get(0));
		assertEquals(first, order.get(1));
		assertEquals(names.length, order.size());
		assertArrayEquals(new int[] { 2, 0 }, new int[] { cc.getColumnsOrder()[0], cc.getColumnsOrder()[1] });
		assertEquals(names.length, cc.getColumnsOrder().length);
		assertEquals("the others follow, hidden (but the always-shown Name)", 2 + fixed - hiddenAmongFirstTwo(first, third),
				cc.getColumnFields().length);
	}

	private int hiddenAmongFirstTwo(String a, String b) {
		int n = 0;
		if (cc.getColumn(a) instanceof GroupColumn)
			n++;
		if (cc.getColumn(b) instanceof GroupColumn)
			n++;
		return n;
	}

	@Test
	public void testAColumnCanBeMovedToTheFront() {
		AbstractColumn third = cc.getColumn(plain[2]);
		cc.moveColumnOnTop(third);
		List<String> order = orderedNames();
		assertEquals(plain[2], order.get(0));
		assertEquals(plain[0], order.get(1));
		assertEquals(names.length, order.size());
		assertNotNull(cc.getColumn(plain[2]));
	}

	@Test
	public void testTheIdIsThePreferencePageId() {
		assertEquals("test.columns.page", new DeckColumnCollection("test.columns.page").getId());
	}
}
