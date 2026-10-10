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
package com.reflexit.magiccards.core.exports;

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.test.assist.MemCardHandler;

import junit.framework.TestCase;

/** ManaBox CSV import / export, on rows from a real ManaBox export. */
public class ManaBoxCsvTest extends TestCase {
	static final String HEADER = "Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,ManaBox ID,Scryfall ID,"
			+ "Purchase price,Misprint,Altered,Signed,Condition,Language,Proxy,Purchase price currency,Added";

	/** The last parse's result. */
	private ImportData data;

	private List<IMagicCard> parse(String text) throws Exception {
		ManaBoxCsvImportDelegate importer = new ManaBoxCsvImportDelegate();
		data = new ImportData();
		data.setText(text);
		importer.init(data);
		importer.run(ICoreProgressMonitor.NONE);
		if (data.getError() != null)
			throw new Exception(data.getError());
		@SuppressWarnings({ "unchecked", "rawtypes" })
		List<IMagicCard> list = (List) data.getList();
		return list;
	}

	public void testRealExportRows() throws Exception {
		String text = HEADER + "\n"
				+ "Kaervek the Merciless,DSC,Duskmourn: House of Horror Commander,222,normal,rare,1,99260,"
				+ "95c5bd95-b483-4dfb-9010-cfee47d22468,0.36,false,false,false,near_mint,en,false,USD,"
				+ "2026-10-10T19:29:49.920Z\n" //
				+ "\n" // blank lines are tolerated
				+ "\"Aang, at the Crossroads // Aang, Destined Savior\",TLA,Avatar: The Last Airbender,203,normal,rare,1,"
				+ "109588,fea89ca0-8070-4f28-9851-994314f9d248,0.48,false,false,false,near_mint,en,false,USD,"
				+ "2026-10-10T19:29:49.924Z\n" //
				+ "Earth Village Ruffians,TLA,Avatar: The Last Airbender,219,foil,common,1,108212,"
				+ "56d4e0b7-232f-4953-84a3-e6f17781aa45,0.12,false,false,false,near_mint,en,false,USD,"
				+ "2026-10-10T19:29:49.932Z\n" //
				+ "Marit Lage,TTLE,Avatar: The Last Airbender Eternal Tokens,1,normal,common,1,109988,"
				+ "dcff76fb-e23d-4333-a770-b1d616f98486,,false,false,false,near_mint,en,false,USD,"
				+ "2026-10-10T19:29:49.939Z\n" //
				+ "\"Atraxa, Praetors' Voice\",MUL,Multiverse Legends,98,etched,mythic,6,82156,"
				+ "cf7e8917-8bd1-4899-bc78-7a5815c08057,40.39,false,false,false,near_mint,en,false,USD,"
				+ "2026-10-10T19:29:49.911Z\n";
		List<IMagicCard> cards = parse(text);
		assertEquals(5, cards.size());
		MagicCardPhysical kaervek = (MagicCardPhysical) cards.get(0);
		assertEquals("Kaervek the Merciless", kaervek.getName());
		assertEquals("95c5bd95-b483-4dfb-9010-cfee47d22468", kaervek.getCardId());
		assertEquals("222", kaervek.getCollectorId());
		assertEquals(1, kaervek.getCount());
		assertEquals(CardFinish.NONFOIL, kaervek.getFinish());
		assertEquals(CardCondition.NEAR_MINT, kaervek.getCondition());
		assertTrue(kaervek.getLanguage() == null || "English".equals(kaervek.getLanguage())); // English = default
		assertFalse(kaervek.isProxy());
		assertEquals("Aang, at the Crossroads // Aang, Destined Savior", cards.get(1).getName());
		assertEquals(CardFinish.FOIL, ((MagicCardPhysical) cards.get(2)).getFinish());
		assertEquals(0f, ((MagicCardPhysical) cards.get(3)).getPrice(), 0.001); // no price for the token
		MagicCardPhysical atraxa = (MagicCardPhysical) cards.get(4);
		// stored as etched; shown as etched once bound to its printing (which supports it)
		assertEquals(CardFinish.ETCHED, atraxa.getRawFinish());
		assertEquals(6, atraxa.getCount());
	}

	public void testFlagsConditionLanguageProxy() throws Exception {
		String text = HEADER + "\n" + "Fog,4ED,Fourth Edition,244,normal,common,2,39943,"
				+ "a1959de7-e945-438c-a90a-158e21c4d5bf,0.28,true,false,true,lightly_played,ja,true,USD,"
				+ "2026-10-10T19:29:49.972Z\n";
		MagicCardPhysical fog = (MagicCardPhysical) parse(text).get(0);
		assertEquals(CardCondition.LIGHTLY_PLAYED, fog.getCondition());
		assertNull(fog.getLanguage()); // imported as English...
		assertEquals("Japanese", data.getDeclaredLanguage(fog)); // ...reported in the preview
		assertTrue(fog.isProxy());
		assertTrue(fog.isSpecialTag("misprint"));
		assertFalse(fog.isSpecialTag("altered"));
		assertTrue(fog.isSpecialTag("signed"));
	}

	public void testCollectionExportWithBinderColumns() throws Exception {
		String text = "Binder Name,Binder Type," + HEADER + "\n" + "New,binder,Fog,4ED,Fourth Edition,244,normal,common,1,"
				+ "39943,a1959de7-e945-438c-a90a-158e21c4d5bf,0.28,false,false,false,near_mint,en,false,USD,"
				+ "2026-10-10T19:29:49.972Z\n";
		MagicCardPhysical fog = (MagicCardPhysical) parse(text).get(0);
		assertEquals("Fog", fog.getName());
		assertEquals("a1959de7-e945-438c-a90a-158e21c4d5bf", fog.getCardId());
	}

	public void testNotManaBoxIsRejected() {
		try {
			parse("Name,Set,Count\nFog,Fourth Edition,1\n");
			fail("a non-ManaBox CSV was accepted");
		} catch (Exception expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("ManaBox"));
		}
	}

	/** Rows of the second real export: every condition, a French card, each flag. */
	public void testSecondRealExport() throws Exception {
		String text = HEADER + "\n"
				+ "Centaur Glade,ONS,Onslaught,251,normal,uncommon,1,31052,1c75f9c8-9640-4f64-b32a-916436e461fc,0.24,"
				+ "false,false,false,near_mint,fr,false,USD,2026-10-10T19:29:49.987Z\n"
				+ "Ceta Sanctuary,APC,Apocalypse,20,normal,uncommon,1,32436,32cec6f3-295a-45e3-8466-e35fb043a596,0.23,"
				+ "true,false,false,near_mint,en,false,USD,2026-10-10T19:29:49.988Z\n"
				+ "Earthshaker,CHK,Champions of Kamigawa,165,normal,uncommon,2,29060,283b47a9-c21e-4c0a-9c70-38b8ebbffab3,"
				+ "0.23,false,false,false,light_played,en,false,USD,2026-10-10T19:29:49.910Z\n"
				+ "\"Atraxa, Praetors' Voice\",MUL,Multiverse Legends,98,etched,mythic,6,82156,"
				+ "cf7e8917-8bd1-4899-bc78-7a5815c08057,40.39,false,false,false,played,en,false,USD,"
				+ "2026-10-10T19:29:49.911Z\n"
				+ "Seven Dwarves,SLD,Secret Lair Drop,813,foil,rare,3,103975,32d232d2-d26d-4e54-aa1a-ffe469a2cbde,46.55,"
				+ "false,false,true,near_mint,en,false,USD,2026-10-10T19:29:49.914Z\n"
				+ "Lavalanche,DMC,Dominaria United Commander,157,normal,rare,2,71814,e3a3b450-d988-40cb-a1ee-8b00ccdf5071,"
				+ "0.16,false,false,false,near_mint,en,true,USD,2026-10-10T19:29:49.915Z\n"
				+ "Craw Wurm,5ED,Fifth Edition,286,normal,common,2,37720,1a2e6afb-7094-4fa3-9246-58343f8d80b8,0.17,"
				+ "false,false,false,excellent,en,false,USD,2026-10-10T19:29:49.962Z\n"
				+ "Will-o'-the-Wisp,4ED,Fourth Edition,171,normal,rare,2,40016,583ef317-8105-4917-ba3c-93de3eebd944,2.41,"
				+ "false,true,false,near_mint,en,false,USD,2026-10-10T19:29:49.966Z\n"
				+ "Ironroot Treefolk,4ED,Fourth Edition,253,normal,common,3,39934,c6c93c85-5263-4770-b937-704e57912478,"
				+ "0.37,false,false,false,poor,en,false,USD,2026-10-10T19:29:49.969Z\n"
				+ "Dragon,TTLA,Avatar: The Last Airbender Tokens,9,normal,common,2,109983,"
				+ "cddc0746-6d3e-441f-866f-28587bf54801,,false,false,false,good,en,false,USD,2026-10-10T19:29:49.933Z\n";
		List<IMagicCard> c = parse(text);
		assertEquals(10, c.size());
		// the card stays English (English printing id, English-only database);
		// the file's language is reported for the preview
		assertNull(c.get(0).getLanguage());
		assertEquals("French", data.getDeclaredLanguage(c.get(0)));
		assertEquals(1, data.getLanguageOverridden().size());
		// Added -> the copy's Date
		assertEquals("2026-10-10T19:29:49.987Z", ((MagicCardPhysical) c.get(0)).getDate().toInstant().toString());
		assertTrue(((MagicCardPhysical) c.get(1)).isSpecialTag("misprint"));
		assertEquals(CardCondition.MODERATELY_PLAYED, ((MagicCardPhysical) c.get(2)).getCondition());
		assertEquals(CardCondition.HEAVILY_PLAYED, ((MagicCardPhysical) c.get(3)).getCondition());
		assertTrue(((MagicCardPhysical) c.get(4)).isSpecialTag("signed"));
		assertTrue(((MagicCardPhysical) c.get(5)).isProxy());
		assertEquals(CardCondition.LIGHTLY_PLAYED, ((MagicCardPhysical) c.get(6)).getCondition());
		assertTrue(((MagicCardPhysical) c.get(7)).isSpecialTag("altered"));
		assertEquals(CardCondition.DAMAGED, ((MagicCardPhysical) c.get(8)).getCondition());
		assertEquals(CardCondition.MODERATELY_PLAYED, ((MagicCardPhysical) c.get(9)).getCondition());
	}

	private static MagicCard printing(String id, String set, String num, String lang) {
		MagicCard c = new MagicCard();
		c.setName("Centaur Glade");
		c.setSet(set);
		c.setCollNumber(num);
		c.setCardId(id);
		if (lang != null)
			c.setLanguage(lang);
		return c;
	}

	/** A French copy is bound to the French printing when the database has it. */
	public void testLocalizedPrinting() {
		MagicCard en = printing("en-id", "Onslaught", "251", null);
		MagicCard frOther = printing("fr-other", "Onslaught", "251a", "French");
		MagicCard frSame = printing("fr-id", "Onslaught", "251", "French");
		MagicCard frOtherSet = printing("fr-set", "Legions", "251", "French");
		MagicCardPhysical copy = new MagicCardPhysical(en, null);
		java.util.List<IMagicCard> all = java.util.Arrays.<IMagicCard> asList(en, frOther, frOtherSet, frSame);
		// same set + same collector number first
		assertSame(frSame, ImportUtils.findLocalizedPrinting(copy, "French", all));
		// else any printing of that language in the same set
		assertSame(frOther,
				ImportUtils.findLocalizedPrinting(copy, "French", java.util.Arrays.<IMagicCard> asList(en, frOther)));
		// never another set, never another language
		assertNull(ImportUtils.findLocalizedPrinting(copy, "French", java.util.Arrays.<IMagicCard> asList(en, frOtherSet)));
		assertNull(ImportUtils.findLocalizedPrinting(copy, "Japanese", all));
	}

	public void testConditionGrades() {
		assertEquals(CardCondition.NEAR_MINT, ManaBoxCsvImportDelegate.condition("near_mint"));
		assertEquals(CardCondition.NEAR_MINT, ManaBoxCsvImportDelegate.condition("mint"));
		assertEquals(CardCondition.MODERATELY_PLAYED, ManaBoxCsvImportDelegate.condition("moderately_played"));
		assertEquals(CardCondition.HEAVILY_PLAYED, ManaBoxCsvImportDelegate.condition("heavily_played"));
		assertEquals(CardCondition.DAMAGED, ManaBoxCsvImportDelegate.condition("damaged"));
		assertEquals(CardCondition.LIGHTLY_PLAYED, ManaBoxCsvImportDelegate.condition("excellent"));
		assertEquals(CardCondition.DAMAGED, ManaBoxCsvImportDelegate.condition("poor"));
		assertNull(ManaBoxCsvImportDelegate.condition(""));
	}

	public void testExportRoundTrip() throws Exception {
		MagicCard base = new MagicCard();
		base.setName("Fog");
		base.setSet("Fourth Edition");
		base.setCollNumber("244");
		base.setCardId("a1959de7-e945-438c-a90a-158e21c4d5bf");
		MagicCardPhysical fog = new MagicCardPhysical(base, null);
		fog.setCount(2);
		fog.setFinish(CardFinish.FOIL);
		fog.setCondition(CardCondition.HEAVILY_PLAYED);
		fog.setProxy(true);
		fog.setSpecialTag("altered");
		fog.setPrice(1.5f);
		fog.setDate(java.util.Date.from(java.time.Instant.parse("2026-10-10T19:29:49.972Z")));
		MemCardHandler store = new MemCardHandler();
		store.getCardStore().add(fog);
		store.update();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ManaBoxCsvExportDelegate exporter = new ManaBoxCsvExportDelegate();
		exporter.setReportType(ImportExportFactory.createReportType("testmanabox"));
		exporter.init(out, true, store);
		exporter.run(ICoreProgressMonitor.NONE);
		String[] lines = out.toString("UTF-8").split("\r?\n");
		assertEquals(ManaBoxCsvExportDelegate.HEADER, lines[0]);
		assertTrue(lines[1], lines[1].startsWith("Fog,"));
		assertTrue(lines[1], lines[1].contains(",Fourth Edition,244,foil,"));
		assertTrue(lines[1], lines[1].contains(",2,a1959de7-e945-438c-a90a-158e21c4d5bf,1.50,false,true,false,played,en,true,"));
		// ...and it reads back
		MagicCardPhysical back = (MagicCardPhysical) parse(
				lines[0] + "\n" + lines[1] + "\n").get(0);
		assertEquals("a1959de7-e945-438c-a90a-158e21c4d5bf", back.getCardId());
		assertEquals(CardFinish.FOIL, back.getFinish());
		assertEquals(CardCondition.HEAVILY_PLAYED, back.getCondition());
		assertEquals(2, back.getCount());
		assertTrue(back.isProxy());
		assertTrue(back.isSpecialTag("altered"));
		assertTrue(lines[1], lines[1].endsWith(",2026-10-10T19:29:49.972Z"));
		assertEquals(fog.getDate(), back.getDate());
	}

	/** "Generate header row" unchecked: no header line. */
	public void testExportWithoutHeader() throws Exception {
		MagicCard base = new MagicCard();
		base.setName("Fog");
		base.setSet("Fourth Edition");
		base.setCardId("a1959de7-e945-438c-a90a-158e21c4d5bf");
		MemCardHandler store = new MemCardHandler();
		store.getCardStore().add(new MagicCardPhysical(base, null));
		store.update();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ManaBoxCsvExportDelegate exporter = new ManaBoxCsvExportDelegate();
		exporter.setReportType(ImportExportFactory.createReportType("testmanabox2"));
		exporter.init(out, false, store);
		exporter.run(ICoreProgressMonitor.NONE);
		String[] lines = out.toString("UTF-8").split("\r?\n");
		assertEquals(1, lines.length);
		assertTrue(lines[0], lines[0].startsWith("Fog,"));
	}

	public void testExportHelpers() {
		assertEquals("A // B", ManaBoxCsvExportDelegate.name("A // B (transform)"));
		assertEquals("Fog (Retro)", ManaBoxCsvExportDelegate.name("Fog (Retro)"));
		assertEquals("abc", ManaBoxCsvExportDelegate.scryfallId("-abc"));
		assertEquals("near_mint", ManaBoxCsvExportDelegate.condition(null));
		// every ManaDesk grade exports to a ManaBox grade that reads back the same
		for (CardCondition g : CardCondition.values())
			assertEquals(g, ManaBoxCsvImportDelegate.condition(ManaBoxCsvExportDelegate.condition(g)));
		assertEquals("normal", ManaBoxCsvExportDelegate.finish(CardFinish.NONFOIL));
	}
}
