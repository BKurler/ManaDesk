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
 *     Rémi Dutil (2026) - rename from this dialog; Type is shown read-only (a deck
 *                         stays a deck, a collection stays a collection)
 *     Rémi Dutil (2026) - field order is now Type, then Name, then the
 *                         checkboxes; the Sideboard/Extra group gets an
 *                         explanatory label and clearer checkbox wording
 *                         ("Create a Sideboard" / "Create an Extra list (...)")
 *     Rémi Dutil (2026) - "Boxed" checkbox (manual "physically boxed up"
 *                         marker for the Proxier view; independent of the
 *                         other checkboxes, no exclusivity/side effects)
 *     Rémi Dutil (2026) - "Default Format" combo (deck-only, like Boxed) -
 *                         the format the Legality tab validates against by
 *                         default, without the user re-picking it every
 *                         visit. Reuses the same storage DeckLegalityPage2
 *                         already wrote as a side effect of its own combo -
 *                         this just makes it a real, discoverable field
 *     Rémi Dutil (2026) - collection Type (Standard / For Trade / Wishlist/To Print) radios
 *                         replace a collection's Virtual checkbox (the type
 *                         decides it); switching to/from Wishlist is offered only
 *                         when the cards allow it. A deck's Virtual checkbox
 *                         is one-way: a non-virtual (legacy) deck can be made
 *                         virtual, a virtual deck cannot be made non-virtual.
 *     Rémi Dutil (2026) - only the fields that apply are shown: a collection
 *                         gets Unsorted (Standard / For Trade only); a deck
 *                         gets Boxed, Default Format and the Sideboard /
 *                         Extra checkboxes, which now look like the New Deck
 *                         page's (no group box, same wording)
 *     Rémi Dutil (2026) - the main collection is locked: Standard only, never
 *                         Unsorted or Read Only, name and description not
 *                         editable (the description explains it)
 */

package com.reflexit.magiccards.ui.dialogs;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import java.util.EnumMap;
import java.util.Map;

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
import com.reflexit.magiccards.core.legality.Format;
import com.reflexit.magiccards.core.model.CollectionType;
import com.reflexit.magiccards.core.model.DeckAccessoriesPopulator;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.OwnershipRules;
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
	private Text nameText;
	private final boolean deckType;
	/** The main collection: always a sorted, writable Standard collection,
	 *  and it cannot be renamed. */
	private final boolean mainCollection;
	private static final String MAIN_COLLECTION_DESCRIPTION = "This is the main collection: it is always a sorted,"
			+ " writable Standard collection, and it cannot be renamed or deleted.";
	/** Deck only - a collection's virtual flag follows its type. */
	private Button virtual;
	/** Collection only - one radio per type. */
	private final Map<CollectionType, Button> typeRadios = new EnumMap<>(CollectionType.class);
	private Button unsorted;
	private Text text;
	private Button protection;
	private Button boxed;
	private Button createSideboard;
	private Button createExtra;
	private boolean sideboardExists;
	private boolean extraExists;
	private Combo formatCombo;
	/** First combo entry - clears the stored default format on save. */
	private static final String NOT_SET_FORMAT = "(Not set)";

	public EditDeckPropertiesDialog(Shell shell, IStorageInfo info) {
		super(shell);
		if (info == null)
			throw new NullPointerException();
		this.info = info;
		this.deckType = IStorageInfo.DECK_TYPE.equals(info.getType());
		this.mainCollection = !deckType && OwnershipRules.isMainCollection(info);
		setShellStyle(getShellStyle() | SWT.RESIZE);
	}

	public EditDeckPropertiesDialog(Shell shell, CardCollection deck) {
		this(shell, deck.getStorageInfo());
		this.deck = deck;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		String kind = deckType ? "deck" : "collection";
		getShell().setText("Edit " + (deckType ? "Deck" : "Collection"));
		setTitle("Edit " + (deckType ? "Deck" : "Collection") + " Properties");
		setMessage("Modify this " + kind + "'s properties, then press OK to save.");
		Composite area = (Composite) super.createDialogArea(parent);
		Composite comp = new Composite(area, SWT.NONE);
		comp.setLayoutData(new GridData(GridData.FILL_BOTH));
		GridLayout layout = new GridLayout(4, false);
		comp.setLayout(layout);
		int cols = ((GridLayout) comp.getLayout()).numColumns;
		{
			// Type leads - a deck stays a deck and a collection stays a
			// collection, so it orients the rest of the dialog before the
			// editable fields below it
			Label label = new Label(comp, SWT.NONE);
			label.setText("Type:");
			Label typeLabel = new Label(comp, SWT.NONE);
			typeLabel.setText(deckType ? "Deck" : "Collection");
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalSpan = cols - 1;
			typeLabel.setLayoutData(gd);
		}
		if (!deckType)
			createCollectionTypeField(comp, cols);
		if (deck != null) {
			Label nl = new Label(comp, SWT.NONE);
			nl.setText("Name:");
			nameText = new Text(comp, SWT.BORDER | SWT.SINGLE);
			nameText.setText(deck.getName());
			nameText.setEditable(!mainCollection);
			GridData ngd = new GridData(GridData.FILL_HORIZONTAL);
			ngd.horizontalSpan = cols - 1;
			nameText.setLayoutData(ngd);
		}
		if (deckType)
			createDeckVirtualField(comp, cols);
		protection = StatusDots.check(comp, StatusDots.READ_ONLY, "Read Only");
		protection.setSelection(info.isReadOnly() && !mainCollection);
		protection.setEnabled(!mainCollection);
		if (deckType) {
			// Boxed, Sideboard / Extra and Default Format are deck-only notions
			boxed = StatusDots.check(comp, StatusDots.BOXED, "Boxed (physically pulled together)");
			boxed.setSelection(info.isBoxed());
			createFamilyGroup(comp);
			createFormatField(comp, cols);
		} else {
			// Unsorted (manual card order) is a collection-only notion
			unsorted = StatusDots.check(comp, StatusDots.UNSORTED,
					"Unsorted - keep the manual card order and do not merge identical cards");
			unsorted.setSelection(info.isUnsorted());
			syncUnsorted();
		}
		createTextArea(comp);
		return comp;
	}

	/**
	 * "Collection Type:" - Standard / For Trade / Wishlist/To Print, each with its meaning.
	 * The type decides the virtual flag, so there is no Virtual checkbox for a
	 * collection. A type the current cards do not allow (an owned card in a
	 * would-be Wishlist collection, a virtual card in a would-be Standard one) is
	 * disabled, with the reason as its tooltip.
	 */
	private void createCollectionTypeField(Composite comp, int cols) {
		Label label = new Label(comp, SWT.NONE);
		label.setText("Collection Type:");
		label.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
		Composite types = new Composite(comp, SWT.NONE);
		GridLayout gl = new GridLayout(2, false);
		gl.marginWidth = 0;
		gl.marginHeight = 0;
		types.setLayout(gl);
		GridData tgd = new GridData(GridData.FILL_HORIZONTAL);
		tgd.horizontalSpan = cols - 1;
		types.setLayoutData(tgd);
		CollectionType current = info.getCollectionType();
		Iterable<?> cards = deck != null ? deck.getStore() : null;
		for (CollectionType t : CollectionType.values()) {
			Button radio = new Button(types, SWT.RADIO);
			radio.setText(t.getLabel());
			radio.setSelection(t == current);
			Label desc = new Label(types, SWT.NONE);
			desc.setText(t.getDescription());
			String veto = t == current ? null : OwnershipRules.collectionTypeVeto(cards, t);
			if (mainCollection && t != CollectionType.STANDARD)
				veto = "is the main collection, which is always Standard";
			if (veto != null) {
				radio.setEnabled(false);
				desc.setEnabled(false);
				String tip = "Not available: this collection " + veto + ".";
				radio.setToolTipText(tip);
				desc.setToolTipText(tip);
			}
			radio.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					syncUnsorted();
				}
			});
			typeRadios.put(t, radio);
		}
		Label rules = new Label(comp, SWT.WRAP);
		rules.setText("Standard and For Trade collections only hold cards you own. A Wishlist/To Print collection is virtual"
				+ " (cards to buy, proxies to print): set a card to Own once bought or printed, then move it to a"
				+ " Standard collection. Only Standard and For Trade collections can be Unsorted.");
		GridData rgd = new GridData(GridData.FILL_HORIZONTAL);
		rgd.horizontalSpan = cols;
		rgd.widthHint = 400;
		rules.setLayoutData(rgd);
	}

	/** Only a Standard / For Trade collection can be Unsorted (not Wishlist/To Print). */
	private void syncUnsorted() {
		if (unsorted == null || unsorted.isDisposed())
			return;
		CollectionType t = selectedCollectionType();
		boolean allowed = !mainCollection && (t == null || !t.isVirtual());
		if (!allowed)
			unsorted.setSelection(false);
		unsorted.setEnabled(allowed);
	}

	private CollectionType selectedCollectionType() {
		for (Map.Entry<CollectionType, Button> e : typeRadios.entrySet()) {
			if (e.getValue().getSelection())
				return e.getKey();
		}
		return info.getCollectionType();
	}

	/**
	 * A deck's "Virtual" checkbox - one way only. New decks are always
	 * virtual and cannot be made non-virtual; an existing non-virtual deck
	 * keeps that status until the user checks the box.
	 */
	private void createDeckVirtualField(Composite comp, int cols) {
		boolean isVirtual = info.isVirtual();
		virtual = StatusDots.check(comp, StatusDots.VIRTUAL,
				isVirtual ? "Virtual (a deck is always virtual)"
						: "Virtual (check to make this deck virtual - this cannot be undone)");
		virtual.setSelection(isVirtual);
		virtual.setEnabled(!isVirtual);
		virtual.setToolTipText("A deck lists the cards to play; the cards you own stay in your collections."
				+ " An older non-virtual deck can be made virtual, but no deck can be made non-virtual.");
	}

	/**
	 * "Default Format:" - which format the Legality tab validates this deck
	 * against by default (Standard/Modern/Commander/...), without the user
	 * re-picking it every visit. Deck-only, like Boxed - a collection has no
	 * notion of legality.
	 */
	private void createFormatField(Composite comp, int cols) {
		Label label = new Label(comp, SWT.NONE);
		label.setText("Default Format:");
		formatCombo = new Combo(comp, SWT.READ_ONLY);
		formatCombo.add(NOT_SET_FORMAT);
		for (Format f : Format.getFormats())
			formatCombo.add(f.name());
		String current = info.getDefaultFormat();
		if (current != null && !current.trim().isEmpty()) {
			if (formatCombo.indexOf(current) < 0)
				formatCombo.add(current); // stale/unknown value - keep it visible & selected
			formatCombo.setText(current);
		} else {
			formatCombo.setText(NOT_SET_FORMAT);
		}
		GridData fgd = new GridData(GridData.FILL_HORIZONTAL);
		fgd.horizontalSpan = cols - 1;
		formatCombo.setLayoutData(fgd);
	}

	/**
	 * The "also create the Sideboard / Extra list" checkboxes. Shown only when the
	 * edited element is known. Each box:
	 * <ul>
	 * <li>is checked and disabled when that list already exists,</li>
	 * <li>is unchecked and enabled when the element is a deck and the list is
	 * missing (checking it creates the list on OK),</li>
	 * <li>is disabled when the edited deck is itself a sideboard / extra list.</li>
	 * </ul>
	 */
	private void createFamilyGroup(Composite comp) {
		if (deck == null || !deckType) // Sideboard / Extra are a deck-only notion
			return;
		Location loc = deck.getLocation();
		boolean member = loc.isSideboard() || loc.isExtra();
		CollectionsContainer parent = deck.getParent() instanceof CollectionsContainer
				? (CollectionsContainer) deck.getParent()
				: null;
		sideboardExists = !member && parent != null && parent.contains(loc.toSideboard());
		extraExists = !member && parent != null && parent.contains(loc.toExtra());

		// same look and wording as the New Deck page: two plain checkboxes,
		// set a little apart from the flags above (they create other lists)
		int cols = ((GridLayout) comp.getLayout()).numColumns;
		createSideboard = new Button(comp, SWT.CHECK);
		createSideboard.setText(sideboardExists ? "Sideboard (already created)" : "Also create a Sideboard");
		createSideboard.setToolTipText(
				"An empty, editable sideboard list alongside the deck. It never counts towards deck legality.");
		GridData sgd = new GridData(GridData.FILL_HORIZONTAL);
		sgd.horizontalSpan = cols;
		sgd.verticalIndent = 8;
		createSideboard.setLayoutData(sgd);

		createExtra = new Button(comp, SWT.CHECK);
		createExtra.setText(extraExists ? "Extra list (already created)"
				: "Also create an Extra list (tokens, emblems, markers)");
		createExtra.setToolTipText(
				"An editable extra list alongside the deck, pre-filled with the tokens / emblems / markers the deck needs at count 0. It never counts towards deck legality.");
		GridData egd = new GridData(GridData.FILL_HORIZONTAL);
		egd.horizontalSpan = cols;
		createExtra.setLayoutData(egd);

		boolean canCreate = !member;
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
		if (mainCollection) {
			// fixed description, not editable
			text.setText(MAIN_COLLECTION_DESCRIPTION);
			text.setEditable(false);
		} else {
			text.setText(info.getComment() == null ? "" : info.getComment());
		}
	}

	@Override
	protected void okPressed() {
		if (!applyRename())
			return;
		try {
			save();
		} catch (MagicException e) {
			MessageDialog.openError(getParentShell(), "Error", "Cannot save: " + e.getMessage());
			return;
		}
		createRequestedFamilyMembers();
		super.okPressed();
	}

	/** Rename the element (and its sideboard / extra) if the Name field changed.
	 *  Returns false (and keeps the dialog open) on an invalid name. */
	private boolean applyRename() {
		if (deck == null || nameText == null)
			return true;
		String newName = nameText.getText().trim();
		if (newName.equals(deck.getName()))
			return true;
		if (newName.isEmpty() || newName.contains("/") || newName.contains("\\") || newName.contains(".")) {
			setErrorMessage("Name cannot be empty or contain '.', '/' or '\\'");
			return false;
		}
		if (deck.getParent() != null && deck.getParent().findChieldByName(newName + ".xml") != null) {
			setErrorMessage("A deck or collection named \"" + newName + "\" already exists here");
			return false;
		}
		deck.renameWithRelated(newName);
		return true;
	}

	private void save() {
		boolean newRO = protection.getSelection();
		boolean oldRO = info.isReadOnly();

		// Case 1: disabling read-only → must disable first
		if (oldRO && !newRO) {
			info.setReadOnly(false);
		}

		// Apply all editable properties (Type is fixed - a deck stays a deck)
		if (!mainCollection)
			info.setComment(text.getText());
		if (deckType) {
			// one way: a deck can become virtual, never non-virtual
			if (virtual.getSelection() && !info.isVirtual())
				info.setVirtual(true);
		} else {
			CollectionType t = selectedCollectionType();
			if (t != null && t != info.getCollectionType())
				info.setCollectionType(t); // also sets the virtual flag
		}
		if (unsorted != null)
			info.setUnsorted(unsorted.isEnabled() && unsorted.getSelection());
		if (boxed != null)
			info.setBoxed(boxed.getSelection());
		if (formatCombo != null) {
			String chosenFormat = formatCombo.getText();
			info.setDefaultFormat(NOT_SET_FORMAT.equals(chosenFormat) ? null : chosenFormat);
		}

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
