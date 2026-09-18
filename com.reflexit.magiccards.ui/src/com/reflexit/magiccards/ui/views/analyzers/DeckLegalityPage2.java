/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tym The Enchanter - initial API and implementation
 *    Alena Laskavaia - ui re-design
 *******************************************************************************/
/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration;
 *                         dropped the "Check Legality Online" toolbar action
 *                         (legality now comes from the Scryfall bulk data)
 *     Rémi Dutil (2026) - the combo's format is seeded once, from
 *                         IStorageInfo#getDefaultFormat() (the deck's own
 *                         Default Format, set in Edit Deck Properties) -
 *                         picking a different one here is session-only and
 *                         no longer writes back to storage. Previously
 *                         setFormat() persisted every pick directly under the
 *                         raw "format" property key, which is what
 *                         Default Format now deliberately owns; decks that
 *                         already had a value there (from having used this
 *                         combo before Default Format existed) keep it as
 *                         their starting default, but a same-session pick no
 *                         longer silently overwrites it
 */
package com.reflexit.magiccards.ui.views.analyzers;

import java.util.List;
import java.util.Map;

import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.legality.Format;
import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Legality;
import com.reflexit.magiccards.core.model.LegalityMap;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardFilter;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;
import com.reflexit.magiccards.core.model.storage.MemoryFilteredCardStore;
import com.reflexit.magiccards.core.model.utils.CardStoreUtils;
import com.reflexit.magiccards.core.model.utils.CardStoreUtils.CardStats;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.actions.ImageAction;
import com.reflexit.magiccards.ui.actions.RefreshAction;
import com.reflexit.magiccards.ui.utils.SymbolRenderer;
import com.reflexit.magiccards.ui.views.IMagicColumnViewer;
import com.reflexit.magiccards.ui.views.analyzers.GroupListControl.GroupTreeViewer;
import com.reflexit.magiccards.ui.views.columns.AbstractColumn;
import com.reflexit.magiccards.ui.views.columns.CostColumn;
import com.reflexit.magiccards.ui.views.columns.CountColumn;
import com.reflexit.magiccards.ui.views.columns.GenColumn;
import com.reflexit.magiccards.ui.views.columns.GroupColumn;
import com.reflexit.magiccards.ui.views.columns.LegalityColumn;

public class DeckLegalityPage2 extends AbstractDeckListPage {
	private static final Format DEFAULT_FORMAT = Format.STANDARD;
	private Format format = DEFAULT_FORMAT;
	/** Seeded once from the deck's own Default Format on the first
	 *  {@link #refresh()} - a same-session combo pick after that must not be
	 *  clobbered by every later refresh() (card edits, the Refresh button, ...). */
	private boolean formatSeeded = false;
	private LegalityMap deckLegalities = LegalityMap.EMPTY; // format->legality
	private Combo comboLegality;
	protected TreeViewer tree;
	private ImageAction refresh;
	private Composite info;
	private Label total;
	private Label totalSideboard;
	private Label colors;
	private Label colorsSideboard;
	private Label maxRepeats;
	private Label rarity;
	private CheckControlDecoration totalDeco;
	private CardStats stats;
	private CheckControlDecoration maxRepeastDeco;

	@Override
	public void createPageContents(Composite area) {
		area.setLayout(new FillLayout());
		SashForm sashForm = new SashForm(area, SWT.HORIZONTAL);
		createMainControl(sashForm);
		createInfoPanel(sashForm);
		sashForm.setWeights(new int[] { 75, 25 });
		makeActions();
		setQuickFilterVisible(false);
	}

	abstract class CheckControlDecoration extends ControlDecoration {
		public CheckControlDecoration(Control control, int position) {
			super(control, position);
			FieldDecorationRegistry registry = FieldDecorationRegistry.getDefault();
			Image newImage = registry.getFieldDecoration(FieldDecorationRegistry.DEC_ERROR).getImage();
			setImage(newImage);
			hide();
		}

		public void updateVisibility() {
			String error = validate();
			if (error == null) {
				hide();
			} else {
				setDescriptionText(error);
				show();
			}
		}

		protected abstract String validate();
	}

	private void createInfoPanel(Composite parent) {
		info = new Composite(parent, SWT.BORDER);
		info.setLayout(new GridLayout(2, false));

		comboLegality = createLegalityCombo(info);
		comboLegality.setLayoutData(GridDataFactory.fillDefaults().span(2, 1).create());
		total = createTextLabel("Cards: ");
		totalSideboard = createTextLabel("Cards (Sideboard): ");
		maxRepeats = createTextLabel("Max Repeats: ",
				"How many time each card repeats, excluding basic land (for legality purposes)");
		colors = createTextLabel("Colors: ");
		colorsSideboard = createTextLabel("Colors (Sideboard): ");
		rarity = createTextLabel("Rarity: ");
		totalDeco = new CheckControlDecoration(total, SWT.LEAD | SWT.CENTER) {
			@Override
			protected String validate() {
				if (stats == null)
					return null;
				String err = format.validateDeckCount(stats.mainCount);
				return err;
			}
		};
		maxRepeastDeco = new CheckControlDecoration(maxRepeats, SWT.LEAD | SWT.CENTER) {
			@Override
			protected String validate() {
				if (stats == null)
					return null;
				String err = format.validateCardCount(stats.maxRepeats);
				return err;
			}
		};
	}

	protected Combo createLegalityCombo(Composite parent) {
		final Combo comboLegality = new Combo(parent, SWT.READ_ONLY);
		comboLegality.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				setFormat(getFormat(comboLegality));
			}
		});
		reloadLegalityCombo(comboLegality);
		return comboLegality;
	}

	protected String getFormat(Combo combo) {
		return (String) combo.getData(combo.getText());
	}

	private Label createTextLabel(String string) {
		return createTextLabel(string, null);
	}

	private Label createTextLabel(String string, String tip) {
		Label label = createBlueLabel(string);
		Label text = new Label(info, SWT.NONE);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		text.setLayoutData(gd);
		if (tip != null) {
			label.setToolTipText(tip);
			text.setToolTipText(tip);
		}
		return text;
	}

	protected Label createBlueLabel(String string) {
		Label label = new Label(info, SWT.NONE);
		label.setText(string);
		label.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_BLUE));
		return label;
	}

	@Override
	public void fillLocalToolBar(IToolBarManager manager) {
		manager.add(this.refresh);
		// super.fillLocalToolBar(manager);
	}

	@Override
	protected void makeActions() {
		super.makeActions();
		refresh = new RefreshAction(this::refresh);
	}

	@Override
	public void refresh() {
		setFStore();
		deckLegalities = LegalityMap.calculateDeckLegality((ICardStore) fstore.getCardStore());
		if (!formatSeeded) {
			// only the very first refresh() seeds from the deck's stored
			// Default Format - a later refresh() (card edit, Refresh button,
			// ...) must leave whatever format the user picked this session
			// alone, not silently revert it
			formatSeeded = true;
			IStorageInfo storageInfo = getStorageInfo();
			String f = storageInfo == null ? null : storageInfo.getDefaultFormat();
			format = (f != null && f.trim().length() > 0) ? Format.valueOf(f) : DEFAULT_FORMAT;
		}
		if (comboLegality != null) {
			reloadLegalityCombo(comboLegality);
		}
		updateInfo();
		ICardGroup root = fstore.getCardGroupRoot();
		tree.setInput(root);
		tree.refresh(true);
		refreshViewer();
	}

	@Override
	public void activate() {
		super.activate();
		refresh();
	}

	private void updateInfo() {
		stats = new CardStoreUtils.CardStats(getCardStore());
		totalSideboard.setText(String.valueOf(stats.sideboardCount));
		total.setText(String.valueOf(stats.mainCount));
		colors.setImage(SymbolRenderer.buildCostImage(stats.mainColors));
		colorsSideboard.setImage(SymbolRenderer.buildCostImage(stats.sideboardColors));
		maxRepeats.setText(String.valueOf(stats.maxRepeats));
		CardGroup types = CardStoreUtils.buildTypeGroups(getCardStore());
		CardGroup top = (CardGroup) types.getChildAtIndex(0);
		CardGroup landGroup = (CardGroup) top.getChildAtIndex(0);
		CardGroup basicLand = (CardGroup) landGroup.getChildAtIndex(0);
		landGroup.remove(basicLand); // remove basic land
		String nonBasicLandRarity = top.getRarity();

		if (nonBasicLandRarity != null) {
			rarity.setText(nonBasicLandRarity);
		} else {
			String basicLandRarity = basicLand.getRarity();
			if (basicLandRarity != null)
				rarity.setText(basicLandRarity);
			else
				rarity.setText("");
		}
		totalDeco.updateVisibility();
		maxRepeastDeco.updateVisibility();
	}

	public void setFStore() {
		if (getCardStore() == null)
			return;
		MemoryFilteredCardStore<ICard> mstore = new MemoryFilteredCardStore<>();
		Location loc = getCardStore().getLocation();
		MagicCardFilter filter = (MagicCardFilter) getDeckView().getFilter().clone();
		ICardStore mainStore = DataManager.getInstance().getCardStore(loc.toMainDeck());
		ICardStore sideStore = DataManager.getInstance().getCardStore(loc.toSideboard());
		if (mainStore != null)
			mstore.getCardStore().addAll(mainStore.getCards());
		if (sideStore != null)
			mstore.getCardStore().addAll(sideStore.getCards());
		mstore.setLocation(loc.toMainDeck());
		filter.getSortOrder().setSortField(MagicCardField.LEGALITY, true);
		filter.getSortOrder().setSortField(MagicCardField.SIDEBOARD, true);
		filter.setGroupFields(MagicCardField.SIDEBOARD);
		mstore.update(filter);
		this.fstore = mstore;
	}

	/** Session-only: picking a format here no longer persists it as the
	 *  deck's Default Format - that's now a deliberate choice made in Edit
	 *  Deck Properties. {@code formatSeeded} (already true by now - the combo
	 *  can't be touched before the first {@link #refresh()} builds it) keeps
	 *  the {@link #refresh()} call below from reverting this pick. */
	public void setFormat(final String f) {
		format = Format.valueOf(f);
		refresh();
	}

	protected ICardField[] getGroupFields() {
		return null;
	}

	@Override
	public IMagicColumnViewer createViewer(Composite parent) {
		tree = new GroupTreeViewer(getPreferencePageId(), parent) {
			@Override
			protected void createCustomColumns(List<AbstractColumn> columns) {
				createPageCustomColumns(columns);
			}
		};
		tree.setAutoExpandLevel(2);
		return (IMagicColumnViewer) tree;
	}

	@Override
	protected String getPreferencePageId() {
		return null;
	}

	protected void createPageCustomColumns(List<AbstractColumn> columns) {
		columns.add(new GroupColumn(false, true, false));
		columns.add(new CountColumn() {
			@Override
			public Color getBackground(Object element) {
				if (element instanceof IMagicCard) {
					String err = format.validateCardOrGroup((IMagicCard) element);
					if (err != null)
						return MagicUIActivator.COLOR_PINKINSH;
				}
				return super.getBackground(element);
			}

			@Override
			public String getToolTipText(Object element) {
				if (element instanceof IMagicCard) {
					String err = format.validateCardOrGroup((IMagicCard) element);
					return err;
				}
				return super.getToolTipText(element);
			}
		});
		columns.add(new CostColumn());
		// columns.add(new SetColumn());
		columns.add(new LegalityColumn() {
			@Override
			public Color getBackground(Object element) {
				if (element instanceof IMagicCard) {
					LegalityMap legalityMap = ((IMagicCard) element).getLegalityMap();
					Legality legality = legalityMap.get(format);
					switch (legality) {
					case UNKNOWN:
					case NOT_LEGAL:
					case BANNED:
						return MagicUIActivator.COLOR_PINKINSH;
					case LEGAL:
						return MagicUIActivator.COLOR_GREENISH;
					case RESTRICTED:
						return Display.getDefault().getSystemColor(SWT.COLOR_DARK_YELLOW);
					default:
						break;
					}
				}
				return super.getBackground(element);
			}
		});
		columns.add(new GenColumn(MagicCardField.ERROR, "Error") {
			@Override
			public int getColumnWidth() {
				return 250;
			}

			@Override
			public String getText(Object element) {
				if (element instanceof IMagicCard) {
					String err = format.validateCardOrGroup((IMagicCard) element);
					return err;
				}
				return super.getToolTipText(element);
			}
		});
	}

	protected void reloadLegalityCombo(Combo comboLegality) {
		comboLegality.removeAll();
		Map<Format, Legality> deckMap = this.deckLegalities.mapOfLegality();
		for (final Format f : deckMap.keySet()) {
			String label = getFormatLabel(f, deckMap.get(f));
			comboLegality.add(label);
			comboLegality.setData(label, f.name());
		}
		comboLegality.setText(getFormatLabel(format, deckMap.get(format)));
	}

	private String getFormatLabel(Format f, Legality legality) {
		if (legality == Legality.UNKNOWN)
			return f.name();
		return f.name() + " - " + legality.getLabel();
	}

	@Override
	public String getStatusMessage() {
		if (fstore == null || format == null || stats == null)
			return "";
		String err = format.validateLegality((ICardStore) fstore.getCardStore(), stats);
		if (err == null)
			return "Format: " + format.name() + " is legal";
		else
			return "Format: " + format.name() + " - " + err;
	}
}
