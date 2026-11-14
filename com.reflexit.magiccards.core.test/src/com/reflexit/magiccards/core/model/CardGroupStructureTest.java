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
package com.reflexit.magiccards.core.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.model.abs.ICard;

/**
 * The structure side of {@link CardGroup}: building groups and sub-groups, walking and
 * removing through the iterator, naming the group of a card for each grouping field,
 * equality (name and parent), depth, recursive count, and the operations a group refuses.
 * The aggregated values are in {@link CardGroupTest}. Pure logic; works as plain JUnit.
 */
public class CardGroupStructureTest extends TestCase {

	private static MagicCard card(String name, String type, String cost) {
		MagicCard c = new MagicCard();
		c.setName(name);
		c.setType(type);
		c.setCost(cost);
		return c;
	}

	private static MagicCardPhysical owned(String name, int count) {
		MagicCardPhysical p = new MagicCardPhysical(card(name, "Instant", "{R}"), Location.NO_WHERE);
		p.setCount(count);
		return p;
	}

	// ---- building

	public void testNullNameIsRefused() {
		try {
			new CardGroup(MagicCardField.NAME, null);
			fail();
		} catch (NullPointerException e) {
			// expected
		}
	}

	public void testBasics() {
		CardGroup g = new CardGroup(MagicCardField.SET, "Alpha");
		assertEquals("Alpha", g.getName());
		assertEquals("Alpha", g.toString());
		assertSame(MagicCardField.SET, g.getFieldIndex());
		assertNull(g.getBase());
		assertEquals(0, g.getOwnTotalAll());
		assertEquals(0, g.size());
		assertEquals(0, g.getChildren().length);
		assertTrue(g.getChildrenList().isEmpty());
		assertNull(g.getChildAtIndex(0));
		assertNull(g.getFirstCard());
		assertFalse(g.iterator().hasNext());
		assertNull(g.getParent());
		assertEquals(1, g.depth());
	}

	public void testAddKeepsOrderAndAddAllIgnoresNull() {
		CardGroup g = new CardGroup(MagicCardField.SET, "S");
		MagicCard a = card("A", "Instant", "{R}");
		MagicCard b = card("B", "Instant", "{R}");
		g.add(a);
		g.addAll(Arrays.asList(b));
		g.addAll(null);
		assertEquals(2, g.size());
		assertSame(a, g.getChildAtIndex(0));
		assertSame(b, g.getChildAtIndex(1));
		assertNull("past the end", g.getChildAtIndex(2));
		assertEquals(2, g.getChildrenList().size());
	}

	public void testSubGroups() {
		CardGroup root = new CardGroup(MagicCardField.NAME, "root");
		assertNull(root.getSubGroup("x"));
		root.addToSubGroup("Bolt", card("Bolt", "Instant", "{R}"));
		root.addToSubGroup("Bolt", card("Bolt", "Instant", "{R}"));
		root.addToSubGroup("Bear", card("Bear", "Creature - Bear", "{1}{G}"));
		assertEquals("one group per name", 2, root.getSubGroups().size());
		assertEquals(2, root.getSubGroup("Bolt").size());
		assertEquals(1, root.getSubGroup("Bear").size());
		assertSame(root, root.getSubGroup("Bolt").getParent());
		assertEquals(2, root.getSubGroup("Bolt").depth());
		assertEquals("a group of the same field", MagicCardField.NAME, root.getSubGroup("Bolt").getFieldIndex());
	}

	public void testDepthOfNestedGroups() {
		CardGroup a = new CardGroup(MagicCardField.TYPE, "a");
		CardGroup b = new CardGroup(MagicCardField.TYPE, "b");
		CardGroup c = new CardGroup(MagicCardField.TYPE, "c");
		a.add(b);
		b.add(c);
		assertEquals(1, a.depth());
		assertEquals(2, b.depth());
		assertEquals(3, c.depth());
		assertSame(b, c.getParent());
	}

	// ---- removing and clearing

	public void testRemoveUsesIdentityAndForgetsSubGroups() {
		CardGroup root = new CardGroup(MagicCardField.TYPE, "root");
		MagicCard a = card("A", "Instant", "{R}");
		MagicCard sameAsA = card("A", "Instant", "{R}");
		root.add(a);
		root.add(sameAsA);
		root.remove(sameAsA);
		assertEquals(1, root.size());
		assertSame(a, root.getChildAtIndex(0));
		CardGroup sub = new CardGroup(MagicCardField.TYPE, "sub");
		root.add(sub);
		assertSame(sub, root.getSubGroup("sub"));
		root.remove(sub);
		assertNull(root.getSubGroup("sub"));
		root.remove(card("Not There", "Instant", "{R}")); // no effect
		assertEquals(1, root.size());
	}

	public void testClearAndRemoveAll() {
		CardGroup g = new CardGroup(MagicCardField.TYPE, "g");
		g.addToSubGroup("x", card("A", "Instant", "{R}"));
		g.add(card("B", "Instant", "{R}"));
		assertEquals(2, g.size());
		g.clear();
		assertEquals(0, g.size());
		assertNull(g.getSubGroup("x"));
		g.addToSubGroup("y", card("C", "Instant", "{R}"));
		g.removeAll();
		assertEquals(0, g.size());
		assertTrue(g.getSubGroups().isEmpty());
	}

	// ---- iterating

	public void testIteratorWalksAndFailsPastTheEnd() {
		CardGroup g = new CardGroup(MagicCardField.TYPE, "g");
		g.add(card("A", "Instant", "{R}"));
		Iterator<ICard> it = g.iterator();
		assertTrue(it.hasNext());
		assertNotNull(it.next());
		assertFalse(it.hasNext());
		try {
			it.next();
			fail();
		} catch (NoSuchElementException e) {
			// expected
		}
	}

	public void testIteratorRemoveRemovesFromTheGroup() {
		CardGroup g = new CardGroup(MagicCardField.TYPE, "g");
		MagicCard a = card("A", "Instant", "{R}");
		MagicCard b = card("B", "Instant", "{R}");
		g.add(a);
		g.add(b);
		Iterator<ICard> it = g.iterator();
		it.next();
		it.remove();
		assertEquals(1, g.size());
		assertSame(b, g.getChildAtIndex(0));
	}

	// ---- looking inside

	public void testFirstCardGoesDownIntoSubGroups() {
		CardGroup root = new CardGroup(MagicCardField.TYPE, "root");
		MagicCard a = card("A", "Instant", "{R}");
		root.addToSubGroup("sub", a);
		assertSame(a, root.getFirstCard());
	}

	public void testContainsLooksInSubGroupsAndByEquality() {
		CardGroup root = new CardGroup(MagicCardField.TYPE, "root");
		MagicCard a = card("A", "Instant", "{R}");
		a.setCardId("-501");
		root.addToSubGroup("sub", a);
		assertTrue(root.contains(a));
		MagicCard copy = a.cloneCard();
		assertTrue("an equal card counts", root.contains(copy));
		assertFalse(root.contains(card("Other", "Instant", "{R}")));
	}

	public void testExpandListsTheCardsOnly() {
		CardGroup root = new CardGroup(MagicCardField.TYPE, "root");
		MagicCard a = card("A", "Instant", "{R}");
		MagicCard b = card("B", "Creature - Bear", "{G}");
		root.addToSubGroup("one", a);
		root.addToSubGroup("two", b);
		root.add(card("C", "Land", ""));
		Collection<IMagicCard> all = root.expand();
		assertEquals(3, all.size());
		assertTrue(all.contains(a));
		assertTrue(all.contains(b));
		for (Object o : all)
			assertFalse(o instanceof CardGroup);
	}

	// ---- counts

	public void testRecursiveCountAddsTheCopiesOfEveryCard() {
		CardGroup root = new CardGroup(MagicCardField.TYPE, "root");
		root.addToSubGroup("one", owned("A", 3));
		root.addToSubGroup("one", owned("B", 2));
		root.addToSubGroup("two", owned("C", 4));
		root.add(owned("D", 1));
		root.add(card("plain card", "Instant", "{R}")); // a card that is not an instance counts for one
		assertEquals(3 + 2 + 4 + 1 + 1, root.getRecursiveCount());
		assertEquals(0, new CardGroup(MagicCardField.TYPE, "empty").getRecursiveCount());
	}

	// ---- equality

	public void testEqualityIsByNameAndParent() {
		CardGroup p1 = new CardGroup(MagicCardField.TYPE, "p");
		CardGroup p2 = new CardGroup(MagicCardField.TYPE, "p");
		CardGroup other = new CardGroup(MagicCardField.TYPE, "q");
		assertEquals(p1, p2);
		assertEquals(p1.hashCode(), p2.hashCode());
		assertFalse(p1.equals(other));
		assertFalse(p1.equals(null));
		assertFalse(p1.equals("p"));
		CardGroup child1 = new CardGroup(MagicCardField.TYPE, "c");
		CardGroup child2 = new CardGroup(MagicCardField.TYPE, "c");
		p1.add(child1);
		assertFalse("same name under a different parent", child1.equals(child2));
		p2.add(child2);
		assertEquals("same name under equal parents", child1, child2);
	}

	// ---- what a group is not

	public void testGroupsCannotBeClonedOrMoved() throws Exception {
		CardGroup g = new CardGroup(MagicCardField.TYPE, "g");
		try {
			g.clone();
			fail();
		} catch (CloneNotSupportedException e) {
			// expected
		}
		try {
			g.cloneCard();
			fail();
		} catch (UnsupportedOperationException e) {
			// expected
		}
		try {
			g.setLocation(Location.NO_WHERE);
			fail();
		} catch (UnsupportedOperationException e) {
			// expected
		}
	}

	public void testTransientGroups() {
		CardGroup single = new CardGroup(MagicCardField.TYPE, "single");
		assertFalse("an empty group", single.isTransient());
		single.add(card("A", "Instant", "{R}"));
		assertTrue("a group of one card", single.isTransient());
		single.add(card("B", "Instant", "{R}"));
		assertFalse(single.isTransient());
		CardGroup byName = new CardGroup(MagicCardField.NAME, "Bolt");
		byName.add(card("Bolt", "Instant", "{R}"));
		byName.add(card("Bolt", "Instant", "{R}"));
		assertTrue("a name group is always transient", byName.isTransient());
	}

	public void testValuesHaveOneEntryPerField() {
		CardGroup g = new CardGroup(MagicCardField.TYPE, "g");
		g.add(card("A", "Instant", "{R}"));
		assertEquals(MagicCardField.allNonTransientFields(true).length, g.getValues().size());
	}

	// ---- the label of a group per field

	public void testLabelOfTheGroupingFieldIsTheGroupName() {
		CardGroup g = new CardGroup(MagicCardField.TYPE, "Instant");
		assertEquals("Instant", g.getLabelByField(MagicCardField.TYPE));
		assertEquals("an empty group has no value", "", g.getLabelByField(MagicCardField.COST));
		g.add(card("A", "Instant", "{R}"));
		assertEquals("the name of a group is its own name", "Instant", g.getLabelByField(MagicCardField.NAME));
	}

	// ---- naming the group of a card

	public void testGroupNameByName() {
		assertEquals("Bolt", CardGroup.getGroupName(card("Bolt", "Instant", "{R}"), MagicCardField.NAME));
	}

	public void testGroupNameByManaValue() {
		assertEquals("2", CardGroup.getGroupName(card("A", "Creature", "{1}{R}"), MagicCardField.CMC));
		assertEquals("0", CardGroup.getGroupName(card("A", "Artifact", ""), MagicCardField.CMC));
		assertEquals("a land is named Land, not 0", "Land",
				CardGroup.getGroupName(card("Forest", "Basic Land - Forest", ""), MagicCardField.CMC));
	}

	public void testGroupNameByColor() {
		String red = CardGroup.getGroupName(card("A", "Instant", "{R}"), MagicCardField.COST);
		String green = CardGroup.getGroupName(card("B", "Instant", "{G}"), MagicCardField.COST);
		assertNotNull(red);
		assertFalse(red.equals(green));
		assertEquals(red, CardGroup.getGroupName(card("C", "Sorcery", "{R}{R}"), MagicCardField.COST));
		assertEquals("generic mana adds Colorless", "Red-Colorless",
				CardGroup.getGroupName(card("D", "Sorcery", "{2}{R}"), MagicCardField.COST));
	}

	public void testGroupNameByLanguage() {
		MagicCard c = card("A", "Instant", "{R}");
		assertEquals("English", CardGroup.getGroupName(c, MagicCardField.LANG));
		c.setLanguage("French");
		assertEquals("French", CardGroup.getGroupName(c, MagicCardField.LANG));
	}

	public void testGroupNameBySideboard() {
		MagicCardPhysical main = new MagicCardPhysical(card("A", "Instant", "{R}"), Location.valueOf("deck"));
		MagicCardPhysical side = new MagicCardPhysical(card("A", "Instant", "{R}"), Location.valueOf("deck").toSideboard());
		assertEquals("Main Deck", CardGroup.getGroupName(main, MagicCardField.SIDEBOARD));
		assertEquals("Sideboard", CardGroup.getGroupName(side, MagicCardField.SIDEBOARD));
		assertEquals("a card that is not an instance is in the main deck", "Main Deck",
				CardGroup.getGroupName(card("A", "Instant", "{R}"), MagicCardField.SIDEBOARD));
	}

	public void testGroupNameOfAnyOtherFieldIsItsValue() {
		MagicCard c = card("A", "Creature - Bear", "{G}");
		assertEquals("Creature - Bear", CardGroup.getGroupName(c, MagicCardField.TYPE));
	}

	public void testGroupNameWhenTheValueCannotBeRead() {
		assertEquals("Unknown", CardGroup.getGroupName(null, MagicCardField.NAME));
	}

	// ---- misc

	public void testGroupsOfPlainCardsAreNotPhysicalUnlessEmpty() {
		CardGroup g = new CardGroup(MagicCardField.TYPE, "g");
		assertTrue("nothing to contradict", g.isPhysical());
		g.add(owned("A", 1));
		assertTrue(g.isPhysical());
	}
}
