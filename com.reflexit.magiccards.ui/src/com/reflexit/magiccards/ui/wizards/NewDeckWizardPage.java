package com.reflexit.magiccards.ui.wizards;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import com.reflexit.magiccards.core.model.nav.CardOrganizer;

public class NewDeckWizardPage extends NewCardCollectionWizardPage {
	private Button createSideboard;
	private Button createExtra;

	public NewDeckWizardPage(ISelection selection) {
		super(selection);
	}

	@Override
	public String getElementTypeName() {
		return "deck";
	}

	@Override
	protected CardOrganizer getRootContainer() {
		return getModelRoot().getDeckContainer();
	}

	@Override
	protected void createOptionsGroup(Composite container) {
		super.createOptionsGroup(container);
		// a deck's cards are almost always tracked separately from the owned
		// physical collection, so default to virtual rather than making the
		// user remember to check it every time
		virtual.setSelection(true);

		this.createSideboard = new Button(container, SWT.CHECK);
		this.createSideboard.setText("Also create a Sideboard for this deck");
		this.createSideboard.setToolTipText(
				"Creates an empty, editable sideboard list alongside the deck. It never counts towards deck legality.");
		GridData gd1 = new GridData(GridData.FILL_HORIZONTAL);
		gd1.horizontalSpan = ((GridLayout) container.getLayout()).numColumns;
		this.createSideboard.setLayoutData(gd1);

		this.createExtra = new Button(container, SWT.CHECK);
		this.createExtra.setText("Also create an Extra list (tokens, emblems, markers)");
		this.createExtra.setToolTipText(
				"Creates an empty, editable extra list alongside the deck, pre-filled with the tokens/emblems/markers the deck needs at count 0. It never counts towards deck legality.");
		GridData gd2 = new GridData(GridData.FILL_HORIZONTAL);
		gd2.horizontalSpan = ((GridLayout) container.getLayout()).numColumns;
		this.createExtra.setLayoutData(gd2);
	}

	public boolean isCreateSideboard() {
		return this.createSideboard != null && this.createSideboard.getSelection();
	}

	public boolean isCreateExtra() {
		return this.createExtra != null && this.createExtra.getSelection();
	}
}