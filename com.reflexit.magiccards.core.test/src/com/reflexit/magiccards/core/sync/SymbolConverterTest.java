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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.sync.ScryfallReference.Symbol;

/**
 * From a card symbol to its picture: the file name and address of a symbol, the PNG name, the
 * pictures rendered on demand for symbols that do not ship with the application. The symbol
 * list is replaced by a small one whose pictures are {@code file:} addresses and the pictures
 * go to a temp folder, so nothing goes on the web and the real ManaDesk folder is not touched.
 * Works as plain JUnit.
 */
public class SymbolConverterTest extends TestCase {
	private File dir;

	@Override
	protected void setUp() throws Exception {
		dir = Files.createTempDirectory("symbol-test").toFile();
		SymbolConverter.useDirForTests(new File(dir, "png"));
	}

	@Override
	protected void tearDown() throws Exception {
		SymbolConverter.useDirForTests(null);
		ScryfallReference.useSymbolsForTests(null);
		FileUtils.deleteTree(dir);
	}

	private File svg(String name, String color) throws IOException {
		File f = new File(dir, name);
		Files.write(f.toPath(), ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\" viewBox=\"0 0 10 10\">"
				+ "<rect width=\"10\" height=\"10\" fill=\"" + color + "\"/></svg>").getBytes(StandardCharsets.UTF_8));
		return f;
	}

	private static Symbol symbol(String text, String svgUri) {
		return new Symbol(text, ScryfallReference.fileOf(svgUri), svgUri);
	}

	private static void symbols(Symbol... list) {
		Map<String, Symbol> m = new LinkedHashMap<>();
		for (Symbol s : list)
			m.put(s.symbol, s);
		ScryfallReference.useSymbolsForTests(m);
	}

	// ---- file name and address

	public void testTheFileOfAKnownSymbol() {
		assertEquals("R.svg", SymbolConverter.getScryfallFilename("{R}"));
		assertEquals("RG.svg", SymbolConverter.getScryfallFilename("{R/G}"));
	}

	public void testTheFileOfAnUnknownSymbolIsNull() {
		assertNull(SymbolConverter.getScryfallFilename("{no such symbol}"));
		assertNull(SymbolConverter.getScryfallFilename(null));
	}

	public void testTheAddressOfAKnownSymbolIsItsSvg() {
		URL url = SymbolConverter.getScryfallManaURL("{R}");
		assertNotNull(url);
		assertTrue(url.toString(), url.getPath().endsWith("/R.svg"));
	}

	public void testTheAddressOfAnUnknownSymbolIsNull() {
		assertNull(SymbolConverter.getScryfallManaURL("{no such symbol}"));
	}

	public void testASymbolWithABrokenAddressHasNoUrl() {
		symbols(new Symbol("{B}", "B.svg", "not an address"));
		assertNull(SymbolConverter.getScryfallManaURL("{B}"));
	}

	// ---- PNG names

	public void testPngNameReplacesTheSvgExtension() {
		assertEquals("RG.png", SymbolConverter.pngName("RG.svg"));
		assertEquals("RG.png", SymbolConverter.pngName("RG.png"));
		assertEquals("noextension", SymbolConverter.pngName("noextension"));
	}

	// ---- the pictures kept locally

	public void testThePictureFolderIsCreated() {
		File d = SymbolConverter.getLocalPngDir();
		assertEquals(new File(dir, "png"), d);
		assertTrue(d.isDirectory());
	}

	public void testNothingRenderedYet() throws Exception {
		assertNull(SymbolConverter.getLocalPng(null));
		assertNull(SymbolConverter.getLocalPng("T.svg"));
		File empty = new File(SymbolConverter.getLocalPngDir(), "E.png");
		assertTrue(empty.createNewFile());
		assertNull("an empty file is not a picture", SymbolConverter.getLocalPng("E.svg"));
	}

	public void testARenderedPictureIsFoundByItsSvgOrPngName() throws Exception {
		File png = new File(SymbolConverter.getLocalPngDir(), "T.png");
		Files.write(png.toPath(), new byte[] { 1, 2, 3 });
		assertEquals(png, SymbolConverter.getLocalPng("T.svg"));
		assertEquals(png, SymbolConverter.getLocalPng("T.png"));
	}

	// ---- rendering what the application does not ship with

	public void testMissingPicturesAreRenderedAt19PixelsInTheirOwnColors() throws Exception {
		symbols(symbol("{T}", svg("T.svg", "#FF0000").toURI().toString()));
		assertEquals(1, SymbolConverter.renderMissingPngs(name -> false));
		File png = SymbolConverter.getLocalPng("T.svg");
		assertNotNull(png);
		BufferedImage img = ImageIO.read(png);
		assertEquals(19, img.getWidth());
		assertEquals(19, img.getHeight());
		assertEquals("colors are kept", 0xFFFF0000, img.getRGB(9, 9));
		assertEquals("no temporary file left", 1, png.getParentFile().list().length);
	}

	public void testWhatIsAlreadyThereIsNotRenderedAgain() throws Exception {
		symbols(symbol("{T}", svg("T.svg", "#FF0000").toURI().toString()));
		assertEquals(1, SymbolConverter.renderMissingPngs(name -> false));
		assertEquals(0, SymbolConverter.renderMissingPngs(name -> false));
	}

	public void testBundledPicturesAreSkipped() throws Exception {
		symbols(symbol("{T}", svg("T.svg", "#FF0000").toURI().toString()));
		assertEquals(0, SymbolConverter.renderMissingPngs(name -> name.equals("T.png")));
		assertNull(SymbolConverter.getLocalPng("T.svg"));
	}

	public void testASymbolThatCannotBeRenderedDoesNotStopTheOthers() throws Exception {
		symbols(symbol("{X}", new File(dir, "missing.svg").toURI().toString()),
				symbol("{T}", svg("T.svg", "#00FF00").toURI().toString()));
		assertEquals(1, SymbolConverter.renderMissingPngs(name -> false));
		assertNull(SymbolConverter.getLocalPng("missing.svg"));
		assertNotNull(SymbolConverter.getLocalPng("T.svg"));
	}
}
