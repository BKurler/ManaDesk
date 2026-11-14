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

import java.io.IOException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Imports a Cockatrice deck ({@code .cod}). The file is XML and only knows card names and counts, in zones:
 *
 * <pre>
 * &lt;cockatrice_deck version="1"&gt;
 *   &lt;deckname&gt;My deck&lt;/deckname&gt;
 *   &lt;zone name="main"&gt;&lt;card number="4" name="Lightning Bolt"/&gt;&lt;/zone&gt;
 *   &lt;zone name="side"&gt;&lt;card number="2" name="Negate"/&gt;&lt;/zone&gt;
 * &lt;/cockatrice_deck&gt;
 * </pre>
 *
 * The {@code main} zone is the deck, {@code side} the sideboard. The deck name and comments are ignored (the
 * destination is chosen in the wizard) and so are the other zones (Cockatrice's token zone: the cards are looked up
 * by name only). There is no set in the file: the preview resolves each name like any text list.
 */
public class CockatriceImportDelegate extends AbstractImportDelegate {
	static final String ROOT = "cockatrice_deck";

	@Override
	protected void doRun(ICoreProgressMonitor monitor) throws IOException {
		importData.setFields(new ICardField[] { MagicCardField.COUNT, MagicCardField.NAME });
		Document doc = parse();
		Element root = doc.getDocumentElement();
		if (root == null || !ROOT.equals(root.getNodeName()))
			throw new IOException("Not a Cockatrice deck: the root element must be <" + ROOT + ">");
		NodeList zones = root.getElementsByTagName("zone");
		for (int i = 0; i < zones.getLength(); i++) {
			Element zone = (Element) zones.item(i);
			String name = zone.getAttribute("name").trim();
			boolean side;
			if ("main".equalsIgnoreCase(name))
				side = false;
			else if ("side".equalsIgnoreCase(name))
				side = true;
			else
				continue; // tokens...
			for (Node n = zone.getFirstChild(); n != null; n = n.getNextSibling()) {
				if (n instanceof Element && "card".equals(n.getNodeName())) {
					MagicCardPhysical card = readCard((Element) n, side);
					if (card != null) {
						importCard(card);
						monitor.worked(1);
					}
				}
			}
		}
	}

	private MagicCardPhysical readCard(Element e, boolean side) {
		lineNum++;
		String name = e.getAttribute("name").trim();
		if (name.isEmpty())
			return null;
		MagicCardPhysical card = createDefaultCard();
		card.setCount(parseCount(e.getAttribute("number")));
		card.set(MagicCardField.NAME, name);
		if (side)
			card.setLocation(getSideboardLocation());
		return card;
	}

	/** A missing, unreadable or non-positive count is 1 (as the other text importers). */
	static int parseCount(String s) {
		try {
			int n = Integer.parseInt(s.trim());
			return n <= 0 ? 1 : n;
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	private Document parse() throws IOException {
		try {
			DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
			// a deck file is untrusted input: no DTD, no external entities
			f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			f.setXIncludeAware(false);
			f.setExpandEntityReferences(false);
			DocumentBuilder b = f.newDocumentBuilder();
			b.setErrorHandler(null); // no console noise: the error comes back as the exception
			return b.parse(getStream());
		} catch (ParserConfigurationException | SAXException e) {
			throw new IOException("Not a Cockatrice deck: " + e.getMessage(), e);
		}
	}

	@Override
	public String getExample() {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" //
				+ "<cockatrice_deck version=\"1\">\n" //
				+ "    <deckname>My deck</deckname>\n" //
				+ "    <comments></comments>\n" //
				+ "    <zone name=\"main\">\n" //
				+ "        <card number=\"4\" name=\"Lightning Bolt\"/>\n" //
				+ "        <card number=\"2\" name=\"Counterspell\"/>\n" //
				+ "    </zone>\n" //
				+ "    <zone name=\"side\">\n" //
				+ "        <card number=\"2\" name=\"Negate\"/>\n" //
				+ "    </zone>\n" //
				+ "</cockatrice_deck>\n";
	}
}
