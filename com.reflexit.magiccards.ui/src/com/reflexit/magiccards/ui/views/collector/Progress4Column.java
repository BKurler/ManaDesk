/*
 * Contributors:
 *     Rémi Dutil (2026) - getLeafTarget() override (4, not the default 1):
 *                         the per-card leaf text used to say "yes" as soon
 *                         as ANY copy was owned, never actually checking
 *                         against a target - so "yes (2)" showed even with
 *                         only half a playset. With "Count Finishes
 *                         Separately" on this means 4 of EACH finish, not 4
 *                         total spread across any mix (see ProgressColumn's
 *                         own header for the shared getLeafTarget()/
 *                         sizeCountText() rework this relies on)
 *     Rémi Dutil (2026) - getPercentKey()/getProgressSize() now also branch
 *                         on "Count Finishes Separately", picking
 *                         COUNT4_BY_FINISH/PERCENT4_COMPLETE_BY_FINISH -
 *                         Progress4's own target scales with how many
 *                         finishes a printing supports (an etched-only
 *                         printing needs 4; a nonfoil+foil one needs 4 of
 *                         EACH, i.e. 8) instead of a flat 4 regardless of
 *                         finish, matching getLeafTarget() above. getSetSize()
 *                         is no longer overridden here at all - the base
 *                         class's own already-finish-aware version (which
 *                         picks UNIQUE_COUNT_BY_FINISH, one slot per
 *                         (printing, finish)) is exactly the right
 *                         denominator once multiplied by 4 in getTotal()
 *     Rémi Dutil (2026) - useFractionLeafText() override: a leaf card here
 *                         now reads "X / Y (Z%)", matching the group row
 *                         right above it, instead of "yes"/"partial (N)" -
 *                         with target 4 (or more once finishes are spread
 *                         across several), the count alone leaves the
 *                         reader to work out the denominator themselves;
 *                         plain Progress (target 1) keeps yes/partial/no,
 *                         where that reading is natural
 *     Rémi Dutil (2026) - getPercentKey()/getProgressSize() now also branch
 *                         on "Count Proxies" (GENUINE_COUNT4/GENUINE_COUNT4_BY_FINISH/
 *                         PERCENT4_COMPLETE_GENUINE/PERCENT4_COMPLETE_GENUINE_BY_FINISH),
 *                         mirroring ProgressColumn#getPercentKey()'s own 2x2
 *                         matrix - the group-level number used to always
 *                         include proxy copies no matter what that toggle
 *                         said, while the per-card leaf text (already
 *                         proxy-aware via qualifyingOwnCount) did not, so a
 *                         card whose only copies were excluded proxies could
 *                         show "0/4" on its own row while still padding its
 *                         group's total (e.g. a group reading "3/8 (37%)"
 *                         with every child individually reading "0/4 (0%)")
 */
package com.reflexit.magiccards.ui.views.collector;

import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardGroup;

public class Progress4Column extends ProgressColumn {
	public Progress4Column() {
		super(MagicCardField.PERCENT4_COMPLETE, "Progress4");
	}

	@Override
	protected MagicCardField getPercentKey() {
		boolean proxies = CollectorView.isCountProxies();
		if (CollectorView.isCountFinishesSeparately()) {
			return proxies ? MagicCardField.PERCENT4_COMPLETE_BY_FINISH
					: MagicCardField.PERCENT4_COMPLETE_GENUINE_BY_FINISH;
		}
		return proxies ? MagicCardField.PERCENT4_COMPLETE : MagicCardField.PERCENT4_COMPLETE_GENUINE;
	}

	/** A playset is 4 copies, not 1 - see ProgressColumn#getLeafTarget(). */
	@Override
	protected int getLeafTarget() {
		return 4;
	}

	/** "2 / 8 (25%)" instead of "partial (2)" - see
	 *  ProgressColumn#useFractionLeafText(). */
	@Override
	protected boolean useFractionLeafText() {
		return true;
	}

	@Override
	public int getTotal(ICard element) {
		if (element instanceof ICardGroup) {
			CardGroup cardGroup = (CardGroup) element;
			int size = getSetSize(cardGroup);
			return size * 4;
		}
		return 1;
	}

	@Override
	public int getProgressSize(ICard element) {
		boolean proxies = CollectorView.isCountProxies();
		boolean byFinish = CollectorView.isCountFinishesSeparately();
		MagicCardField field = byFinish
				? (proxies ? MagicCardField.COUNT4_BY_FINISH : MagicCardField.GENUINE_COUNT4_BY_FINISH)
				: (proxies ? MagicCardField.COUNT4 : MagicCardField.GENUINE_COUNT4);
		return element.getInt(field);
	}

	@Override
	public String getToolTipText(Object element) {
		if (element instanceof ICardGroup) {
			return "X/Y (Z%) - Means you have X cards you own (max 4 each)\n out of Y possible in this class (Y is number of cards in set/group x 4), which represents Z%";
		}
		return super.getToolTipText(element);
	}

	@Override
	public String getColumnTooltip() {
		return "Collection progress for sets of 4 cards";
	}
}
