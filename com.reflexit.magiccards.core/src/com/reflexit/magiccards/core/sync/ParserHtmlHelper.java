/*
 * Contributors:
 *     Rémi Dutil (2026) - removed the HTML scraping helpers and the duplicate Gatherer URL builders
 *                         (GatherHelper has the live ones); nothing called them
 */

package com.reflexit.magiccards.core.sync;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.MagicCard;

public abstract class ParserHtmlHelper {
	public static interface ILoadCardHander {
		void handleCard(MagicCard card);

		void handleSecondary(MagicCard primary, MagicCard secondary);

		void handleEdition(Edition ed);

		void setCardCount(int count);

		int getCardCount();

		int getRealCount();

		default void onEnd() {
		};
	}

	public static class OutputHandler implements ILoadCardHander {
		private PrintStream out;
		private boolean loadLandPrintings;
		private boolean loadOtherPrintings;
		private int cardCount;
		protected int count;

		public OutputHandler(PrintStream st, boolean loadLandPrintings, boolean loadOtherPrintings) {
			this.out = st;
			this.loadLandPrintings = loadLandPrintings;
			this.loadOtherPrintings = loadOtherPrintings;
		}

		@Override
		public void handleCard(MagicCard card) {
			count++;
			printCard(card);
		}

		protected void printCard(MagicCard card) {
			TextPrinter.print(card, this.out);
		}

		@Override
		public void handleEdition(Edition ed) {
			Editions.getInstance().addEdition(ed);
		}

		@Override
		public void handleSecondary(MagicCard primary, MagicCard secondary) {
			if ((loadLandPrintings && primary.getSet() != null && primary.getSet().equals(secondary.getSet()))
					|| loadOtherPrintings) {
				handleCard(secondary);
			}
		}

		public void printHeader() {
			TextPrinter.printHeader(out);
		}

		@Override
		public void setCardCount(int count) {
			this.cardCount = count;
		}

		@Override
		public int getCardCount() {
			return cardCount;
		}

		@Override
		public int getRealCount() {
			return count;
		}
	}


	public static class StashLoadHandler implements ILoadCardHander {
		private int count;
		private int cardCount;
		private ArrayList<MagicCard> primary = new ArrayList<>();
		private ArrayList<MagicCard> secondary = new ArrayList<>();

		@Override
		public void setCardCount(int count) {
			cardCount = count;
		}

		@Override
		public void handleSecondary(MagicCard primary, MagicCard secondary) {
			this.secondary.add(secondary);
		}

		@Override
		public void handleEdition(Edition ed) {
			Editions eds = Editions.getInstance();
			String abbr = ed.getMainAbbreviation();
			Edition exitsing = eds.getEditionByAbbr(abbr);
			if (exitsing != null) {
				if (!exitsing.getName().equals(ed.getName()))
					exitsing.addNameAlias(ed.getName());
			} else {
				Editions.getInstance().addEdition(ed);
			}
		}

		@Override
		public void handleCard(MagicCard card) {
			primary.add(card);
			count++;
		}

		@Override
		public int getCardCount() {
			return cardCount;
		}

		@Override
		public int getRealCount() {
			return count;
		}

		public Collection<MagicCard> getPrimary() {
			return primary;
		}

		public Collection<MagicCard> getSecondary() {
			return secondary;
		}
	}

	public ParserHtmlHelper() {
		// !!! RD Not supported anymore
		throw new UnsupportedOperationException();

		// super();
	}

	public abstract boolean loadSingleUrl(URL url, ILoadCardHander handler) throws IOException;
}