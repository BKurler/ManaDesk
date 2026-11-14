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
package com.reflexit.magiccards.ui.view.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreePath;
import org.junit.Test;

import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.storage.MemoryFilteredCardStore;
import com.reflexit.magiccards.ui.views.model.ExpandContentProvider;
import com.reflexit.magiccards.ui.views.model.GroupExpandContentProvider;
import com.reflexit.magiccards.ui.views.model.TableViewerContentProvider;

/**
 * What the flat list, the "expanded" list and the group-expanding tree show for cards, groups,
 * lists and a card store, how a selection is turned into positions and into what a viewer
 * selects. Plain objects only (no viewer is created), so it works as plain JUnit.
 */
public class ContentProvidersTest {
	private static MagicCard card(int id, String name) {
		MagicCard c = new MagicCard();
		c.setCardId(id);
		c.setName(name);
		return c;
	}

	private static CardGroup group(MagicCardField field, String name, Object... children) {
		CardGroup g = new CardGroup(field, name);
		for (Object o : children)
			g.add((com.reflexit.magiccards.core.model.abs.ICard) o);
		return g;
	}

	private static MemoryFilteredCardStore<com.reflexit.magiccards.core.model.abs.ICard> store(MagicCard... cards) {
		MemoryFilteredCardStore<com.reflexit.magiccards.core.model.abs.ICard> s = new MemoryFilteredCardStore<>();
		for (MagicCard c : cards)
			s.add(c);
		s.update();
		return s;
	}

	// ---- the flat table list

	@Test
	public void testTableElementsOfACollectionOrAGroupOrAStore() {
		MagicCard a = card(1, "a");
		MagicCard b = card(2, "b");
		TableViewerContentProvider<Object> p = new TableViewerContentProvider<>();
		assertArrayEquals(new Object[] { a, b }, p.getElements(Arrays.asList(a, b)));
		assertEquals(2, p.getElements(group(null, "g", a, b)).length);
		assertEquals(0, p.getElements("something else").length);
		assertEquals(0, p.getElements(null).length);
		assertEquals(2, p.getElements(store(a, b)).length);
	}

	@Test
	public void testTheInputIsRememberedAndForgotten() {
		TableViewerContentProvider<Object> p = new TableViewerContentProvider<>();
		List<Object> input = new ArrayList<>();
		p.inputChanged(null, null, input);
		assertSame(input, p.getInput());
		p.dispose();
		assertEquals(null, p.getInput());
		assertEquals(null, p.getParent("x"));
	}

	@Test
	public void testIndexOfFindsAnElementOrTheGroupHoldingIt() {
		MagicCard a = card(1, "a");
		MagicCard b = card(2, "b");
		MagicCard c = card(3, "c");
		CardGroup g = group(null, "g", b);
		TableViewerContentProvider<Object> p = new TableViewerContentProvider<>();
		Object[] elements = new Object[] { a, g };
		assertEquals(0, p.indexOf(a, elements));
		assertEquals("the group itself", 1, p.indexOf(g, elements));
		assertEquals("a card inside a group gives the group's position", 1, p.indexOf(b, elements));
		assertEquals(-1, p.indexOf(c, elements));
	}

	@Test
	public void testIndicesOfASelection() {
		MagicCard a = card(1, "a");
		MagicCard b = card(2, "b");
		TableViewerContentProvider<Object> p = new TableViewerContentProvider<>();
		p.inputChanged(null, null, Arrays.asList(a, b));
		assertEquals(0, p.getIndices(StructuredSelection.EMPTY).length);
		assertArrayEquals(new int[] { 1, 0 }, p.getIndices(new StructuredSelection(new Object[] { b, a })));
		assertArrayEquals(new int[] { -1 }, p.getIndices(new StructuredSelection(card(9, "z"))));
	}

	@Test
	public void testASelectionIsTranslatedToTheListsOwnElements() {
		MagicCard a = card(1, "a");
		MagicCard other = card(2, "b");
		TableViewerContentProvider<Object> p = new TableViewerContentProvider<>();
		p.inputChanged(null, null, Arrays.asList(a));
		assertTrue(p.translateSelection(StructuredSelection.EMPTY, 0).isEmpty());
		IStructuredSelection res = p.translateSelection(
				new StructuredSelection(new Object[] { new TreePath(new Object[] { a }), other }), 0);
		assertEquals(2, res.size());
		assertSame(a, res.toArray()[0]);
		assertSame("not in the list: kept as it is", other, res.toArray()[1]);
	}

	// ---- the expanded list

	@Test
	public void testExpandedListFlattensGroups() {
		MagicCard a = card(1, "a");
		MagicCard b = card(2, "b");
		MagicCard c = card(3, "c");
		CardGroup inner = group(null, "inner", b, c);
		CardGroup top = group(null, "top", a, inner);
		ExpandContentProvider p = new ExpandContentProvider(false);
		assertArrayEquals(new Object[] { a, b, c }, p.getFlatChildren(top));
		assertArrayEquals(new Object[] { a, b, c }, p.getFlatChildren(Arrays.asList(a, inner)));
	}

	@Test
	public void testExpandedListOfAStoreOrArrays() {
		MagicCard a = card(1, "a");
		MagicCard b = card(2, "b");
		ExpandContentProvider p = new ExpandContentProvider(false);
		assertEquals(2, p.getFlatChildren(store(a, b)).length);
		Object[] array = new Object[] { a };
		assertSame(array, p.getFlatChildren(array));
		assertEquals(0, p.getFlatChildren("text").length);
		assertEquals(0, p.getFlatChildren(null).length);
	}

	@Test
	public void testAGroupByNameIsAStopUnlessLeavesAreExpanded() {
		MagicCard a = card(1, "a");
		CardGroup byName = group(MagicCardField.NAME, "a", a);
		CardGroup top = group(null, "top", byName);
		ExpandContentProvider keep = new ExpandContentProvider(false);
		ExpandContentProvider expand = new ExpandContentProvider(true);
		assertEquals(1, keep.getFlatChildren(top).length);
		assertEquals(1, expand.getFlatChildren(top).length);
	}

	@Test
	public void testTheExpandedListIsTheTopOfTheInput() {
		MagicCard a = card(1, "a");
		MagicCard b = card(2, "b");
		List<Object> input = Arrays.<Object> asList(a, b);
		ExpandContentProvider p = new ExpandContentProvider(true);
		p.inputChanged(null, null, input);
		assertEquals(2, p.getSize(input));
		assertArrayEquals(new Object[] { a, b }, p.getChildren(input));
		assertArrayEquals(new Object[] { a, b }, p.getElements(input));
		assertEquals("only the input has children", 0, p.getSize(a));
		assertEquals(0, p.getChildren(a).length);
		assertEquals(null, p.getParent(a));
		p.dispose();
	}

	// ---- the group-expanding tree

	@Test
	public void testTheRootHasOneChildAndItHoldsTheCards() {
		MagicCard a = card(1, "a");
		MagicCard b = card(2, "b");
		List<Object> input = Arrays.<Object> asList(a, b);
		GroupExpandContentProvider p = new GroupExpandContentProvider();
		p.inputChanged(null, null, input);
		Object[] top = p.getChildren(input);
		assertEquals(1, top.length);
		assertEquals("Cards", top[0]);
		assertArrayEquals(new Object[] { a, b }, p.getChildren(top[0]));
		assertSame(input, p.getParent(top[0]));
		assertEquals(null, p.getParent(input));
		assertSame(top[0], p.getParent(a));
		assertTrue(p.hasChildren(input));
	}

	@Test
	public void testGroupsAreFlattenedInTheTree() {
		MagicCard a = card(1, "a");
		MagicCard b = card(2, "b");
		CardGroup top = group(null, "top", a, group(null, "inner", b));
		GroupExpandContentProvider p = new GroupExpandContentProvider();
		assertArrayEquals(new Object[] { a, b }, p.calculateChildren(top));
		assertEquals(0, p.calculateChildren(a).length);
		assertEquals(0, p.calculateChildren("text").length);
		assertEquals(2, p.calculateChildren(store(a, b)).length);
		Object[] array = new Object[] { a };
		assertSame(array, p.calculateChildren(array));
	}

	@Test
	public void testSizesAndChildrenOfWhatTheTreeIsGiven() {
		MagicCard a = card(1, "a");
		GroupExpandContentProvider p = new GroupExpandContentProvider();
		CardGroup g = group(null, "g", a);
		assertEquals(1, p.getSize(g));
		assertTrue(p.hasChildren(g));
		assertEquals(0, p.getSize(a));
		assertFalse(p.hasChildren(a));
		assertEquals(1, p.getSize(Arrays.asList(a)));
		assertTrue(p.hasChildren(Arrays.asList(a)));
		assertFalse(p.hasChildren(new ArrayList<Object>()));
		assertEquals(1, p.getSize(new Object[] { a }));
		assertTrue(p.hasChildren(new Object[] { a }));
		assertFalse(p.hasChildren(new Object[0]));
		assertEquals(1, p.getSize(store(a)));
		assertTrue(p.hasChildren(store(a)));
		assertEquals(0, p.getSize("text"));
		assertFalse(p.hasChildren("text"));
		assertFalse(p.hasChildren(group(null, "empty")));
	}

	@Test
	public void testATreeSelectionIsTranslatedToItsPaths() {
		MagicCard a = card(1, "a");
		List<Object> input = Arrays.<Object> asList(a);
		GroupExpandContentProvider p = new GroupExpandContentProvider();
		assertTrue("nothing to translate", p.translateSelection(StructuredSelection.EMPTY, 0).isEmpty());
		IStructuredSelection withoutInput = new StructuredSelection(a);
		assertSame("no input yet: unchanged", withoutInput, p.translateSelection(withoutInput, 0));
		p.inputChanged(null, null, input);
		Object[] top = p.getChildren(input);
		IStructuredSelection res = p.translateSelection(new StructuredSelection(new Object[] { input, top[0], a }), 0);
		assertEquals(3, res.size());
		Object[] paths = res.toArray();
		assertEquals(0, ((TreePath) paths[0]).getSegmentCount());
		assertEquals(1, ((TreePath) paths[1]).getSegmentCount());
		TreePath cardPath = (TreePath) paths[2];
		assertEquals(2, cardPath.getSegmentCount());
		assertSame(a, cardPath.getLastSegment());
	}

	@Test
	public void testAGroupSelectionStaysAsItIsInTheTree() {
		MagicCard a = card(1, "a");
		List<Object> input = Arrays.<Object> asList(a);
		GroupExpandContentProvider p = new GroupExpandContentProvider();
		p.inputChanged(null, null, input);
		CardGroup g = group(null, "g", a);
		IStructuredSelection res = p.translateSelection(new StructuredSelection(new TreePath(new Object[] { g })), 0);
		assertSame(g, res.getFirstElement());
	}
}
