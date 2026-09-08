/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - create the deck's Sideboard / Extra list from this
 *                         dialog (checkboxes; checked+disabled when they already
 *                         exist, disabled for collections)
 */

package com.reflexit.magiccards.ui.dialogs;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.DeckAccessoriesPopulator;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CollectionsContainer;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;
import com.reflexit.magiccards.ui.utils.StatusDots;

/**
 * Dialog to edit properties of a deck/collection
 */
public class EditDeckPropertiesDialog extends TitleAreaDialog {
	private IStorageInfo info;
	/** The edited element, when known - needed to create/detect its sideboard/extra. */
	private CardCollection deck;
	private Combo type;
	private Button virtual;
	private Button unsorted;
	private Text text;
	private Button protection;
	private Button createSideboard;
	private Button createExtra;
	private boolean sideboardExists;
	private boolean extraExists;

	public EditDeckPropertiesDialog(Shell shell, IStorageInfo info) {
		super(shell);
		if (info == null)
			throw new NullPointerException();
		this.info = info;
		setShellStyle(getShellStyle() | SWT.RESIZE);
	}

	public EditDeckPropertiesDialog(Shell shell, CardCollection deck) {
		this(shell, deck.getStorageInfo());
		this.deck = deck;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		getShell().setText("Edit...");
		// setTitleImage(MagicUIActivator.getDefault().getImage("icons/Book-1-icon.gif"));
		setTitle("Edit Properties");
		setMessage("You can modify deck/collection properties here. Press OK to save.");
		Composite area = (Composite) super.createDialogArea(parent);
		Composite comp = new Composite(area, SWT.NONE);
		comp.setLayoutData(new GridData(GridData.FILL_BOTH));
		GridLayout layout = new GridLayout(4, false);
		comp.setLayout(layout);
		{
			Label label = new Label(comp, SWT.NONE);
			label.setText("Type:");
			type = new Combo(comp, SWT.READ_ONLY | SWT.DROP_DOWN);
			type.add(IStorageInfo.DECK_TYPE);
			type.add(IStorageInfo.COLLECTION_TYPE);
			type.setText(IStorageInfo.DECK_TYPE.equals(info.getType()) ? IStorageInfo.DECK_TYPE
					: IStorageInfo.COLLECTION_TYPE);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			// take the rest of the row so the checkboxes below aren't
			// crowded onto the same line - easier to read as its own row
			gd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns - 1;
			type.setLayoutData(gd);
		}
		virtual = StatusDots.check(comp, StatusDots.VIRTUAL, "Virtual");
		virtual.setSelection(info.isVirtual());
		protection = StatusDots.check(comp, StatusDots.READ_ONLY, "Read Only");
		protection.setSelection(info.isReadOnly());
		unsorted = StatusDots.check(comp, StatusDots.UNSORTED, "Unsorted (collections only)");
		unsorted.setSelection(info.isUnsorted());
		StatusDots.exclusive(virtual, unsorted);
		createFamilyGroup(comp);
		// Unsorted (manual card order) only makes sense for a collection
		type.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				syncForType();
			}
		});
		syncForType();
		createTextArea(comp);
		return comp;
	}

	private void syncForType() {
		boolean deckType = IStorageInfo.DECK_TYPE.equals(type.getText());
		if (deckType)
			unsorted.setSelection(false);
		unsorted.setEnabled(!deckType);
		syncFamilyForType(deckType);
	}

	/**
	 * The "also create the Sideboard / Extra list" checkboxes. Shown only when the
	 * edited element is known. Each box:
	 * <ul>
	 * <li>is checked and disabled when that list already exists,</li>
	 * <li>is unchecked and enabled when the element is a deck and the list is
	 * missing (checking it creates the list on OK),</li>
	 * <li>is disabled for a collection (or a sideboard/extra list itself).</li>
	 * </ul>
	 */
	private void createFamilyGroup(Composite comp) {
		if (deck == null)
			return;
		Location loc = deck.getLocation();
		boolean member = loc.isSideboard() || loc.isExtra();
		CollectionsContainer parent = deck.getParent() instanceof CollectionsContainer
				? (CollectionsContainer) deck.getParent()
				: null;
		sideboardExists = !member && parent != null && parent.contains(loc.toSideboard());
		extraExists = !member && parent != null && parent.contains(loc.toExtra());

		Group group = new Group(comp, SWT.NONE);
		group.setText("Sideboard / Extra");
		GridData ggd = new GridData(GridData.FILL_HORIZONTAL);
		ggd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns;
		group.setLayoutData(ggd);
		group.setLayout(new GridLayout());

		createSideboard = new Button(group, SWT.CHECK);
		createSideboard.setText("Sideboard");
		createSideboard.setToolTipText(
				"An empty, editable sideboard list alongside the deck. It never counts towards deck legality.");
		createSideboard.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		createExtra = new Button(group, SWT.CHECK);
		createExtra.setText("Extra list (tokens, emblems, markers)");
		createExtra.setToolTipText(
				"An editable extra list alongside the deck, pre-filled with the tokens / emblems / markers the deck needs at count 0. It never counts towards deck legality.");
		createExtra.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	}

	private void syncFamilyForType(boolean deckType) {
		if (createSideboard == null)
			return;
		Location loc = deck.getLocation();
		boolean canCreate = deckType && !loc.isSideboard() && !loc.isExtra();
		setFamilyCheck(createSideboard, sideboardExists, canCreate);
		setFamilyCheck(createExtra, extraExists, canCreate);
	}

	private static void setFamilyCheck(Button b, boolean exists, boolean canCreate) {
		if (exists) {
			b.setSelection(true);
			b.setEnabled(false); // already there - nothing to do
		} else {
			b.setEnabled(canCreate);
			if (!canCreate)
				b.setSelection(false); // collection / not a main deck: never offered
		}
	}

	/** True when the box is a live "please create it" request (enabled + checked). */
	private boolean isCreate(Button b) {
		return b != null && b.isEnabled() && b.getSelection();
	}

	private void createTextArea(Composite area) {
		Group group = new Group(area, SWT.NONE);
		group.setText("Description");
		GridData gd = new GridData(GridData.FILL_BOTH);
		gd.horizontalSpan = ((GridLayout) area.getLayout()).numColumns;
		group.setLayoutData(gd);
		group.setLayout(new GridLayout());
		text = new Text(group, SWT.WRAP | SWT.BORDER);
		text.setLayoutData(GridDataFactory.fillDefaults().hint(600, 200).create());
		text.setText(info.getComment() == null ? "" : info.getComment());
	}

	@Override
	protected void okPressed() {
		try {
			save();
		} catch (MagicException e) {
			MessageDialog.openError(getParentShell(), "Error", "Cannot save: " + e.getMessage());
			return;
		}
		createRequestedFamilyMembers();
		super.okPressed();
	}

	private void save() {
		boolean newRO = protection.getSelection();
		boolean oldRO = info.isReadOnly();

		// Case 1: disabling read-only → must disable first
		if (oldRO && !newRO) {
			info.setReadOnly(false);
		}

		// Apply all editable properties
		info.setComment(text.getText());
		info.setVirtual(virtual.getSelection());
		info.setUnsorted(unsorted.getSelection());
		info.setType(type.getText());

		// Case 2: enabling read-only → must enable last
		if (!oldRO && newRO) {
			info.setReadOnly(true);
		}
	}

	/**
	 * Create the sideboard / extra sibling(s) the user asked for. Runs on the UI
	 * thread (this is {@code okPressed()}); {@link DeckAccessoriesPopulator#populate}
	 * fires card events that touch SWT, so it must not move to a background job.
	 */
	private void createRequestedFamilyMembers() {
		if (deck == null || (!isCreate(createSideboard) && !isCreate(createExtra)))
			return;
		CollectionsContainer parent = deck.getParent() instanceof CollectionsContainer
				? (CollectionsContainer) deck.getParent()
				: null;
		if (parent == null)
			return;
		boolean asVirtual = virtual.getSelection();
		try {
			if (isCreate(createSideboard))
				createFamilyMember(parent, deck.getLocation().toSideboard(), asVirtual);
			if (isCreate(createExtra)) {
				Location extraLoc = deck.getLocation().toExtra();
				if (createFamilyMember(parent, extraLoc, asVirtual) != null)
					DeckAccessoriesPopulator.populate(extraLoc);
			}
		} catch (RuntimeException e) {
			MessageDialog.openError(getParentShell(), "Error",
					"The deck properties were saved, but its companion list could not be created:\n" + e.getMessage());
		}
	}

	/** Creates {@code loc} mirroring the deck's virtual flag, or returns null if it already exists. */
	private static CardCollection createFamilyMember(CollectionsContainer parent, Location loc, boolean virtual) {
		if (parent.contains(loc))
			return null;
		return parent.addDeck(loc.getBaseFileName(), true, virtual);
	}

}
