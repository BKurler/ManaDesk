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

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.storage.ILocatable;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Exports a deck as a Cockatrice deck file ({@code .cod}): the deck in the {@code main} zone, the sideboard in the
 * {@code side} zone. Cockatrice only knows names and counts, so the printings of a card are added up under its
 * name. The extra list (tokens, emblems...) is left out. See {@link CockatriceImportDelegate}.
 */
public class CockatriceExportDelegate extends AbstractExportDelegate<IMagicCard> {

	@Override
	public void export(ICoreProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		if (monitor == null)
			monitor = ICoreProgressMonitor.NONE;
		monitor.beginTask("Exporting to " + getLabel() + "...", store.getSize());
		try {
			Map<String, Integer> main = new LinkedHashMap<>();
			Map<String, Integer> side = new LinkedHashMap<>();
			for (IMagicCard card : store) {
				Location loc = card instanceof ILocatable ? ((ILocatable) card).getLocation() : null;
				if (loc != null && loc.isExtra())
					continue;
				String name = ManaBoxCsvExportDelegate.name(card.getName());
				if (name.isEmpty())
					continue;
				int count = card instanceof MagicCardPhysical ? ((MagicCardPhysical) card).getCount() : 1;
				if (count <= 0)
					continue;
				(loc != null && loc.isSideboard() ? side : main).merge(name, count, Integer::sum);
				monitor.worked(1);
			}
			stream.print(write(getName(), main, side));
		} finally {
			monitor.done();
		}
	}

	/** The text of the {@code .cod} file. */
	static String write(String deckName, Map<String, Integer> main, Map<String, Integer> side) {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<cockatrice_deck version=\"1\">\n");
		sb.append("    <deckname>").append(escape(deckName)).append("</deckname>\n");
		sb.append("    <comments></comments>\n");
		zone(sb, "main", main);
		zone(sb, "side", side);
		sb.append("</cockatrice_deck>\n");
		return sb.toString();
	}

	private static void zone(StringBuilder sb, String zone, Map<String, Integer> cards) {
		sb.append("    <zone name=\"").append(zone).append("\">\n");
		for (Map.Entry<String, Integer> e : cards.entrySet())
			sb.append("        <card number=\"").append(e.getValue()).append("\" name=\"").append(escape(e.getKey()))
					.append("\"/>\n");
		sb.append("    </zone>\n");
	}

	/** XML text / attribute escaping. */
	static String escape(String s) {
		if (s == null)
			return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	@Override
	public boolean isColumnChoiceSupported() {
		return false;
	}

	@Override
	public String getContentSlug() {
		return "cockatrice";
	}

	@Override
	public String getExample() {
		LinkedHashMap<String, Integer> main = new LinkedHashMap<>();
		main.put("Lightning Bolt", 4);
		main.put("Counterspell", 2);
		LinkedHashMap<String, Integer> side = new LinkedHashMap<>();
		side.put("Negate", 2);
		return write("My deck", main, side);
	}
}
