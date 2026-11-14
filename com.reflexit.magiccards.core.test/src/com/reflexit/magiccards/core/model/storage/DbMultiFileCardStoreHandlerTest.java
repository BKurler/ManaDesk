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
package com.reflexit.magiccards.core.model.storage;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.events.CardEvent;
import com.reflexit.magiccards.core.model.events.ICardEventListener;
import com.reflexit.magiccards.core.model.xml.DbMultiFileCardStore;
import com.reflexit.magiccards.core.model.xml.XmlCardHolder;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.unittesting.CardGenerator;

/**
 * The card database store: how its id and name indexes resolve cards added twice
 * (merge, re-derived Scryfall facts, double-faced cards), the bulk update (events
 * withheld and replayed once, auto-commit restored, errors passed on), and writing
 * the changed set files. Uses temp set files only (never the real database folder),
 * deleted in tearDown. Works as plain JUnit.
 */
public class DbMultiFileCardStoreHandlerTest extends TestCase {
	private static final String SET_A = "Handler Test Set A";
	private static final String SET_B = "Handler Test Set B";
	private DbMultiFileCardStore store;
	private final List<File> files = new ArrayList<File>();
	private MagicCard a;

	@Override
	protected void setUp() throws Exception {
		store = new DbMultiFileCardStore(false) {
		};
		addSetFile(SET_A);
		addSetFile(SET_B);
		a = card("Handler Card", SET_A);
	}

	@Override
	protected void tearDown() throws Exception {
		for (File f : files)
			f.delete();
		files.clear();
		// guard: nothing may have been written to the real database folder
		boolean leaked = false;
		for (String set : new String[] { SET_A, SET_B }) {
			File real = new File(XmlCardHolder.getDbFolder(), Location.createLocationFromSet(set).getBaseFileName());
			if (real.exists()) {
				leaked = true;
				real.delete();
			}
		}
		assertFalse("the test wrote to the real database folder", leaked);
	}

	private void addSetFile(String set) throws IOException {
		File f = File.createTempFile("handler-test", ".xml");
		files.add(f);
		// the same location a card of that set resolves to (spaces become "_"): with any other one the
		// store would create a file for the set in the real database folder
		store.addFile(f, Location.createLocationFromSet(set), false);
	}

	private static MagicCard card(String name, String set) {
		MagicCard c = CardGenerator.generateRandomCard();
		c.setName(name);
		c.setSet(set);
		return c;
	}

	// ---- lookups by id and by name

	public void testGetCardById() {
		store.add(a);
		assertSame(a, store.getCard(a.getCardId()));
		assertNull(store.getCard("no-such-id"));
		Collection<IMagicCard> found = store.getCards(a.getCardId());
		assertEquals(1, found.size());
		assertSame(a, found.iterator().next());
		assertTrue(store.getCards("no-such-id").isEmpty());
	}

	public void testPrimeAndCandidatesByName() {
		assertNull(store.getPrime("Handler Card"));
		assertTrue(store.getCandidates(null).isEmpty());
		assertTrue(store.getCandidates("Handler Card").isEmpty());
		store.add(a);
		assertSame(a, store.getPrime("Handler Card"));
		assertEquals(1, store.getCandidates("Handler Card").size());
		MagicCard reprint = card("Handler Card", SET_B);
		store.add(reprint);
		Collection<IMagicCard> all = store.getCandidates("Handler Card");
		assertEquals(2, all.size());
		assertTrue(all.contains(a));
		assertTrue(all.contains(reprint));
		assertTrue(store.getCandidates("Another Name").isEmpty());
	}

	// ---- the same card added again

	public void testSameCardAddedAgainIsMergedNotDuplicated() {
		a.setProperty(MagicCardField.GAME_CHANGER, "true");
		a.setProperty(MagicCardField.PRODUCED_MANA, "{G}");
		store.add(a);
		MagicCard again = a.cloneCard();
		again.setProperty(MagicCardField.GAME_CHANGER, null);
		again.setProperty(MagicCardField.PRODUCED_MANA, "{R}");
		store.add(again);
		assertEquals(1, store.size());
		IMagicCard kept = store.getCard(a.getCardId());
		// Scryfall-derived facts are re-derived on every update: the new value wins, even an empty one
		assertNull(kept.get(MagicCardField.GAME_CHANGER));
		assertEquals("{R}", kept.get(MagicCardField.PRODUCED_MANA));
	}

	public void testSameCardKeepsItsOwnNonEmptyFields() {
		a.setOracleText("Old text");
		store.add(a);
		MagicCard again = a.cloneCard();
		again.setOracleText("");
		store.add(again);
		assertEquals("an empty incoming field must not erase a known one", "Old text",
				((MagicCard) store.getCard(a.getCardId())).getOracleText());
	}

	public void testAccessoriesAreKeptWhenTheIncomingCardHasNoneAndReplacedWhenItHasSome() {
		a.set(MagicCardField.ACCESSORIES, "Treasure");
		store.add(a);
		MagicCard noColumn = a.cloneCard();
		noColumn.set(MagicCardField.ACCESSORIES, null);
		store.add(noColumn);
		assertEquals("a record without the column leaves the accessories alone", "Treasure",
				((MagicCard) store.getCard(a.getCardId())).getAccessories());
		MagicCard changed = a.cloneCard();
		changed.set(MagicCardField.ACCESSORIES, "Treasure|Clue");
		store.add(changed);
		assertEquals("a new value replaces the old one", "Treasure|Clue",
				((MagicCard) store.getCard(a.getCardId())).getAccessories());
		assertEquals(1, store.size());
	}

	// ---- two faces / parts that share one id

	public void testSecondFaceGetsAnotherId() {
		store.add(a);
		MagicCard back = a.cloneCard();
		back.setProperty(MagicCardField.PART, "back");
		store.add(back);
		assertEquals(2, store.size());
		assertSame("the first card keeps the id", a, store.getCard(a.getCardId()));
		assertSame("the face is indexed under a marked id", back, store.getCard("-" + a.getCardId()));
	}

	public void testFirstFaceIsMovedWhenTheOtherArrivesSecond() {
		MagicCard back = a.cloneCard();
		back.setProperty(MagicCardField.PART, "back");
		store.add(back);
		MagicCard front = a.cloneCard();
		store.add(front);
		assertEquals(2, store.size());
		assertSame("the plain card takes the id", front, store.getCard(a.getCardId()));
		assertSame("the part that came first is moved", back, store.getCard("-" + a.getCardId()));
	}

	public void testTwoDifferentPartsOfTheSameSideAreAConflict() {
		MagicCard p1 = a.cloneCard();
		p1.setProperty(MagicCardField.PART, "one");
		MagicCard p2 = a.cloneCard();
		p2.setProperty(MagicCardField.PART, "two");
		store.add(p1);
		store.add(p2);
		assertEquals("the second one is refused", 1, store.size());
		assertSame(p1, store.getCard(a.getCardId()));
	}

	// ---- what a database store does not provide

	public void testStoreInfoIsNotAvailableForTheDatabase() {
		try {
			store.getName();
			fail();
		} catch (UnsupportedOperationException e) {
			// expected
		}
		try {
			store.getComment();
			fail();
		} catch (UnsupportedOperationException e) {
			// expected
		}
		try {
			store.isVirtual();
			fail();
		} catch (UnsupportedOperationException e) {
			// expected
		}
		try {
			store.isUnsorted();
			fail();
		} catch (UnsupportedOperationException e) {
			// expected
		}
		assertNull(store.getLocation());
	}

	public void testCountFollowsTheCards() {
		assertEquals(0, store.getCount());
		store.add(a);
		store.add(card("Other Card", SET_B));
		assertEquals(2, store.getCount());
		assertEquals(store.size(), store.getCount());
	}

	// ---- bulk update

	private static class Counter implements ICardEventListener {
		int count;

		@Override
		public void handleEvent(CardEvent event) {
			count++;
		}
	}

	public void testUpdateOperationRunsTheWorkAndRestoresAutoCommit() throws Exception {
		store.setAutoCommit(true);
		final boolean[] ran = new boolean[1];
		final boolean[] inside = new boolean[1];
		store.updateOperation(monitor -> {
			ran[0] = true;
			inside[0] = store.isAutoCommit();
		}, ICoreProgressMonitor.NONE);
		assertTrue(ran[0]);
		assertFalse("no auto-commit while the update runs", inside[0]);
		assertTrue("restored afterwards", store.isAutoCommit());
	}

	public void testUpdateOperationReplaysOneEventForManyChanges() throws Exception {
		final Counter counter = new Counter();
		store.addListener(counter);
		final int[] during = new int[1];
		store.updateOperation(monitor -> {
			store.add(a);
			store.add(card("Second Card", SET_B));
			store.add(card("Third Card", SET_A));
			during[0] = counter.count;
		}, ICoreProgressMonitor.NONE);
		assertEquals("nothing is sent while the update runs", 0, during[0]);
		assertEquals("one event is replayed at the end", 1, counter.count);
		assertEquals(3, store.size());
		// afterwards events flow normally again
		store.add(card("Fourth Card", SET_B));
		assertTrue(counter.count > 1);
	}

	public void testUpdateOperationWithoutChangesSendsNothing() throws Exception {
		final Counter counter = new Counter();
		store.addListener(counter);
		store.updateOperation(monitor -> {
		}, ICoreProgressMonitor.NONE);
		assertEquals(0, counter.count);
	}

	public void testUpdateOperationPassesOnARuntimeError() throws Exception {
		final Counter counter = new Counter();
		store.addListener(counter);
		store.setAutoCommit(true);
		try {
			store.updateOperation(monitor -> {
				throw new InvocationTargetException(new IllegalStateException("boom"));
			}, ICoreProgressMonitor.NONE);
			fail();
		} catch (IllegalStateException e) {
			assertEquals("boom", e.getMessage());
		}
		assertTrue("auto-commit restored after a failure", store.isAutoCommit());
		store.add(a); // events are not withheld any more
		assertTrue(counter.count > 0);
	}

	public void testUpdateOperationWrapsACheckedError() throws Exception {
		try {
			store.updateOperation(monitor -> {
				throw new InvocationTargetException(new IOException("disk"));
			}, ICoreProgressMonitor.NONE);
			fail();
		} catch (MagicException e) {
			assertTrue(e.getCause() instanceof IOException);
		}
	}

	// ---- writing the changed set files

	public void testSaveDirtySetsLeavesTheSetFileWithItsCards() throws Exception {
		store.setAutoCommit(false);
		store.add(a); // set A
		File fileA = files.get(0);
		store.saveDirtySets(ICoreProgressMonitor.NONE);
		assertTrue("the changed set is written", fileA.length() > 0);
		String xml = new String(java.nio.file.Files.readAllBytes(fileA.toPath()), java.nio.charset.StandardCharsets.UTF_8);
		assertTrue(xml, xml.contains("<name>Handler Card</name>"));
	}

	public void testSaveDirtySetsWithNothingToSave() {
		store.saveDirtySets(ICoreProgressMonitor.NONE);
		for (File f : files)
			assertEquals(0, f.length());
	}
}
