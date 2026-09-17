/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: card-finish filter checkbox group
 *     Rémi Dutil (2026) - "Only" checkbox: wires CardFinishes.ONLY_ID (which
 *                         MagicCardFilter already understood, same mechanism
 *                         as Color's never-wired ColorTypes.ONLY_ID) to a real
 *                         control - "Foil" + Only = "is only foil" instead of
 *                         the default "contains foil"
 *     Rémi Dutil (2026) - "Only" label trimmed to just that - the full
 *                         explanation made the whole filter dialog ugly;
 *                         moved to the checkbox's tooltip instead
 *     Rémi Dutil (2026) - replaced "Only" with "And" (CardFinishes.AND_ID):
 *                         checking several finishes now defaults to "any of
 *                         these" with And requiring all of them together,
 *                         instead of Only's "and exclude every unchecked one"
 *     Rémi Dutil (2026) - "And" disabled (greyed out) for views where a row
 *                         can only ever have one finish (Deck/Collection,
 *                         My Cards) - it's only meaningful where a single row
 *                         can offer several finishes at once, i.e. Collector
 *     Rémi Dutil (2026) - "And" redefined as an exact match: a printing must
 *                         offer every checked finish AND none of the
 *                         unchecked ones (was: checked finishes required
 *                         together, but unchecked ones were never excluded)
 */
package com.reflexit.magiccards.ui.preferences.feditors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

import com.reflexit.magiccards.core.model.CardFinishes;
import com.reflexit.magiccards.core.model.ISearchableProperty;

public class CardFinishPreferenceGroup extends MFieldEditorPreferencePage {
	private Group group;
	private final Collection<String> ids = new ArrayList<String>(4);
	private final boolean allowAnd;
	private BooleanFieldEditor and;

	/**
	 * @param allowAnd whether this view's rows can hold more than one finish
	 *            at once (e.g. Collector) - when false, the "And" checkbox is
	 *            still shown (same UI everywhere) but disabled, since it has
	 *            no effect on a row that can only ever match one finish
	 */
	public CardFinishPreferenceGroup(boolean allowAnd) {
		this.allowAnd = allowAnd;
	}

	@Override
	protected void createFieldEditors() {
		this.group = new Group(getFieldEditorParent(), SWT.NONE);
		this.group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		this.group.setText("Finish");
		this.group.setFont(getFieldEditorParent().getFont());
		Composite parent = this.group;
		CardFinishes finishes = CardFinishes.getInstance();
		for (Iterator iterator = finishes.getIds().iterator(); iterator.hasNext();) {
			String id = (String) iterator.next();
			addCheckBox(id, finishes.getNameById(id), parent);
		}
		Label sep = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
		sep.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		// BooleanFieldEditor#getChangeControl is protected - a tiny local subclass
		// is the only way to grab the checkbox to set a tooltip on it
		this.and = new BooleanFieldEditor(CardFinishes.AND_ID, "And", parent) {
			{
				getChangeControl(parent).setToolTipText(allowAnd
						? "Exact match: offers every checked finish and none of the unchecked ones, e.g. \"Etched\" + And = etched only"
						: "Not applicable here: each row can only ever have one finish");
			}
		};
		addField(this.and);
		if (!this.allowAnd)
			this.and.setEnabled(false, parent);
		ids.add(CardFinishes.AND_ID);
	}

	public ISearchableProperty getSearchablePropery() {
		return CardFinishes.getInstance();
	}

	@Override
	public Collection<String> getIds() {
		Collection<String> all = new ArrayList<String>(getSearchablePropery().getIds());
		all.addAll(ids);
		return all;
	}

	private FieldEditor addCheckBox(String id, String name, Composite parent) {
		BooleanFieldEditor editor = new BooleanFieldEditor(id, name, parent);
		addField(editor);
		return editor;
	}

	@Override
	protected void adjustGridLayout() {
		GridLayout layout = (GridLayout) this.group.getLayout();
		layout.marginHeight = 5;
		layout.marginWidth = 5;
		super.adjustGridLayout();
	}
}
