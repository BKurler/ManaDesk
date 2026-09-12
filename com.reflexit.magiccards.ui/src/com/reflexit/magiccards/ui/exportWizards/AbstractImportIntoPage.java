/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: shared scaffold of the two
 *                         "Import into an existing ..." pages
 *                         (ImportIntoDeckPage / ImportIntoCollectionPage). Just a
 *                         target picker + the inherited source group - no Name,
 *                         no "Empty". Creating a new element is a separate wizard.
 */
package com.reflexit.magiccards.ui.exportWizards;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.core.model.nav.ModelRoot;
import com.reflexit.magiccards.ui.dialogs.LocationPickerDialog;

/**
 * "Import into an existing deck / collection" first page. Pick the target, then
 * a card-list source (clipboard / file). Preview and write are inherited.
 */
public abstract class AbstractImportIntoPage extends AbstractCardListImportPage {
	private Text targetText;

	protected AbstractImportIntoPage(String pageName, IStructuredSelection selection) {
		super(pageName, selection);
		// keep only a target that is an existing element of the right side
		if (!(element instanceof CardCollection) || sideOf(element) != side())
			element = null;
	}

	/** "deck" or "collection" - lower case, for labels. */
	protected abstract String typeName();

	/** DECK / COLLECTION. */
	protected abstract ModelRoot.Side side();

	private ModelRoot.Side sideOf(CardElement el) {
		return DataManager.getInstance().getModelRoot().sideOf(el);
	}

	@Override
	protected String getTitleText() {
		String t = typeName();
		return "Import cards into a " + t;
	}

	@Override
	protected void createDestinationGroup(Composite parent) {
		String type = typeName();
		Group group = new Group(parent, SWT.NONE);
		group.setText(Character.toUpperCase(type.charAt(0)) + type.substring(1));
		group.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		group.setLayout(new GridLayout(2, false));

		if (element instanceof CardCollection) {
			// launched from the navigator's "Import into '<name>'..." - the
			// target is fixed by that selection, not a choice made in this dialog
			new Label(group, SWT.NONE).setText("Importing into:");
			Label targetLabel = new Label(group, SWT.NONE);
			targetLabel.setText(element.getLocation().getPath());
			targetLabel.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		} else {
			// defensive fallback - opened without a valid pre-selected target
			targetText = new Text(group, SWT.BORDER);
			targetText.setEditable(false);
			targetText.setMessage("choose the " + type + " to import into");
			targetText.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
			targetText.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseDown(MouseEvent e) {
					pickTarget();
				}
			});
			Button change = new Button(group, SWT.PUSH);
			change.setText("Choose...");
			change.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					pickTarget();
				}
			});
		}
		syncDestination();
	}

	private void pickTarget() {
		LocationPickerDialog dialog = new LocationPickerDialog(getShell(), SWT.SINGLE) {
			@Override
			protected Control createDialogArea(Composite parent) {
				Control x = super.createDialogArea(parent);
				setMessage("Select the " + typeName() + " to import the cards into.");
				return x;
			}
		};
		dialog.setSideFilter(side());
		if (element != null)
			dialog.setSelection(new StructuredSelection(element));
		if (dialog.open() == Window.OK && dialog.getSelection() != null && !dialog.getSelection().isEmpty()) {
			CardElement picked = (CardElement) dialog.getSelection().getFirstElement();
			if (!(picked instanceof CardCollection) || sideOf(picked) != side()) {
				setErrorMessage("Pick a " + typeName() + " under “"
						+ DataManager.getInstance().getModelRoot().containerFor(side()).getName() + "”.");
			} else if (((CardCollection) picked).isReadOnly()) {
				setErrorMessage("“" + picked.getName() + "” is read-only - you cannot import into it.");
			} else {
				element = picked;
				setErrorMessage(null);
			}
		}
		syncDestination();
		updatePageCompletion();
		updateWidgetEnablements();
	}

	@Override
	protected void syncDestination() {
		if (targetText == null || targetText.isDisposed())
			return;
		targetText.setText(element instanceof CardCollection ? element.getLocation().getPath() : "");
	}

	@Override
	public String getImportTargetDescription() {
		return element instanceof CardCollection ? "“" + element.getName() + "”"
				: "the selected " + typeName();
	}

	@Override
	protected boolean validateDestinationGroup() {
		if (!(element instanceof CardCollection) || sideOf(element) != side()) {
			setErrorMessage("Choose the " + typeName() + " to import into.");
			return false;
		}
		if (((CardCollection) element).isReadOnly()) {
			setErrorMessage("“" + element.getName() + "” is read-only.");
			return false;
		}
		return super.validateDestinationGroup();
	}
}
