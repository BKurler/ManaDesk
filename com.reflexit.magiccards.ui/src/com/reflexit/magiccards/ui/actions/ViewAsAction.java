/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.ui.actions;

import java.util.Collection;
import java.util.function.Consumer;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;

import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.preferences.PreferenceConstants;
import com.reflexit.magiccards.ui.views.Presentation;

public class ViewAsAction extends DropDownAction<Presentation> {
	private static final boolean DEBUG = false;
	private IPreferenceStore store;

	public ViewAsAction(Collection<Presentation> pres, IPreferenceStore store, Consumer<Presentation> onSelect) {
		super(pres, "View As", MagicUIActivator.getImageDescriptor("icons/obj16/pres_tree16.png"), onSelect);
		this.store = store;
		setToolTipText("View As");
		// Show the icon of the presentation that is actually active (default:
		// Table), not a hard-coded tree icon.
		syncIcon();
	}

	/** Re-point the toolbar icon at whatever presentation is currently in effect. */
	public void syncIcon() {
		setImageDescriptor(getImageDesc(currentPresentation()));
	}

	/**
	 * The presentation currently in effect: the stored value when it is a valid
	 * {@link Presentation} name, otherwise {@link #getDefault()} (Table). An
	 * unset preference reads back as "" from {@link IPreferenceStore}, so it must
	 * be treated as "use the default", not "nothing selected".
	 */
	private Presentation currentPresentation() {
		Presentation res = getDefault();
		String cur = null;
		if (store != null) {
			cur = store.getString(PreferenceConstants.PRESENTATION_VIEW);
			if (cur != null && !cur.isEmpty()) {
				try {
					res = Presentation.valueOf(cur);
				} catch (RuntimeException e) {
					// fall through to default
				}
			}
		}
		if (DEBUG)
			System.out.println("[ViewAsAction] currentPresentation: stored='" + cur + "' -> " + res
					+ " (store=" + (store == null ? "null" : store.getClass().getSimpleName()) + ")");
		return res;
	}

	@Override
	public Action createItemAction(Presentation pres) {
		Action action = super.createItemAction(pres);
		action.setImageDescriptor(getImageDesc(pres));
		return action;
	}

	private ImageDescriptor getImageDesc(Presentation pres) {
		switch (pres) {
		case TREE:
			return MagicUIActivator.getImageDescriptor("icons/obj16/pres_tree16.png");
		case TABLE:
			return MagicUIActivator.getImageDescriptor("icons/obj16/pres_list16.png");
		case SPLITTREE:
			return MagicUIActivator.getImageDescriptor("icons/obj16/pres_splittree16.png");
		case GALLERY:
			return MagicUIActivator.getImageDescriptor("icons/obj16/pres_gallery16.png");
		default:
			break;
		}
		return null;
	}

	@Override
	public String getText(Object element) {
		if (element instanceof Presentation) {
			return ((Presentation) element).getLabel();
		}
		return super.getText();
	}

	@Override
	protected Presentation getDefault() {
		return Presentation.TABLE;
	}

	@Override
	public boolean isChecked(Object element) {
		return currentPresentation() == element;
	}

	@Override
	protected void actionOnSelectItem(Presentation pres) {
		if (store != null)
			store.setValue(PreferenceConstants.PRESENTATION_VIEW, pres.key());
		setImageDescriptor(getImageDesc(pres));
		super.actionOnSelectItem(pres);
	}
}