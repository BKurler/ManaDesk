/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: editable card-finish column
 *     Rémi Dutil (2026) - cell text now shows the label ("Nonfoil"/"Foil"/
 *                         "Etched"), matching the drop-down instead of the
 *                         raw property-map value; drop-down choices are now
 *                         constrained to what the row's own printing actually
 *                         supports (MagicCard#getSupportedFinishes()) -
 *                         Etched can no longer be picked on a printing that
 *                         was never etched
 *     Rémi Dutil (2026) - dropped "Auto" from the drop-down: it's exactly
 *                         Nonfoil / Foil / Etched now, pre-selected to the
 *                         copy's current (possibly derived) finish; picking
 *                         one always sets it explicitly - there's no "clear
 *                         back to auto-derived" pick in the UI any more (the
 *                         underlying auto-derivation for a copy that was
 *                         never explicitly set still exists, it's just not
 *                         something the user chooses from this list)
 *     Rémi Dutil (2026) - a plain MagicCard row (Printings / Scryfall
 *                         database view - no owned copy) now shows the
 *                         printing's own supported finishes, e.g.
 *                         "Nonfoil, Foil" - it used to fall through to
 *                         super.getActualText(), which (via
 *                         MagicCardField.FINISH's old getM(MagicCard)
 *                         default) showed an aggregate of the user's OWN
 *                         copies of that printing instead - blank when
 *                         unowned, and changing your own copy's finish
 *                         visibly changed this column too
 *     Rémi Dutil (2026) - label reverted from "Regular" to "Nonfoil" - match
 *                         Scryfall's own wording, not an invented one
 *     Rémi Dutil (2026) - getActualText(ICardGroup): a "Name" group (several
 *                         printings of the same card, e.g. one etched-only
 *                         and one nonfoil+foil) is a "transient" group, and
 *                         AbstractColumn's default getActualText() for those
 *                         just shows the FIRST child's value for every
 *                         column - a reasonable shortcut for fields that
 *                         never actually differ between printings of the
 *                         same name (Cost, Type, Oracle Text, ...), but
 *                         wrong for Finish, which very much can. Now
 *                         explicitly unions every printing's finishes
 *                         instead - "Etched, Nonfoil, Foil", not just
 *                         whichever printing happened to be first.
 */
package com.reflexit.magiccards.ui.views.columns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardGroup;

/**
 * This copy's finish (Nonfoil/Foil/Etched). Cell text is always the resolved
 * label - never blank, never the raw storage tag - straight from
 * {@link MagicCardPhysical#getFinish()} (which derives a value when none was
 * explicitly set). Editable via a drop-down offering only the finishes the
 * row's own printing actually supports.
 */
public class FinishColumn extends GenColumn {
	public FinishColumn() {
		super(MagicCardField.FINISH, "Finish");
	}

	@Override
	public int getColumnWidth() {
		return 63; // -30% from 90
	}

	@Override
	protected String getActualText(Object element) {
		if (element instanceof MagicCardPhysical)
			return ((MagicCardPhysical) element).getFinish().getLabel();
		// browsing a printing itself (Printings / Scryfall database view, no
		// specific owned copy) - the finishes THAT PRINTING supports, e.g.
		// "Nonfoil, Foil" - never blank, and never an aggregate of the user's
		// own copies (see MagicCardField.FINISH.getM(MagicCard))
		if (element instanceof MagicCard)
			return CardFinish.joinLabels(((MagicCard) element).getSupportedFinishes());
		if (element instanceof ICardGroup)
			return CardFinish.joinLabels(unionFinishes((ICardGroup) element));
		return super.getActualText(element);
	}

	/** Every finish owned/supported anywhere under this group, recursively -
	 *  a printing's own supported finishes for a MagicCard leaf, this copy's
	 *  actual finish for a MagicCardPhysical leaf, so a mixed group (some
	 *  etched-only, some nonfoil+foil) reads as the true union instead of
	 *  whichever child happened to be first. */
	private static EnumSet<CardFinish> unionFinishes(ICardGroup group) {
		EnumSet<CardFinish> union = EnumSet.noneOf(CardFinish.class);
		for (ICard child : group.getChildrenList()) {
			if (child instanceof ICardGroup) {
				union.addAll(unionFinishes((ICardGroup) child));
			} else if (child instanceof MagicCardPhysical) {
				union.add(((MagicCardPhysical) child).getFinish());
			} else if (child instanceof MagicCard) {
				union.addAll(((MagicCard) child).getSupportedFinishes());
			}
		}
		return union;
	}

	/** {@link CardFinish#values()}, filtered to what the card's printing
	 *  supports, in enum order - the row-specific choice list both the combo
	 *  editor and its index mapping are built from. */
	private static List<CardFinish> allowedFor(MagicCardPhysical card) {
		Set<CardFinish> allowed = card.getBase().getSupportedFinishes();
		List<CardFinish> ordered = new ArrayList<>();
		for (CardFinish f : CardFinish.values())
			if (allowed.contains(f))
				ordered.add(f);
		return ordered.isEmpty() ? java.util.Arrays.asList(CardFinish.values()) : ordered;
	}

	private static String[] choicesFor(List<CardFinish> allowed) {
		String[] choices = new String[allowed.size()];
		for (int i = 0; i < allowed.size(); i++)
			choices[i] = allowed.get(i).getLabel();
		return choices;
	}

	private static int toIndex(List<CardFinish> allowed, CardFinish f) {
		int i = allowed.indexOf(f);
		// the current finish is no longer one of the allowed choices (e.g. the
		// printing's known finishes changed after a DB update) - still show it,
		// appended past the "real" choices, rather than silently losing the value
		return i >= 0 ? i : allowed.size();
	}

	private static CardFinish fromIndex(List<CardFinish> allowed, int idx) {
		return idx >= 0 && idx < allowed.size() ? allowed.get(idx) : null;
	}

	@Override
	public EditingSupport getEditingSupport(final ColumnViewer viewer) {
		return new EditingSupport(viewer) {
			@Override
			protected boolean canEdit(Object element) {
				return element instanceof MagicCardPhysical;
			}

			@Override
			protected CellEditor getCellEditor(Object element) {
				MagicCardPhysical card = (MagicCardPhysical) element;
				List<CardFinish> allowed = allowedFor(card);
				String[] choices = choicesFor(allowed);
				CardFinish current = card.getFinish();
				if (!allowed.contains(current)) {
					// keep the stale/out-of-range value visible & selected instead of
					// silently normalizing it away under the user
					String[] withStale = java.util.Arrays.copyOf(choices, choices.length + 1);
					withStale[choices.length] = current.getLabel() + " (not offered by this printing)";
					choices = withStale;
				}
				ComboBoxCellEditor editor = new ComboBoxCellEditor((Composite) viewer.getControl(), choices,
						SWT.READ_ONLY);
				editor.setValue(toIndex(allowed, current));
				return editor;
			}

			@Override
			protected Object getValue(Object element) {
				MagicCardPhysical card = (MagicCardPhysical) element;
				return toIndex(allowedFor(card), card.getFinish());
			}

			@Override
			protected void setValue(Object element, Object value) {
				if (!(element instanceof MagicCardPhysical))
					return;
				MagicCardPhysical card = (MagicCardPhysical) element;
				int idx = value instanceof Integer ? ((Integer) value).intValue()
						: Integer.parseInt(String.valueOf(value));
				CardFinish next = fromIndex(allowedFor(card), idx);
				if (next == null || card.getFinish() == next)
					return;
				card.setFinish(next);
				Set<MagicCardField> of = Collections.singleton(MagicCardField.FINISH);
				DataManager.getInstance().update(card, of);
			}
		};
	}
}
