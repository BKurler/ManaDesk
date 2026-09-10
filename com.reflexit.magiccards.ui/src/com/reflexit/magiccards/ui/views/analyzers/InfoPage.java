/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - open the deck-aware Edit Properties dialog (sideboard/
 *                         extra creation); use getArea().getShell() (editButton is
 *                         never built)
 *     Rémi Dutil (2026) - proxy count (tournament-readiness) + cost to replace them
 *     Rémi Dutil (2026) - proxies split into "covered by cards you own (any print)"
 *                         vs "must acquire"; only the latter feeds the cost
 */
package com.reflexit.magiccards.ui.views.analyzers;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;
import com.reflexit.magiccards.core.model.utils.CardStoreUtils;
import com.reflexit.magiccards.core.sync.CurrencyConvertor;
import com.reflexit.magiccards.ui.dialogs.EditDeckPropertiesDialog;
import com.reflexit.magiccards.ui.utils.SymbolRenderer;
import com.reflexit.magiccards.ui.views.columns.PriceColumn;
import com.reflexit.magiccards.ui.views.columns.SellerPriceColumn;
import com.reflexit.magiccards.ui.views.lib.IDeckPage;
import com.reflexit.magiccards.ui.widgets.DynamicCombo;

public class InfoPage extends AbstractDeckPage implements IDeckPage {
	private Text text;
	private Label total;
	private Label totalSideboard;
	private Label dbprice;
	String prefix = "Deck";
	DecimalFormat decimalFormat = new DecimalFormat("#0.00");
	private Label colors;
	private DynamicCombo ownership;
	private Link editButton;
	private Label decktype;
	private Label averagecost;
	private Composite stats;
	private Label maxRepeats;
	private Label loclabel;
	private Label colorsSideboard;
	private Label rarity;
	private Label proxies;
	private Label proxiesOwned;
	private Label proxyCost;
	private DynamicCombo protection;
	private IStorageInfo storageInfo;
	private static final DecimalFormat INFO_DECIMAL = new DecimalFormat("#0.00");

	@Override
	public void createPageContents(Composite parent) {
		createTextArea().setLayoutData(GridDataFactory.fillDefaults().grab(true, true).minSize(-1, 40).create());
		/*
		 * !!! RD createEditButton(getArea())
		 * .setLayoutData(GridDataFactory.fillDefaults().align(SWT.BEGINNING,
		 * SWT.END).create());
		 */
		createStatsArea(getArea())
				.setLayoutData(GridDataFactory.swtDefaults().align(SWT.BEGINNING, SWT.FILL).grab(false, true).create());
	}

	protected Control createEditButton(Composite parent) {
		editButton = new Link(parent, SWT.PUSH);
		editButton.setText("<a>Edit...</a>");
		editButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				openEdit();
			}
		});
		return editButton;
	}

	private Composite createStatsArea(Composite parent) {
		stats = new Composite(parent, SWT.NONE);
		stats.setLayout(new GridLayout(4, false));
		decktype = createTextLabel("Type: ");
		loclabel = createTextLabel("Location: ");
		ownership = createDynCombo("Ownership: ", null, "Own", "Virtual");
		ownership.getCombo().addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String value = ownership.getCombo().getText();
				boolean virtual = value.equals("Virtual");
				if (storageInfo.isVirtual() != virtual) {
					storageInfo.setVirtual(virtual);
				}
			}
		});
		protection = createDynCombo("Protection: ",
				"If collection is read only it cannot be modfied, except for unsetting read only flag", "Read Only",
				"Writable");
		protection.getCombo().addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String value = protection.getCombo().getText();
				boolean b = value.equals("Read Only");
				if (storageInfo.isReadOnly() != b) {
					storageInfo.setReadOnly(b);
				}
			}
		});
		total = createTextLabel("Cards: ");
		totalSideboard = createTextLabel("Cards (Sideboard): ");
		colors = createTextLabel("Colors: ");
		colorsSideboard = createTextLabel("Colors (Sideboard): ");
		averagecost = createTextLabel("Average Mana Cost: ");
		maxRepeats = createTextLabel("Max Repeats: ",
				"How many time each card repeats, excluding basic land (for legality purposes)");
		rarity = createTextLabel("Rarity: ");
		// tree.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_BLUE));
		dbprice = createTextLabel("Price: ",
				"Cost of a deck using Online Price column," + " in brackets cost of a deck using User Price column");
		proxies = createTextLabel("Proxies: ",
				"How many cards in this deck are proxies - not paper-legal for sanctioned play until replaced");
		proxiesOwned = createTextLabel("  ...you own for real: ",
				"Proxy cards you already own a genuine copy of somewhere in your collection (any printing) -\n"
						+ "swap those in and no purchase is needed. The rest still have to be acquired.");
		proxyCost = createTextLabel("Cost to acquire the rest: ",
				"Market value (Online Price) of the real copies you would still have to buy - only the proxies\n"
						+ "you do NOT already own a genuine copy of");
		return stats;
	}

	private Label createTextLabel(String string) {
		return createTextLabel(string, null);
	}

	private Label createTextLabel(final String string, String tip) {
		Label label = new Label(stats, SWT.NONE);
		label.setText(string);
		label.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_BLUE));
		Label text = new Label(stats, SWT.NONE);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		text.setLayoutData(gd);
		if (tip != null) {
			label.setToolTipText(tip);
			text.setToolTipText(tip);
		}
		return text;
	}

	private DynamicCombo createDynCombo(final String string, String tip, String... values) {
		Label label = new Label(stats, SWT.NONE);
		label.setText(string);
		label.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_BLUE));
		final DynamicCombo text = new DynamicCombo(stats, SWT.READ_ONLY, values);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		text.setLayoutData(gd);
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {
				text.activateCombo();
			}
		});
		if (tip != null) {
			label.setToolTipText(tip);
			text.setToolTipText(tip);
		}
		return text;
	}

	private Group createTextArea() {
		Group group = new Group(getArea(), SWT.NONE);
		group.setText("Description");
		group.setLayout(new GridLayout());
		text = new Text(group, SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL);
		text.addMouseListener(new MouseListener() {
			@Override
			public void mouseUp(MouseEvent e) {
				openEdit();
			}

			@Override
			public void mouseDown(MouseEvent e) {
				// ignore
			}

			@Override
			public void mouseDoubleClick(MouseEvent e) {
				openEdit();
			}
		});
		text.setLayoutData(GridDataFactory.fillDefaults().hint(600, 80).grab(true, false).create());
		return group;
	}

	protected void setComment(String text2) {
		IStorageInfo si = getStorageInfo();
		if (si == null)
			return;
		if (!text2.equals(si.getComment()))
			si.setComment(text2);
	}

	@Override
	public void activate() {
		super.activate();
		storageInfo = getStorageInfo();
		String type = null;
		if (storageInfo != null) {
			String comment = storageInfo.getComment();
			if (comment != null)
				text.setText(comment);
			type = storageInfo.getType();
			protection.setText(storageInfo.isReadOnly() ? "Read Only" : "Writable");
		}
		Location location = store.getLocation();
		Location sideboard = location.toSideboard();
		ICardStore<IMagicCard> sideboardStore = DataManager.getInstance().getCardStore(sideboard);
		ICardStore<IMagicCard> mainStore = DataManager.getInstance().getCardStore(location.toMainDeck());
		if (mainStore == null)
			mainStore = store;
		totalSideboard.setText(String.valueOf(getCount(sideboardStore)));
		total.setText(String.valueOf(getCount(mainStore)));
		prefix = (type != null && type.equals(IStorageInfo.DECK_TYPE)) ? "Deck" : "Collection";
		if (location.isSideboard()) {
			prefix = "Sideboard";
		}
		CardGroup group = CardStoreUtils.buildGroup(mainStore, sideboardStore);
		loclabel.setText(location.toString());

		String sp = computeFormattedPrice(group, new ColumnFormatter() {
			private final SellerPriceColumn col = new SellerPriceColumn();

			@Override
			public String formatGroup(CardGroup g) {
				return col.getText(g);
			}

			@Override
			public Object getRawPrice(ICard card) {
				return card.get(com.reflexit.magiccards.core.model.MagicCardField.DBPRICE);
			}
		});
		String up = computeFormattedPrice(group, new ColumnFormatter() {
			private final PriceColumn col = new PriceColumn();

			@Override
			public String formatGroup(CardGroup g) {
				return col.getText(g);
			}

			@Override
			public Object getRawPrice(ICard card) {
				return card.get(com.reflexit.magiccards.core.model.MagicCardField.PRICE);
			}
		});

		dbprice.setText(sp + " (" + up + ")");
		updateProxyStats(mainStore, sideboardStore);
		colors.setImage(SymbolRenderer.buildCostImage(CardStoreUtils.buildColors(mainStore)));
		colorsSideboard.setImage(SymbolRenderer.buildCostImage(CardStoreUtils.buildColors(sideboardStore)));
		ownership.setText(store.isVirtual() ? "Virtual" : "Own");
		decktype.setText(prefix);
		List<? extends ICard> childrenList = group.getChildrenList();
		maxRepeats.setText(String.valueOf(CardStoreUtils.getMaxRepeats(childrenList)));
		CardGroup types = CardStoreUtils.buildTypeGroups(childrenList);
		CardGroup top = (CardGroup) types.getChildAtIndex(0);
		CardGroup ncre = (CardGroup) top.getChildAtIndex(1);
		CardGroup cre = (CardGroup) top.getChildAtIndex(2);

		if (ncre == null || cre == null) {
			rarity.setText("*");
			averagecost.setText("-");
			getArea().layout(true);
			return;
		}

		String r1 = ncre.getRarity();
		String r2 = cre.getRarity();

		if (Objects.equals(r1, r2)) {
			rarity.setText(r1 != null ? r1 : "*");
		} else {
			rarity.setText("*");
		}

		int spellCount = ncre.getCount() + cre.getCount();
		if (spellCount > 0) {
			int creCost = CardStoreUtils.getManaCost(cre.expand());
			int ncreCost = CardStoreUtils.getManaCost(ncre.expand());
			averagecost.setText(
					String.valueOf((creCost + ncreCost) / (float) spellCount) + " (" + spellCount + " spells)");
		} else {
			averagecost.setText("-");
		}

		getArea().layout(true);
	}

	private void openEdit() {
		try {
			Shell shell = getArea().getShell();
			CardCollection cc = null;
			try {
				if (getDeckView() != null)
					cc = getDeckView().getCardCollection();
			} catch (RuntimeException notADeckView) {
				// InfoPage shown outside a DeckView - fall back to the info-only dialog
			}
			EditDeckPropertiesDialog dialog = cc != null ? new EditDeckPropertiesDialog(shell, cc)
					: new EditDeckPropertiesDialog(shell, getStorageInfo());
			if (dialog.open() == Window.OK) {
				activate();
			}
		} catch (Exception x) {
			x.printStackTrace();
		}
	}

	private void updateProxyStats(ICardStore<IMagicCard> mainStore, ICardStore<IMagicCard> sideboardStore) {
		// proxy quantity per card NAME, a representative copy for pricing, and the
		// real (non-proxy, owned) copies of that name already sitting in this deck
		java.util.Map<String, Integer> proxyQty = new java.util.LinkedHashMap<>();
		java.util.Map<String, MagicCardPhysical> rep = new java.util.HashMap<>();
		java.util.Map<String, Integer> realInDeck = new java.util.HashMap<>();
		int proxyCount = 0;
		for (ICardStore<IMagicCard> s : new ICardStore[] { mainStore, sideboardStore }) {
			if (s == null)
				continue;
			for (IMagicCard card : s) {
				if (!(card instanceof MagicCardPhysical))
					continue;
				MagicCardPhysical mcp = (MagicCardPhysical) card;
				String name = mcp.getName();
				if (mcp.isProxy()) {
					proxyCount += mcp.getCount();
					proxyQty.merge(name, mcp.getCount(), Integer::sum);
					rep.putIfAbsent(name, mcp);
				} else if (mcp.isOwn()) {
					realInDeck.merge(name, mcp.getCount(), Integer::sum);
				}
			}
		}
		int total = getCount(mainStore) + getCount(sideboardStore);
		if (proxyCount == 0) {
			proxies.setText("none");
			proxies.setForeground(null);
			proxiesOwned.setText("-");
			proxyCost.setText("-");
			return;
		}

		int covered = 0;
		double cost = 0;
		for (java.util.Map.Entry<String, Integer> e : proxyQty.entrySet()) {
			int need = e.getValue();
			MagicCardPhysical mcp = rep.get(e.getKey());
			// real copies of this card owned anywhere (any printing), minus the
			// ones already committed to this same deck as real cards
			int spareReal = Math.max(0,
					mcp.getBase().getGenuineOwnTotalAll() - realInDeck.getOrDefault(e.getKey(), 0));
			int cov = Math.min(need, spareReal);
			covered += cov;
			int toBuy = need - cov;
			float unit = mcp.getDbPrice();
			if (toBuy > 0 && unit > 0)
				cost += unit * toBuy;
		}
		int toAcquire = proxyCount - covered;

		proxies.setText(proxyCount + " / " + total + (toAcquire > 0 ? "  (not paper-legal until replaced)"
				: "  (swap in your real copies - nothing to buy)"));
		proxies.setForeground(Display.getDefault().getSystemColor(toAcquire > 0 ? SWT.COLOR_RED : SWT.COLOR_DARK_GREEN));
		proxiesOwned.setText(covered + " of " + proxyCount);
		String sym = CurrencyConvertor.getCurrency().getSymbol();
		proxyCost.setText(sym + " " + INFO_DECIMAL.format(toAcquire == 0 ? 0.0 : cost) + "  (" + toAcquire + " card"
				+ (toAcquire == 1 ? "" : "s") + " to buy)");
	}

	private String computeFormattedPrice(CardGroup group, ColumnFormatter fmt) {
		if (group == null)
			return "";

		List<? extends ICard> children = group.getChildrenList();
		if (children != null && children.size() > 1) {
			return fmt.formatGroup(group);
		}

		ICard first = group.getFirstCard();
		if (first == null)
			return "";

		Object rawPrice = fmt.getRawPrice(first);
		if (rawPrice == null)
			return "";

		float unitPrice;
		try {
			if (rawPrice instanceof Number) {
				unitPrice = ((Number) rawPrice).floatValue();
			} else {
				unitPrice = Float.parseFloat(rawPrice.toString());
			}
		} catch (NumberFormatException e) {
			return fmt.formatGroup(group);
		}

		int count = 1;
		try {
			count = first.getInt(MagicCardField.COUNT);
			if (count < 1)
				count = 1;
		} catch (Exception e) {
			// keep default count = 1
		}

		float total = unitPrice * count;
		if (total == 0f)
			return "";

		java.text.DecimalFormat df = new java.text.DecimalFormat("#0.00");
		java.util.Currency cur = com.reflexit.magiccards.core.sync.CurrencyConvertor.getCurrency();
		return cur.getSymbol() + " " + df.format(total);
	}

	private interface ColumnFormatter {
		String formatGroup(CardGroup group);

		Object getRawPrice(ICard card);
	}

	@Override
	public ISelectionProvider getSelectionProvider() {
		return null;
	}
}
