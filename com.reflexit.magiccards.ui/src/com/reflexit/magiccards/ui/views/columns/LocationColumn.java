/*
 * Contributors:
 *     Rémi Dutil (2026) - editing the location is a real move: the picker only
 *                         lists allowed destinations (no read-only list, no
 *                         virtual list for an owned card), a virtual card
 *                         moved into a non-virtual list becomes Own after a
 *                         confirmation, and it goes through
 *                         DataManager#moveCards (was DataManager#move, which
 *                         skipped every rule); the picker shows 20 rows and
 *                         can hide sideboards / extra lists
 */
package com.reflexit.magiccards.ui.views.columns;

import java.util.Collections;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.DialogCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ISelectionValidator;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.OwnershipRules;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.core.model.nav.LocationPath;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.core.model.storage.ILocatable;
import com.reflexit.magiccards.ui.dialogs.CardNavigatorSelectionDialog;
import com.reflexit.magiccards.ui.dialogs.OwnershipConfirmation;

/**
 * @author Alena
 * 
 */
public class LocationColumn extends GenColumn {
	/**
	 * @param columnName
	 */
	public LocationColumn() {
		super(MagicCardField.LOCATION, "Location");
	}

	@Override
	public int getColumnWidth() {
		return 100;
	}

	@Override
	public String getText(Object element) {
		if (element instanceof ILocatable) {
			ILocatable m = (ILocatable) element;
			return pretty(m.getLocation());
		}
		return super.getText(element);
	}

	public String pretty(Location locc) {
		if (locc == null)
			return "";
		String loc = locc.toString();
		if (loc == null)
			return "";
		if (loc.endsWith(".xml")) {
			loc = loc.replaceFirst("\\.xml$", "");
		}
		loc = loc.replaceFirst("Collections/", "C/");
		loc = loc.replaceFirst("Decks/", "D/");
		return loc;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.reflexit.magiccards.ui.views.columns.ColumnManager#getEditingSupport
	 * (org.eclipse.jface.viewers.ColumnViewer)
	 */
	@Override
	public EditingSupport getEditingSupport(final ColumnViewer viewer) {
		return new EditingSupport(viewer) {
			@Override
			protected boolean canEdit(Object element) {
				if (element instanceof MagicCardPhysical) {
					if (viewer.getInput() instanceof IFilteredCardStore)
						return true;
				}
				return false;
			}

			@Override
			protected CellEditor getCellEditor(final Object element) {
				final String iniLoc = (String) getValue(element);
				final CardElement cardElement = DataManager.getInstance().getModelRoot().findElement(iniLoc);
				DialogCellEditor editor = new DialogCellEditor((Composite) viewer.getControl(), SWT.NONE) {
					@Override
					protected Object openDialogBox(Control cellEditorWindow) {
						CardNavigatorSelectionDialog d = new CardNavigatorSelectionDialog(
								cellEditorWindow.getShell(),
								DataManager.getInstance().getModelRoot().getMyCardsContainer(), false,
								"Select location to move card into");
						d.setInitialSelections(new Object[] { cardElement });
						d.setVisibleLines(20);
						d.setFamilyToggle(true);
						// only the allowed destinations are listed (same rule as
						// the Move to menu): no read-only list, and an owned card
						// never goes to a virtual list
						final boolean own = element instanceof MagicCardPhysical
								&& ((MagicCardPhysical) element).isOwn();
						d.setFilters(new ViewerFilter[] { new ViewerFilter() {
							@Override
							public boolean select(Viewer v, Object parentElement, Object el) {
								if (!(el instanceof CardCollection))
									return true; // folders
								CardCollection cc = (CardCollection) el;
								if (cc.isReadOnly())
									return false;
								return !(own && cc.isVirtual());
							}
						} });
						d.setValidator(new ISelectionValidator() {
							@Override
							public String isValid(Object selection) {
								if (selection instanceof IStructuredSelection) {
									IStructuredSelection iss = (IStructuredSelection) selection;
									if (iss.isEmpty())
										return null;
									if (iss.size() != 1)
										return "Only one location can be chosen";
									Object el = iss.getFirstElement();
									if (el instanceof CardCollection)
										return null;
									return "Invalid location: Select Collection or Deck";
								}
								return null;
							}
						});
						if (d.open() == Window.OK) {
							Object[] result = d.getResult();
							if (result.length == 0)
								return null;
							Object res = result[0];
							if (res instanceof CardElement) {
								return ((CardElement) res).getLocation();
							}
							return null;
						}
						return null;
					}
				};
				return editor;
			}

			@Override
			protected Object getValue(Object element) {
				if (element instanceof MagicCardPhysical) {
					MagicCardPhysical card = (MagicCardPhysical) element;
					String loc = card.getLocation().toString();
					return loc;
				}
				return null;
			}

			@Override
			protected void setValue(Object element, Object value) {
				if (viewer.getInput() instanceof IFilteredCardStore) {
					if (element instanceof MagicCardPhysical) {
						MagicCardPhysical card = (MagicCardPhysical) element;
						// move
						Location loc;
						if (value instanceof Location)
							loc = (Location) value;
						else if (value instanceof String)
							loc = Location.createLocation(new LocationPath((String) value));
						else
							return;
						if (loc.equals(card.getLocation()))
							return;
						// same rules as any move (the picker already hides refused
						// destinations); a virtual card into a non-virtual list
						// becomes Own after a confirmation
						ICardStore<IMagicCard> dest = DataManager.getInstance().getCardStore(loc);
						if (dest == null)
							return;
						Shell shell = viewer.getControl().getShell();
						String veto = OwnershipRules.moveVeto(card, dest);
						if (veto != null) {
							MessageDialog.openInformation(shell, "Cannot Move", veto);
							return;
						}
						if (!OwnershipConfirmation.confirmMove(shell, Collections.singletonList(card), dest))
							return;
						try {
							DataManager.getInstance().moveCards(Collections.<IMagicCard> singletonList(card), dest);
						} catch (MagicException e) {
							MessageDialog.openError(shell, "Cannot Move", e.getMessage());
							return;
						}
						// update
						viewer.update(element, null);
					}
				}
			}
		};
	}
}