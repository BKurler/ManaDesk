/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.ui.views.card;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.IPostSelectionProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.sync.CardCache;
import com.reflexit.magiccards.core.sync.GatherHelper;
import com.reflexit.magiccards.core.sync.WebUtils;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.utils.ImageCreator;
import com.reflexit.magiccards.ui.utils.StoredSelectionProvider;
import com.reflexit.magiccards.ui.utils.SymbolRenderer;
import com.reflexit.magiccards.ui.views.columns.PowerColumn;

class CardDescComposite extends Composite {

	private final CardDescView cardDescView;
	private Browser textBrowser;
	private Text textBackup;
	private Composite details;
	private IMagicCard card;
	private PowerColumn powerProvider;
	private PowerColumn toughProvider;
	// keep the true disk/original image separate so we can scale up to it
	// Debounce / force flags
	private final java.util.concurrent.atomic.AtomicInteger imageUpdatePending = new java.util.concurrent.atomic.AtomicInteger(
			0);

	// --- scaling / debug fields ---
	private static final boolean DEBUG = false; // set false to silence debug logs
	private final int MIN_DISPLAY_WIDTH = 120; // minimum width in pixels (adjust to taste)
	private final int SCALE_THRESHOLD_PX = 16; // minimum pixel change to trigger a rescale
	// unified border/margin used for image templates and layout calculations
	private static final int IMAGE_BORDER = 10;

	// int width = 223, hight = 310;
	int width = 265, hight = 370;
	private StoredSelectionProvider selectionProvider = new StoredSelectionProvider();

	public CardDescComposite(CardDescView cardDescView1, Composite parent, int style) {
		super(parent, style | SWT.INHERIT_DEFAULT);
		// UI
		this.cardDescView = cardDescView1;
		Composite panel = this;
		panel.setLayout(new GridLayout());

		this.powerProvider = new PowerColumn(MagicCardField.POWER, null, null);
		this.toughProvider = new PowerColumn(MagicCardField.TOUGHNESS, null, null);
		details = new Composite(panel, SWT.INHERIT_DEFAULT);
		GridDataFactory.fillDefaults()//
				.align(SWT.FILL, SWT.FILL)//
				.grab(true, true)//
				.applyTo(details);
		details.setLayout(new StackLayout());
		this.textBackup = new Text(details, SWT.WRAP | SWT.INHERIT_DEFAULT);
		// textBackup.setBackground(getDisplay().getSystemColor(SWT.COLOR_BLUE));
		try {
			// if (true)
			// throw new SWTError();
			this.textBrowser = new Browser(details, SWT.WRAP | SWT.INHERIT_FORCE);
			textBrowser.addLocationListener(new LocationAdapter() {
				@Override
				public void changing(LocationEvent event) {
					String location = event.location;
					if (location.equals("about:blank"))
						return;
					try {
						if (location.contains("https:")) {
							if (WebUtils.isWorkOffline())
								return;

							if (Desktop.isDesktopSupported()
									&& Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
								URI url = new URI(location);
								Desktop.getDesktop().browse(url);
							}

							// Nothing else to do
							event.doit = false;
						} else {

							String cardId = GatherHelper.extractCardIdFromURL(new URL(location));
							if (cardId != null) {
								event.doit = false;
								ICardStore<IMagicCard> magicDBStore = DataManager.getCardHandler().getMagicDBStore();
								IMagicCard card2 = magicDBStore.getCard(cardId);
								if (card2 != null) {
									cardDescView.setSelection(new StructuredSelection(card2));
								}
							}
						}
					} catch (MalformedURLException e) {
						MagicLogger.log(e);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (URISyntaxException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
			swapVisibility(textBrowser, textBackup);

		} catch (Throwable e) {
			textBrowser = null;
			MagicUIActivator.log(e);
			swapVisibility(textBackup, textBrowser);
		}
	}

	private boolean logOnce = false;

	public void setCard(IMagicCard card) {
		this.card = card;
		if (card == IMagicCard.DEFAULT || card == null) {
			selectionProvider.setSelection(new StructuredSelection());
		} else {
			selectionProvider.setSelection(new StructuredSelection(card));
		}
	}

	private Menu forcedMenu;

	@Override
	public void setMenu(Menu menu) {
		// If this is the menu we installed, accept it
		if (forcedMenu == null || menu == forcedMenu) {
			forcedMenu = menu;
			super.setMenu(menu);

			if (textBrowser != null && !textBrowser.isDisposed()) {
				textBrowser.setMenu(menu);
			}
			if (textBackup != null && !textBackup.isDisposed()) {
				textBackup.setMenu(menu);
			}
		} else {
			// Eclipse is trying to override our menu → block it
			// System.out.println("[CardInfo] BLOCKED external menu: " + menu);
		}
	}

	private void downloadCardImage(IMagicCard card, String path) throws IOException {
		URL url = CardCache.createRemoteImageURL(card);
		if (url == null) {
			return;
		}

		try (InputStream in = url.openStream()) {
			Files.copy(in, Paths.get(path), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void ensureCardImageCached(IMagicCard card) {
		try {
			String path = ImageCreator.getInstance().createCardPath(card, false, false);
			if (path == null || path.isEmpty()) {
				MagicUIActivator.log("Image path is null for card: " + card);
				return;
			}

			File file = new File(path);
			if (file.exists()) {
				return;
			}

			// --- THIS is the missing piece ---
			downloadCardImage(card, path);

		} catch (Exception e) {
			MagicUIActivator.log("Failed to cache card image for " + card, e);
		}
	}

	public void setText(IMagicCard card) {
		if (card == IMagicCard.DEFAULT) {
			return;
		}

		// --- NEW: ensure the image is cached on disk ---
		ensureCardImageCached(card);
		// ------------------------------------------------

		try {
			if (textBrowser != null) {
				String links = getLinks(card);
				String data = getCardDataHtml(card, links);
				String text = getText(card);

				String oracle = getOracle(card, text);
				String rulings = getCardRulingsHtml(card);

				String raw = data + oracle + text + rulings;
				String html = SymbolRenderer.wrapHtml(raw, textBrowser);
				this.textBrowser.setText(html);

				swapVisibility(textBrowser, textBackup);
				return;
			}
		} catch (Exception e) {
			if (logOnce == false) {
				MagicUIActivator.log(e);
				logOnce = true;
			}
		}

		String data = getCardDataText(card);
		String text = getText(card);
		text = text.replaceAll("<br>", "\n");
		this.textBackup.setText(data + text);
		swapVisibility(textBackup, textBrowser);
	}

	protected String getText(IMagicCard card) {
		String text = card.getText();
		if (text == null || text.length() == 0)
			text = card.getOracleText();
		if (text == null || text.length() == 0)
			text = "";
		return text;
	}

	protected String getOracle(IMagicCard card, String text) {
		String oracle = card.getOracleText();
		if (text != null && text.length() != 0 && !text.equals(oracle)) {
			oracle = "Oracle:<br>" + oracle + "<br><br>";
		} else {
			oracle = "";
		}
		return oracle;
	}

	protected String getLinks(IMagicCard card) {
		String links = "";
		String flipId = card.getFlipId();
		if (flipId != null) {
			if (flipId.equals(card.getCardId())) {
				MagicLogger.log("Same flip id for " + card.getCardId());
				flipId = "-" + flipId;
			}
			links = "<a href=\"" + GatherHelper.createImageDetailURL(flipId) + "\">Flip</a><br><br>";
		}
		return links;
	}

	private void swapVisibility(Control con1, Control con2) {
		((StackLayout) details.getLayout()).topControl = con1;
		details.layout(true, true);
		redraw();
	}

	private String getCardDataText(IMagicCard card) {
		String pt = "";
		if (card.getToughness() != null && card.getToughness().length() > 0) {
			pt = powerProvider.getText(card) + "/" + toughProvider.getText(card);
		}
		String data = card.getName() + "\n" + getType(card);
		if (pt.length() > 0) {
			data += "\n" + pt;
		} else {
			data += "\n";
		}
		String num = getCollectorNumber(card);

		String abbr = card.getEdition().getIconAbbreviation();
		data += "\n{SETICON:" + abbr + "-" + getRarity(card) + "} [" + abbr + "] " + card.getSet() + " ("
				+ getRarity(card) + ") " + num + "\n";

		return data;
	}

	public String getRarity(IMagicCard card) {
		return card.getRarity() == null ? "Unknown Rarity" : card.getRarity();
	}

	public String getType(IMagicCard card) {
		return card.getType() == null ? "Unknown Type" : card.getType();
	}

	private String getCardDataHtml(IMagicCard card, String links) {
		StringBuilder sb = new StringBuilder();

		// 1. Card image (local cache or remote URL)
		try {
			URL imgUrl = CardCache.getImageURL(card);
			if (imgUrl != null) {
				sb.append("<img src=\"").append(imgUrl.toExternalForm()).append("\" class=\"cardimage\"/>");
			}
		} catch (Exception e) {
			// ignore, no image available
		}

		// 2. Existing text formatting, wrapped in a block
		String text = getCardDataText(card);
		text = text.replaceAll("\n", "<br/>\n");

		sb.append("<div class='cardtext'>");
		sb.append(links);
		sb.append(text);
		sb.append("</div>");

		sb.append("<p/>");

		return sb.toString();
	}

	public String getCollectorNumber(IMagicCard card) {
		String num = (String) card.get(MagicCardField.COLLNUM);
		if (num != null)
			num = "[" + num + "]";
		return num;
	}

	private String getCardRulingsHtml(IMagicCard card) {
		String srulings = card.getRulings();
		if (srulings == null || srulings.length() == 0) {
			return "";
		}
		String data = "<p>Rulings:<ul>";
		String rulings[] = srulings.split("\\n");
		for (String ruling : rulings) {
			data += "<li>" + ruling + "</li>";
		}
		data += "</ul><p/>";
		return data;
	}

	@Override
	public void dispose() {
		super.dispose();
	}

	public IMagicCard getCard() {
		return this.card;
	}

	public IPostSelectionProvider getSelectionProvider() {
		return selectionProvider;
	}
}