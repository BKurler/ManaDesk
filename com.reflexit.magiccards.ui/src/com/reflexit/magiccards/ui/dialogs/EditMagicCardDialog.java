/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.ui.dialogs;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.sync.CardCache;
import com.reflexit.magiccards.core.sync.WebUtils;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.utils.CardImageUI;
import com.reflexit.magiccards.ui.utils.ImageCreator;

public class EditMagicCardDialog extends MagicDialog {
	private final static String UNCHANGED = EditCardsPropertiesDialog.UNCHANGED;
	private MagicCard card;
	private Image img;
	private Button imageButton;
	private Text urlText;
	private String localPath;
	private Composite area;
	private Text priceText;
	private Text collnumText;

	public EditMagicCardDialog(Shell parentShell, MagicCard card) {
		super(parentShell, new PreferenceStore());
		this.card = card;
		ICardField[] allFields = MagicCardField.allFields();
		for (ICardField field : allFields) {
			String value = card.getString(field);
			if (value != null)
				store.setDefault(field.name(), value);
			else
				store.setDefault(field.name(), "");
		}
	}

	@Override
	protected void createBodyArea(Composite parent) {
		getShell().setText("Edit Magic Card Properties");
		setTitle("Edit Magic Card Printing '" + card.getName() + "'");
		setMessage("Click on image to replace it with local file or type remote URL");
		Composite back = new Composite(parent, SWT.NONE);
		back.setLayout(new GridLayout(2, false));
		createImageControl(back);
		area = new Composite(back, SWT.NONE);
		area.setLayout(new GridLayout(2, false));
		GridData gda = new GridData(GridData.FILL_BOTH);
		gda.widthHint = convertWidthInCharsToPixels(80);
		area.setLayoutData(gda);
		createReadOnlyField("Id", MagicCardField.ID);
		createReadOnlyField("Name", MagicCardField.NAME);
		createReadOnlyField("Language", MagicCardField.LANG);
		collnumText = createEditableField("Collector's Number", MagicCardField.COLLNUM);
		priceText = createEditableField("Online Price", MagicCardField.DBPRICE);
		urlText = createEditableField("Image URL", MagicCardField.IMAGE_URL);
	}

	private void createImageControl(Composite parent) {

		GridData gda1 = new GridData(GridData.GRAB_VERTICAL);
		gda1.widthHint = ImageCreator.CARD_WIDTH;
		gda1.heightHint = ImageCreator.CARD_HEIGHT;

		imageButton = createPushButton(parent, "");
		imageButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				browseImage();
			}
		});
		imageButton.setLayoutData(gda1);

		IMagicCard card = CardImageUI.buildTemporaryCard(store.getString(MagicCardField.ID.name()),
				store.getString(MagicCardField.EDITION_ABBR.name()), store.getString(MagicCardField.LANG.name()));

		localPath = ImageCreator.getInstance().createCardPath(card, false, false);

		if (localPath != null && new File(localPath).exists()) {
			reloadImage(localPath);
		}
	}

	private void reloadImage(final String path) {

		// 1) Retirer l’image AVANT de disposer (SWT-safe)
		if (img != null && !img.isDisposed()) {
			imageButton.setImage(null); // ← OBLIGATOIRE
			img.dispose();
			img = null;
		}

		// 2) Charger l’ImageData en background
		new Job("Reload card image") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {

				ImageData data = null;

				try {
					data = ImageCreator.getInstance().createCardImageData(path, true);
				} catch (Exception ex) {
					StringWriter sw = new StringWriter();
					ex.printStackTrace(new PrintWriter(sw));
					MagicUIActivator.log("Error creating ImageData for reloadImage: " + path + "\n" + sw.toString());
					data = null;
				}

				final ImageData finalData = data;

				// 3) UI thread : créer l’Image et l’appliquer
				Display.getDefault().asyncExec(() -> {

					if (imageButton == null || imageButton.isDisposed())
						return;

					Image newImg = null;

					try {
						if (finalData != null) {
							newImg = new Image(Display.getDefault(), finalData);
						}
					} catch (SWTException ex) {
						MagicUIActivator.log("Failed to create Image from ImageData for reloadImage: " + path + " - "
								+ ex.getMessage());
						newImg = null;
					}

					// fallback UI-safe
					if (newImg == null) {
						try {
							newImg = ImageCreator.getInstance().createCardNotFoundImage(null);
						} catch (Throwable t) {
							MagicUIActivator.log("Failed to create not-found image for reloadImage: " + path + " - "
									+ t.getMessage());
							return;
						}
					}

					// appliquer la nouvelle image
					img = newImg;
					imageButton.setImage(img);

					// relayout UI
					if (!imageButton.getParent().isDisposed()) {
						imageButton.getParent().layout(true);
					}
				});

				return Status.OK_STATUS;
			}
		}.schedule();
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, 2, "Reload Image", false);
		createButton(parent, 3, "Restore Defaults", false);
		if (MagicUIActivator.isActivityEnabled(MagicUIActivator.ACTIVITY_DB_EXTEND)) {
			createButton(parent, 4, "Duplicate", false);
			createButton(parent, 5, "Remove", false);
		}
		super.createButtonsForButtonBar(parent);
	}

	@Override
	protected void buttonPressed(int buttonId) {
		try {
			if (buttonId == 2) {// reload
				reloadImageFromUrl();
			} else if (buttonId == 3) { // restore default
				store.setValue(MagicCardField.DBPRICE.name(), 0);
				String defaultImageUrl = card.getDefaultImageUrl();
				if (defaultImageUrl != null)
					store.setValue(MagicCardField.IMAGE_URL.name(), defaultImageUrl);
				else
					store.setValue(MagicCardField.IMAGE_URL.name(), "");
				priceText.setText(store.getString(MagicCardField.DBPRICE.name()));
				urlText.setText(store.getString(MagicCardField.IMAGE_URL.name()));
				collnumText.setText(store.getString(MagicCardField.COLLNUM.name()));
				reloadImageFromUrl();
			} else if (buttonId == 4) { // duplicate
				MagicCard card2 = card.cloneCard();
				card2.setCardId(card2.syntesizeId());
				card2.setProperty(MagicCardField.IMAGE_URL, card.getImageUrl());
				DataManager.getInstance().getMagicDBStore().add(card2);
				new EditMagicCardDialog(getShell(), card2).open();
				close();
			} else if (buttonId == 5) { // remove
				String id = card.getCardId();
				String enId = card.getEnglishCardId();
				if (id == null || id.startsWith("-") || enId != null) {
					CardGroup realCards = card.getRealCards();
					if (realCards != null && realCards.size() > 0) {
						throw new UnsupportedOperationException(
								"Cannot delete this card, it has instances: " + realCards.size());
					}
					if (MessageDialog.openConfirm(getParentShell(), "Delete",
							"Are you sure you want to delete " + card.getName() + " from database?")) {
						DataManager.getInstance().getMagicDBStore().remove(card);
					}
				} else {
					throw new UnsupportedOperationException("Cannot delete this card, it is synced to gatherer");
				}
				close();
			} else
				super.buttonPressed(buttonId);
		} catch (Exception e) {
			e.printStackTrace();
			MessageDialog.openError(getParentShell(), "Error", e.getMessage());
		}
	}

	private Text createEditableField(String label, MagicCardField field) {
		return createTextFieldEditor(area, label, field.name());
	}

	private Text createReadOnlyField(String label, MagicCardField field) {
		Text text = createTextFieldEditor(area, label, field.name());
		text.setEditable(false);
		text.setToolTipText("Not editable");
		text.setEnabled(false);
		return text;
	}

	protected void browseImage() {
		FileDialog fileDialog = new FileDialog(getShell());
		String file = fileDialog.open();
		if (file != null) {
			try {
				URL url = new File(file).toURI().toURL();
				store.setValue(MagicCardField.IMAGE_URL.name(), url.toExternalForm());
				urlText.setText(url.toExternalForm());
				new File(localPath).delete(); // delete card cached image
				File loc = new File(url.getFile());
				if (loc.exists())
					reloadImage(loc.getAbsolutePath());
			} catch (MalformedURLException e) {
				// ignore
			}
		} else {
			// cancelled, lets reload url
			reloadImageFromUrl();
		}
	}

	private void reloadImageFromUrl() {
		if (WebUtils.isWorkOffline())
			return;
		try {
			CardCache.saveCachedFile(new File(localPath), new URL(store.getString(MagicCardField.IMAGE_URL.name())));
			reloadImage(localPath);
		} catch (IOException e) {
			MagicUIActivator.log(e);
		}
	}

	@Override
	protected void okPressed() {
		editCard(this.card, store, true);
		super.okPressed();
	}

	@Override
	public boolean close() {
		if (img != null)
			img.dispose();
		return super.close();
	}

	private void editCard(MagicCard card, PreferenceStore store, boolean update) {
		boolean modified = false;
		modified = setField(card, store, MagicCardField.DBPRICE) || modified;
		modified = setField(card, store, MagicCardField.IMAGE_URL) || modified;
		modified = setField(card, store, MagicCardField.COLLNUM) || modified;
		if (modified && update) {
			DataManager.getInstance().update(card, Collections.singleton(MagicCardField.COLLNUM));
		}
	}

	protected boolean setField(MagicCard card, PreferenceStore store, ICardField field) {
		Boolean modified = false;
		String orig = String.valueOf(card.get(field));
		String edited = store.getString(field.name());
		if (!UNCHANGED.equals(edited) && !edited.equals(orig)) {
			try {
				card.set(field, edited);
				modified = true;
			} catch (Exception e) {
				// was bad value
				MessageDialog.openError(getShell(), "Error", "Invalid value for " + field + ": " + edited);
			}
		}
		return modified;
	}
}
