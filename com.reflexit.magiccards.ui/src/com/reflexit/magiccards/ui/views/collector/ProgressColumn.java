/*
 * Contributors:
 *     Rémi Dutil (2026) - completion % counts genuine copies only unless
 *                         "Count Proxies" is on in the Collector view
 *     Rémi Dutil (2026) - getPercentKey()/getSetSize()/getProgressSize() now
 *                         also branch on "Count Finishes Separately" - a
 *                         second, independent axis crossed with the proxies
 *                         one, so all three must pick the same *_BY_FINISH
 *                         field together or the displayed fraction and the
 *                         bar's fill percent would disagree. getSetSize()
 *                         becoming finish-aware here turned out to silently
 *                         reach Progress4Column too (it inherits this method
 *                         rather than overriding it) - Progress4Column
 *                         doesn't need its own override, though: it just
 *                         multiplies this same value by 4 in its own
 *                         getTotal()
 *     Rémi Dutil (2026) - getToolTipText(): a per-card "yes (3)" cell only
 *                         ever showed the total owned count, no hint of
 *                         finish - added a finish breakdown ("2 Nonfoil, 1
 *                         Foil") to the tooltip so it's clear on hover
 *                         without widening the cell or adding columns
 *     Rémi Dutil (2026) - getSizeCountText(): "yes" meant "own at least one
 *                         copy" regardless of finish, so owning only the
 *                         nonfoil of a foil+nonfoil printing looked the same
 *                         as owning both - when "Count Finishes Separately"
 *                         is on, a MagicCard leaf now reads "partial" instead
 *                         of "yes" unless every finish the printing supports
 *                         is actually owned; the tooltip lists what's missing
 *     Rémi Dutil (2026) - qualifyingOwnCount(): the leaf cell/tooltip count
 *                         used MagicCard#getOwnCount(), which always counts
 *                         proxy copies regardless of "Count Proxies" - now
 *                         proxy-aware like the group-level numbers already
 *                         were, via the same qualifies-a-copy test
 *                         ownedFinishes() uses. Also: the "(N)" count is now
 *                         always shown, not just above a >1 threshold -
 *                         "yes (1)" instead of bare "yes"
 *     Rémi Dutil (2026) - finishBreakdown(): when "Count Proxies" is on (so
 *                         a proxy copy is actually included in the count),
 *                         the tooltip now also says how many of each
 *                         finish's copies are proxies - "2 Nonfoil [1
 *                         proxy], 1 Foil" - instead of silently folding
 *                         proxy and genuine copies into one number
 *     Rémi Dutil (2026) - getToolTipText(): the ownCount==0 message wrongly
 *                         borrowed the Deck/Collection concept of a
 *                         "virtual" card - Collector has no virtual slots at
 *                         all, a row here is just a printing you either own
 *                         or don't, so the message no longer mentions it;
 *                         hasExcludedProxyOnly() distinguishes "own nothing"
 *                         from "own it, but only as an excluded proxy"
 *     Rémi Dutil (2026) - getLeafTarget()/sizeCountText(): "yes" never
 *                         actually checked how many copies were owned
 *                         against a target - any ownership at all read as
 *                         "yes", which happened to look right for plain
 *                         Progress (target 1) but was wrong for Progress4
 *                         (target 4: owning 2 of 4 needed showed "yes (2)").
 *                         Reworked around an explicit per-slot target,
 *                         checked per finish when "Count Finishes
 *                         Separately" is on (so Progress4 in that mode means
 *                         4 of EACH finish) or against the total otherwise;
 *                         dropped isFinishAwareLeafText() - Progress4 was
 *                         never supposed to ignore finishes outright, only
 *                         to want a different target than 1
 *     Rémi Dutil (2026) - useFractionLeafText()/sizeCountFraction(): plain
 *                         Progress keeps its yes/partial/no leaf text, but
 *                         Progress4's own "2/8 (25%)" now matches its group
 *                         row's own X/Y(Z%) format exactly instead of the
 *                         same yes/partial(N) wording, which left the
 *                         denominator for a Progress4 leaf up to the reader
 *                         to work out for themselves. Tooltip unchanged.
 *     Rémi Dutil (2026) - getColumnWidth() override: doubled from
 *                         AbstractImageColumn's inherited 40 - also covers
 *                         Progress4Column, which never had its own override
 */
package com.reflexit.magiccards.ui.views.collector;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.ui.PlatformUI;

import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.IMagicCardPhysical;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.ui.views.columns.AbstractImageColumn;

public class ProgressColumn extends AbstractImageColumn {
	protected MagicCardField getPercentKey() {
		boolean proxies = CollectorView.isCountProxies();
		if (CollectorView.isCountFinishesSeparately()) {
			return proxies ? MagicCardField.PERCENT_COMPLETE_BY_FINISH : MagicCardField.PERCENT_COMPLETE_GENUINE_BY_FINISH;
		}
		return proxies ? MagicCardField.PERCENT_COMPLETE : MagicCardField.PERCENT_COMPLETE_GENUINE;
	}

	final Color barColor = PlatformUI.getWorkbench().getDisplay().getSystemColor(SWT.COLOR_GREEN);
	final Color missColor = PlatformUI.getWorkbench().getDisplay().getSystemColor(SWT.COLOR_RED);
	final Color partColor = PlatformUI.getWorkbench().getDisplay().getSystemColor(SWT.COLOR_YELLOW);

	public ProgressColumn(ICardField field, String columnName) {
		super(field, columnName);
	}

	public ProgressColumn() {
		super(MagicCardField.PERCENT_COMPLETE, "Progress");
	}

	/** Doubled from AbstractImageColumn's inherited 40, then +10px, then
	 *  +15px, then +10px, then +15px more - the bar plus its "X / Y (Z%)"
	 *  text needs more room than a plain image column; also inherited by
	 *  Progress4Column, which never overrode this itself. */
	@Override
	public int getColumnWidth() {
		return 130;
	}

	@Override
	public String getText(Object element) {
		if (element instanceof ICardGroup) {
			CardGroup cardGroup = (CardGroup) element;
			int size = getTotal(cardGroup);
			int count = getProgressSize(cardGroup);
			float per = cardGroup.getFloat(getPercentKey());
			if (per < 5 && per > 0)
				return String.format("%3d / %3d (%2.1f%%)", count, size, per);
			else if (size > 0)
				return String.format("%3d / %3d (%2d%%)", count, size, (int) per);
			else
				return String.format("%3d / ?", count);
		} else if (element instanceof IMagicCard) {
			return getSizeCountText(element);
		}
		return super.getText(element);
	}

	public int getTotal(ICard element) {
		if (element instanceof ICardGroup) {
			CardGroup cardGroup = (CardGroup) element;
			int size = getSetSize(cardGroup);
			return size;
		}
		return 1;
	}

	public int getProgressSize(ICard element) {
		if (element instanceof ICardGroup) {
			CardGroup cardGroup = (CardGroup) element;
			boolean proxies = CollectorView.isCountProxies();
			if (CollectorView.isCountFinishesSeparately()) {
				return cardGroup.getInt(
						proxies ? MagicCardField.OWN_UNIQUE_BY_FINISH : MagicCardField.GENUINE_OWN_UNIQUE_BY_FINISH);
			}
			return proxies ? cardGroup.getOwnUnique() : cardGroup.getGenuineOwnUnique();
		}
		return 0;
	}

	@Override
	public String getToolTipText(Object element) {
		if (element instanceof MagicCard) {
			MagicCard mc = (MagicCard) element;
			if (mc.getPhysicalCards().size() == 0)
				return "This card is not in any of your card collections";
			int ownCount = qualifyingOwnCount(mc);
			if (ownCount == 0) {
				if (hasExcludedProxyOnly(mc))
					return "You only own proxy copies of this printing - excluded from the count"
							+ " (see \"Count Proxies\")";
				return "You don't own any copies of this printing";
			}
			String msg = "You own " + ownCount + " of these cards" + finishBreakdown(mc);
			if (CollectorView.isCountFinishesSeparately()) {
				String missing = missingFinishesText(mc);
				if (!missing.isEmpty())
					msg += " - missing " + missing;
			}
			return msg;
		}
		if (element instanceof MagicCardPhysical) {
			MagicCardPhysical card = (MagicCardPhysical) element;
			if (card.isOwn()) {
				return "You own " + card.getCount() + " of these cards";
			} else {
				return "This means you have " + card.getCount() + " virtual cards (you don't own them)";
			}
		}
		if (element instanceof ICardGroup) {
			return "X/Y (Z%) - Means you have X unique cards you own out of Y possible in this class, which represents Z%";
		}
		return null;
	}

	/** " (2 Nonfoil [1 proxy], 1 Foil)" - which finishes make up an owned
	 *  count, in {@link CardFinish} order, with a "[N proxy]" note on any
	 *  finish that includes one - only relevant when "Count Proxies" is on,
	 *  since an excluded proxy copy never reaches {@code counts}/
	 *  {@code proxyCounts} at all. The "yes (3)"-style cell text is a plain
	 *  total with no hint of finish (or of proxy vs genuine), which reads as
	 *  unclear once "Count Finishes Separately"/"Count Proxies" are in play -
	 *  this spells it out on hover without adding more columns or
	 *  lengthening the cell text itself. */
	private String finishBreakdown(MagicCard card) {
		boolean proxies = CollectorView.isCountProxies();
		java.util.EnumMap<CardFinish, Integer> counts = new java.util.EnumMap<>(CardFinish.class);
		java.util.EnumMap<CardFinish, Integer> proxyCounts = new java.util.EnumMap<>(CardFinish.class);
		for (MagicCardPhysical p : card.getPhysicalCards()) {
			if (!p.isOwn() || (p.isProxy() && !proxies))
				continue;
			counts.merge(p.getFinish(), p.getCount(), Integer::sum);
			if (p.isProxy())
				proxyCounts.merge(p.getFinish(), p.getCount(), Integer::sum);
		}
		if (counts.isEmpty())
			return "";
		StringBuilder sb = new StringBuilder(" (");
		boolean first = true;
		for (CardFinish f : CardFinish.values()) {
			Integer n = counts.get(f);
			if (n == null || n == 0)
				continue;
			if (!first)
				sb.append(", ");
			sb.append(n).append(" ").append(f.getLabel());
			Integer pn = proxyCounts.get(f);
			if (pn != null && pn > 0) {
				sb.append(" [").append(pn).append(pn == 1 ? " proxy]" : " proxies]");
			}
			first = false;
		}
		sb.append(")");
		return sb.toString();
	}

	/** Which finishes have at least one qualifying owned copy - derived from
	 *  {@link #qualifyingCountsByFinish}, so it never disagrees with it (or,
	 *  through that, with the group-level *_BY_FINISH aggregators) about
	 *  whether a finish is "owned". */
	private java.util.EnumSet<CardFinish> ownedFinishes(MagicCard card) {
		java.util.EnumSet<CardFinish> owned = java.util.EnumSet.noneOf(CardFinish.class);
		owned.addAll(qualifyingCountsByFinish(card).keySet());
		return owned;
	}

	/** How many qualifying copies of the printing are owned, per finish - a
	 *  proxy copy only qualifies when "Count Proxies" is on, matching
	 *  exactly what the group-level *_BY_FINISH aggregators count (see
	 *  FieldOwnUniqueByFinishAggregator/FieldGenuineOwnUniqueByFinishAggregator)
	 *  so the per-card text and the group percentage never disagree about
	 *  whether a finish is "owned". A finish with no qualifying copies is
	 *  simply absent from the map (not present with a 0). */
	private java.util.EnumMap<CardFinish, Integer> qualifyingCountsByFinish(MagicCard card) {
		boolean proxies = CollectorView.isCountProxies();
		java.util.EnumMap<CardFinish, Integer> counts = new java.util.EnumMap<>(CardFinish.class);
		for (MagicCardPhysical p : card.getPhysicalCards()) {
			if (p.isOwn() && (proxies || !p.isProxy()))
				counts.merge(p.getFinish(), p.getCount(), Integer::sum);
		}
		return counts;
	}

	/** Total owned copies that qualify toward completion - a proxy copy only
	 *  counts when "Count Proxies" is on. {@link MagicCard#getOwnCount()}
	 *  always counts every owned copy regardless of that toggle, which is
	 *  what made the tooltip/cell count disagree with the group-level
	 *  percentage whenever proxies were excluded. */
	private int qualifyingOwnCount(MagicCard card) {
		boolean proxies = CollectorView.isCountProxies();
		int count = 0;
		for (MagicCardPhysical p : card.getPhysicalCards()) {
			if (p.isOwn() && (proxies || !p.isProxy()))
				count += p.getCount();
		}
		return count;
	}

	/** True when this printing's only owned copies are proxies that "Count
	 *  Proxies" is currently excluding - i.e. {@link #qualifyingOwnCount}
	 *  reads 0 only because of that toggle, not because nothing is owned at
	 *  all. Lets the tooltip say the accurate thing instead of the
	 *  Deck/Collection-only concept of a "virtual" (uncommitted) card, which
	 *  doesn't apply here - Collector has no virtual slots, only owned or
	 *  not-owned printings. */
	private boolean hasExcludedProxyOnly(MagicCard card) {
		for (MagicCardPhysical p : card.getPhysicalCards()) {
			if (p.isOwn() && p.isProxy())
				return true;
		}
		return false;
	}

	/** "Foil, Etched" - the finishes this printing supports that aren't owned
	 *  (per {@link #ownedFinishes}), or "" once every supported finish is. */
	private String missingFinishesText(MagicCard card) {
		java.util.Set<CardFinish> owned = ownedFinishes(card);
		StringBuilder sb = new StringBuilder();
		for (CardFinish f : card.getSupportedFinishes()) {
			if (owned.contains(f))
				continue;
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(f.getLabel());
		}
		return sb.toString();
	}

	public int getSetSize(CardGroup cardGroup) {
		if (CollectorView.isCountFinishesSeparately())
			return cardGroup.getInt(MagicCardField.UNIQUE_COUNT_BY_FINISH);
		return cardGroup.getUniqueCount();
	}

	@Override
	protected void handleEraseEvent(Event event) {
		// use standard text paint
		// super.handleEraseEvent(event);
	}

	@Override
	public void handlePaintEvent(Event event) {
		Item item = (Item) event.item;
		Object row = item.getData();
		int x = event.x;
		int y = event.y;
		Rectangle bounds = getBounds(event);
		x = bounds.x;
		int w = bounds.width;
		int h = bounds.height;
		float per = 100;
		if (row instanceof ICardGroup) {
			Float per1 = (Float) ((CardGroup) row).get(getPercentKey());
			if (per1 == null)
				per = Float.valueOf(0);
			else
				per = per1;
		} else if (row instanceof MagicCard && ((MagicCard) row).getOwnCount() == 0) {
			per = 0;
		} else if (row instanceof MagicCardPhysical
				&& (((MagicCardPhysical) row).getCount() == 0 || ((IMagicCardPhysical) row).isOwn() == false)) {
			per = 0;
		}
		GC gc = event.gc;
		if (per > 0) {
			int width = (int) (w * (per > 60 ? 60 : per) / 100);
			gc.setBackground(barColor);
			gc.setForeground(partColor);
			gc.setAlpha(64);
			gc.fillGradientRectangle(x, y, w - width, h, false);
			gc.fillRectangle(x + w - width, y, width, h);
		} else {
			gc.setBackground(missColor);
			gc.setForeground(partColor);
			gc.setAlpha(64);
			gc.fillRectangle(x, y, w, h);
		}
	}

	public String getSizeCountText(Object element) {
		if (element instanceof MagicCard) {
			MagicCard mc = (MagicCard) element;
			boolean byFinish = CollectorView.isCountFinishesSeparately();
			int target = getLeafTarget();
			return useFractionLeafText() ? sizeCountFraction(mc, byFinish, target)
					: sizeCountText(mc, byFinish, target);
		}
		if (element instanceof MagicCardPhysical) {
			MagicCardPhysical p = (MagicCardPhysical) element;
			if (!p.isOwn())
				return "no";
			int count = p.getCount();
			if (count == 0)
				return "no";
			return (count >= getLeafTarget() ? "yes" : "partial") + " (" + count + ")";
		}
		return "no";
	}

	/** How many copies of a slot (the whole printing, or - when "Count
	 *  Finishes Separately" is on - each finish individually) it takes to
	 *  call that slot complete. 1 for plain Progress ("do you own this at
	 *  all"); overridden to 4 by Progress4Column ("do you have a full
	 *  playset"). */
	protected int getLeafTarget() {
		return 1;
	}

	/** Whether a leaf card's cell text should be an "X / Y (Z%)" fraction
	 *  (matching the group row right above it) instead of "yes"/"partial"/
	 *  "no". False (the default) for plain Progress, where target 1 makes
	 *  yes/no the natural reading ("do you own this at all"); overridden to
	 *  true by Progress4Column - with target 4, a bare "partial (2)" leaves
	 *  the reader to work out 2 of how many themselves, whereas the group
	 *  row right above already shows that as a fraction, so a card with no
	 *  sub-rows of its own (a single printing, no group to expand into)
	 *  should read the same way, not switch to a different vocabulary just
	 *  because it happens to be a leaf. */
	protected boolean useFractionLeafText() {
		return false;
	}

	/** "yes" once every relevant slot meets {@code target}, "partial" once
	 *  at least one copy is owned but some slot doesn't, "no" if nothing
	 *  qualifies - plus the total owned count, ALWAYS shown in parens (not
	 *  just above some threshold, so "yes (1)" is exactly as explicit as
	 *  "yes (4)"). With {@code byFinish} true, a "slot" is each finish the
	 *  printing supports and the count against {@code target} is checked
	 *  per finish (so Progress4 + "Count Finishes Separately" means 4 of
	 *  EACH finish, not 4 total spread across any mix); with it false, the
	 *  whole printing is the one slot and the total is checked directly.
	 *  Both counting paths are proxy-aware via
	 *  {@link #qualifyingCountsByFinish}/{@link #qualifyingOwnCount}, so
	 *  they never disagree with the group-level percentage above them about
	 *  whether a proxy copy counts. */
	private String sizeCountText(MagicCard card, boolean byFinish, int target) {
		if (byFinish) {
			java.util.EnumMap<CardFinish, Integer> perFinish = qualifyingCountsByFinish(card);
			int total = 0;
			boolean anyOwned = false;
			boolean allMet = true;
			for (CardFinish f : card.getSupportedFinishes()) {
				int n = perFinish.getOrDefault(f, 0);
				total += n;
				if (n > 0)
					anyOwned = true;
				if (n < target)
					allMet = false;
			}
			if (!anyOwned)
				return "no";
			return (allMet ? "yes" : "partial") + " (" + total + ")";
		}
		int count = qualifyingOwnCount(card);
		if (count == 0)
			return "no";
		return (count >= target ? "yes" : "partial") + " (" + count + ")";
	}

	/** "X / Y (Z%)" for a leaf card, same shape and rounding as the group
	 *  row's own {@code getText()} formatting just above it. Each slot's
	 *  contribution to X is capped at {@code target} (owning 6 when the
	 *  target is 4 still only contributes 4), matching how the group-level
	 *  *_BY_FINISH aggregators cap each slot too - so a card's own fraction
	 *  and its parent group's fraction stay addable/consistent. */
	private String sizeCountFraction(MagicCard card, boolean byFinish, int target) {
		int count;
		int size;
		if (byFinish) {
			java.util.EnumMap<CardFinish, Integer> perFinish = qualifyingCountsByFinish(card);
			java.util.Set<CardFinish> supported = card.getSupportedFinishes();
			count = 0;
			for (CardFinish f : supported) {
				count += Math.min(perFinish.getOrDefault(f, 0), target);
			}
			size = supported.size() * target;
		} else {
			count = Math.min(qualifyingOwnCount(card), target);
			size = target;
		}
		float per = size > 0 ? count * 100f / size : 0;
		if (per < 5 && per > 0)
			return String.format("%d / %d (%.1f%%)", count, size, per);
		return String.format("%d / %d (%d%%)", count, size, (int) per);
	}
}
