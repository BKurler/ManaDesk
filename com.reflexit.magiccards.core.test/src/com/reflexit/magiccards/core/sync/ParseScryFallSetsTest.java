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
package com.reflexit.magiccards.core.sync;

import java.io.BufferedReader;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Locale;

import com.reflexit.magiccards.core.model.Edition;

import junit.framework.TestCase;

/**
 * Which sets of Scryfall's set list are kept: the digital ones (flagged), and the ones not released yet as
 * long as they already have cards; only an empty set is skipped. Offline: the set list is a small JSON
 * text. Plain JUnit.
 */
public class ParseScryFallSetsTest extends TestCase {

	private static String date(int daysFromNow) {
		Calendar c = Calendar.getInstance();
		c.add(Calendar.DAY_OF_YEAR, daysFromNow);
		return new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(c.getTime());
	}

	private static String set(String code, String name, int released, int cards, boolean digital) {
		return "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"set_type\":\"expansion\",\"released_at\":\""
				+ date(released) + "\",\"icon_svg_uri\":\"https://svgs.scryfall.io/sets/" + code
				+ ".svg?1\",\"digital\":" + digital + ",\"card_count\":" + cards + "}";
	}

	private static ParseScryFallSets parse(String... sets) throws Exception {
		StringBuilder sb = new StringBuilder("{\"has_more\":false,\"data\":[");
		for (int i = 0; i < sets.length; i++)
			sb.append(i > 0 ? "," : "").append(sets[i]);
		sb.append("]}");
		ParseScryFallSets parser = new ParseScryFallSets();
		parser.processFromReader(new BufferedReader(new StringReader(sb.toString())), null);
		return parser;
	}

	private static Collection<String> names(ParseScryFallSets parser) {
		Collection<String> names = new ArrayList<String>();
		for (Edition ed : parser.getAll())
			names.add(ed.getName());
		return names;
	}

	public void testASetNotReleasedYetIsKeptWhenItHasCards() throws Exception {
		Collection<String> names = names(parse(set("psx", "Parse Test Future", 20, 343, false)));
		assertTrue(names.toString(), names.contains("Parse Test Future"));
	}

	public void testAnEmptySetIsStillSkipped() throws Exception {
		Collection<String> names = names(parse(set("psy", "Parse Test Empty Future", 20, 0, false),
				set("psz", "Parse Test Empty Past", -20, 0, false)));
		assertFalse(names.toString(), names.contains("Parse Test Empty Future"));
		assertFalse(names.toString(), names.contains("Parse Test Empty Past"));
	}

	public void testAReleasedSetAndADigitalSetAreKept() throws Exception {
		Collection<String> names = names(parse(set("pa1", "Parse Test Past", -400, 250, false),
				set("pa2", "Parse Test Digital", -30, 100, true)));
		assertTrue(names.toString(), names.contains("Parse Test Past"));
		assertTrue(names.toString(), names.contains("Parse Test Digital"));
	}

	public void testAFutureSetIsComingSoonAndKeepsItsRealName() throws Exception {
		Edition ed = parse(set("pa3", "Parse Test Soon", 15, 80, false)).getAll().iterator().next();
		assertEquals("Parse Test Soon", ed.getName());
		assertTrue(ed.isComingSoon());
		assertEquals("Parse Test Soon (coming soon)", ed.getDisplayName());
	}
}
