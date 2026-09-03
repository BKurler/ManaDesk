
/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.sync;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.xml.DbPricesMultiFileStore;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.seller.CustomPriceProvider;
import com.reflexit.magiccards.core.sync.ParserHtmlHelper.ILoadCardHander;
import com.reflexit.magiccards.core.sync.ParserHtmlHelper.OutputHandler;

public class ParseScryFallChecklist extends AbstractParseJson {
	CustomPriceProvider priceProvider = new CustomPriceProvider("TCG Player (Medium)");
	DbPricesMultiFileStore priceStore = (DbPricesMultiFileStore) DbPricesMultiFileStore.getInstance();
	public static final String BASE_SEARCH_URL = "https://api.scryfall.com/cards/search?";
	public static final String TEXT_EXPORT_DIR = "/tmp/madatabase";
	public boolean includeImagesUrl = true;

	// Set to try when generating flat files. This is required to remove some fields
	public boolean generateFlat = false;
	public ICardStore store = DataManager.getInstance().getMagicDBStore();

	private final Set<String> costSymbols = new TreeSet<>();
	private final Set<String> textSymbols = new TreeSet<>();
	private String updateString = "";

	private static final Pattern SYMBOL_PATTERN = Pattern.compile("\\{[^}]+\\}");

	private String normalizeText(String s) {
		if (s == null)
			return null;
		return s.replace("\r", "") // remove CR
				.replace("\n", " ") // flatten lines
				.replace("\t", " ") // tabs → spaces
				.trim();
	}

	private void collectSymbols(String source, String cardName, Set<String> target) {
		if (source == null || source.isEmpty())
			return;

		source = normalizeText(source);

		Matcher m = SYMBOL_PATTERN.matcher(source);
		while (m.find()) {
			String sym = m.group();
			String entry = sym + " :: first seen in " + cardName;

			boolean exists = target.stream().anyMatch(s -> s.startsWith(sym + " ::"));
			if (!exists) {
				target.add(entry);
			}
		}
	}

	@Override
	public String processFromReader(BufferedReader st, ILoadCardHander handler) throws IOException {
		try {
			JSONObject top = (JSONObject) new JSONParser().parse(st);
			int c = getInt(top, "total_cards");
			handler.setCardCount(c);
			Boolean has_more = (Boolean) top.get("has_more");
			JSONArray data = (JSONArray) top.get("data");
			for (Object elem : data) {
				parseRecord((JSONObject) elem, handler);
			}
			if (has_more) {
				return getString(top, "next_page");
			}
		} catch (ParseException e) {
			MagicLogger.log(e);
			throw new RuntimeException("No results");
		}
		handler.onEnd();
		return null;
	}

	// Convert from Scryfall to MA language string
	private String BuildLanguage(String Language) {
		if (Language == null) {
			return null;
		}

		String maLanguage = null;

		switch (Language) {
		case ("en"):
			maLanguage = "English";
			break;
		case ("es"):
			maLanguage = "Spanish";
			break;
		case ("fr"):
			maLanguage = "French";
			break;
		case ("de"):
			maLanguage = "German";
			break;
		case ("it"):
			maLanguage = "Italian";
			break;
		case ("pt"):
			maLanguage = "Portuguese";
			break;
		case ("ja"):
			maLanguage = "Japanese";
			break;
		case ("ko"):
			maLanguage = "Korean";
			break;
		case ("ru"):
			maLanguage = "Russian";
			break;
		case ("zhs"):
			maLanguage = "Chinese Simplified";
			break;
		case ("zht"):
			maLanguage = "Chinese Traditional";
			break;
		case ("he"):
			maLanguage = "Hebrew";
			break;
		case ("la"):
			maLanguage = "Latin";
			break;
		case ("grc"):
			maLanguage = "Ancient Greek";
			break;
		case ("ar"):
			maLanguage = "Arabic";
			break;
		case ("sa"):
			maLanguage = "Sanskrit";
			break;
		case ("ph"):
			maLanguage = "Phyrexian";
			break;
		case ("qya"):
			maLanguage = "Quenya";
			break;

		default:
			// Unknown return null
			break;
		}

		return maLanguage;
	}

	private String BuildText(String header, String text) {
		assert (header != null);
		assert (text != null);

		String line = "";
		if (text == null || text.isEmpty() || text.equals("false")) {
			return line;
		}
		return header + text + "<br>";
	}

	private String BuildFinishes(JSONArray list) {
		if (list == null || list.size() == 0) {
			return "";
		}

		String finishes = "Finishes: ";

		for (int i = 0; i < list.size(); i++) {
			finishes += list.get(i).toString() + ":";
		}

		return finishes + "<br>";
	}

	private String BuildPromos(JSONArray list) {
		if (list == null || list.size() == 0) {
			return "";
		}

		String promos = "Promos: ";

		for (int i = 0; i < list.size(); i++) {
			promos += list.get(i).toString() + ":";
		}

		return promos + "<br>";
	}

	private String BuildLegalities(JSONObject object) {
		if (object == null || object.isEmpty())
			return "";

		StringBuilder sb = new StringBuilder();

		appendLegality(sb, "Standard", object.get("standard"));
		appendLegality(sb, "Pioneer", object.get("pioneer"));
		appendLegality(sb, "Modern", object.get("modern"));
		appendLegality(sb, "Commander", object.get("commander"));
		appendLegality(sb, "Vintage", object.get("vintage"));
		appendLegality(sb, "Legacy", object.get("legacy"));

		return sb.toString();
	}

	private void appendLegality(StringBuilder sb, String name, Object value) {
		if (value == null) {
			sb.append(name).append("-;");
			return;
		}

		String v = value.toString().toLowerCase(Locale.ROOT);

		switch (v) {
		case "legal":
			sb.append(name).append("+;");
			break;

		case "not_legal":
		case "illegal":
			sb.append(name).append("-;");
			break;

		case "banned":
			sb.append(name).append("!;");
			break;

		case "restricted":
			sb.append(name).append("1;");
			break;

		default:
			// Unknown → treat as not legal
			sb.append(name).append("-;");
			break;
		}
	}

	private String BuildPrice(JSONObject prices) {
		String priceStr = "";
		String foilPriceStr = "";
		String etchedPriceStr = "";

		if (prices == null || prices.size() == 0) {
			return "";
		}

		Object obj = prices.get("usd");

		if (obj != null) {
			priceStr = "R$ " + obj.toString() + " ";
		} else {

			// Try EUR price if USD is not available
			obj = prices.get("eur");
			if (obj != null) {

				float eur = Float.parseFloat(obj.toString());

				// If EUR exists, convert it to USD
				if (eur != 0f) {
					double eurToUsd = CurrencyConvertor.getRate(Currency.getInstance("EUR"),
							Currency.getInstance("USD"));
					priceStr = "RE$ " + String.format(Locale.US, "%.2f", (eur * (float) eurToUsd)) + " ";
				}
			}
		}

		obj = prices.get("usd_foil");
		if (obj != null) {
			foilPriceStr = "F$ " + obj.toString();
		} else {
			// Try EUR price if USD is not available
			obj = prices.get("eur_foil");
			if (obj != null) {

				float eur = Float.parseFloat(obj.toString());

				// If EUR exists, convert it to USD
				if (eur != 0f) {
					double eurToUsd = CurrencyConvertor.getRate(Currency.getInstance("EUR"),
							Currency.getInstance("USD"));
					priceStr = "FE$ " + String.format(Locale.US, "%.2f", (eur * (float) eurToUsd)) + " ";
				}
			}
		}

		obj = prices.get("usd_etched");
		if (obj != null) {
			etchedPriceStr = "E$ " + obj.toString();
		}

		if (!priceStr.isEmpty() || !foilPriceStr.isEmpty() || !etchedPriceStr.isEmpty()) {
			return priceStr + foilPriceStr + etchedPriceStr + "<br>";
		}
		return "";
	}

	private void parseRecord(JSONObject elem, ILoadCardHander handler) {
		if (elem == null)
			return;

		Object games = elem.get("games");

		// Skip non paper cards
		// We don't want to manage Virtual cards
		if (games == null || !(games.toString().contains("paper"))) {
			return;
		}

		// Skip some languages for now
		// We could improve this later
		String lang = (String) elem.get("lang");

		// We have a card we support, let check the info
		MagicCard frontCard = new MagicCard();
		MagicCard backCard = new MagicCard();

		int cardLayout = 0; // Single face card by default

		Object layout = elem.get("layout");
		JSONArray card_faces = (JSONArray) elem.get("card_faces");

		JSONObject frontFace = null;
		JSONObject backFace = null;

		if (card_faces != null && card_faces.size() > 0) {
			frontFace = (JSONObject) card_faces.get(0);
			backFace = (JSONObject) card_faces.get(1);
		}

		// Determine the number of "faces/sides/zones", depending of the card type
		switch (layout.toString()) {
		case ("split"):
		case ("flip"):
		case ("adventure"):
		case ("prepare"):
			// Two cards, same face
			cardLayout = 1;
			break;

		case ("transform"):
		case ("modal_dfc"):
		case ("battle"):
		case ("double_faced_token"):
		case ("reversible_card"):
			// Two cards, 2 faces
			cardLayout = 2;
			break;

		case ("scheme"):
		case ("token"):
		case ("emblem"):
		case ("art_series"):
			// Depends
			if (card_faces != null && card_faces.size() > 1) {
				// 2 cards
				cardLayout = 2;
			} else {
				// Single card
				cardLayout = 0;
			}

			break;

		case ("normal"):
		case ("meld"): // Process only the face for now
		case ("leveler"):
		case ("class"):
		case ("case"):
		case ("saga"):
		case ("mutate"):
		case ("prototype"):
		case ("planar"):
		case ("vanguard"):
		case ("augment"):
		case ("host"):
			// Single face
			// Exception: Meld, we process only the "main Face" for now
			cardLayout = 0;
			break;

		default:
			// Unknown layout, assume single side card by default
			cardLayout = 0;
			break;
		}

		// Read multiverse info, for the ID
		JSONArray gids = (JSONArray) elem.get("multiverse_ids");
		String frontMultiverseString = "";
		String backMultiverseString = "";
		String cardText = "";
		Object tcgId = elem.get("tcgplayer_id");
		Object tcgEtchedId = elem.get("tcgplayer_etched_id");
		Object rulings_uri = elem.get("rulings_uri");
		String rulingsUriString = "";

		// Always use Scryfall ID
		frontCard.setCardId(elem.get("id").toString());
		backCard.setCardId("-" + elem.get("id").toString());
		if (gids != null && gids.size() == 2) {
			frontMultiverseString = "MID: " + gids.get(0).toString() + " // " + gids.get(1).toString() + "<br>";
			backMultiverseString = "MID: " + gids.get(1).toString() + " // " + gids.get(0).toString() + "<br>";
		} else if (gids != null && gids.size() == 1) {
			frontMultiverseString = "MID: " + gids.get(0).toString() + "<br>";
			backMultiverseString = "MID: " + gids.get(0).toString() + "<br>";
		} else {
			frontMultiverseString = "";
			backMultiverseString = "";
		}

		String languageString = BuildLanguage(elem.get("lang").toString());
		String finishesString = BuildFinishes((JSONArray) elem.get("finishes"));
		String legalitiesString = BuildLegalities((JSONObject) elem.get("legalities"));
		String legalitiesText = "";
		String scryfallUriString = "<br><a href=\"" + ((String) elem.get("scryfall_uri")) + "\">Scryfall</a>";
		String fullArtString = BuildText("FullArt: ", elem.get("full_art").toString());
		String textlessString = BuildText("TextLess: ", elem.get("textless").toString());
		String storySpotlightString = BuildText("StorySpotlight: ", elem.get("story_spotlight").toString());
		String boosterString = BuildText("Booster: ", elem.get("textless").toString());
		String promoTypesString = BuildPromos((JSONArray) elem.get("promo_types"));
		String priceString = "";

		JSONObject purchaseUri = (JSONObject) elem.get("purchase_uris");
		String tcgUriString = "";

		if (!generateFlat) {
			if (purchaseUri != null && purchaseUri.size() > 0) {
				Object tcg = purchaseUri.get("tcgplayer");
				if (tcg != null) {
					tcgUriString = "   <a href=\"" + ((String) tcg) + "\">TcgPlayer</a>";

					float price = 0f;
					float price_foil = 0f;

					JSONObject prices = (JSONObject) elem.get("prices");

					if (prices != null && prices.size() >= 0) {
						Object obj = prices.get("usd");

						if (obj != null) {
							price = Float.parseFloat(obj.toString());
						}

						if (price == 0f) {

							// Try EUR price if USD is not available
							obj = prices.get("eur");
							if (obj != null) {

								float eur = Float.parseFloat(obj.toString());

								// If EUR exists, convert it to USD
								if (eur != 0f) {
									double eurToUsd = CurrencyConvertor.getRate(Currency.getInstance("EUR"),
											Currency.getInstance("USD"));
									price = eur * (float) eurToUsd;
								}
							}

							if (price == 0f) {
								price = -0.0001f;
							}
						}

						obj = prices.get("usd_foil");
						if (obj != null) {
							price_foil = Float.parseFloat(obj.toString());
						}

						if (price_foil == 0f) {

							// Try Etched
							obj = prices.get("usd_etched");
							if (obj != null) {
								price_foil = Float.parseFloat(obj.toString());
							}

							if (price_foil == 0f) {

								// Try EUR price if USD is not available
								obj = prices.get("eur_foil");
								if (obj != null) {

									float eur_foil = Float.parseFloat(obj.toString());

									// If EUR exists, convert it to USD
									if (eur_foil != 0f) {
										double eurToUsd = CurrencyConvertor.getRate(Currency.getInstance("EUR"),
												Currency.getInstance("USD"));
										price_foil = eur_foil * (float) eurToUsd;
									}
								}
							}

							if (price_foil == 0f) {
								price_foil = -0.0001f;
							}
						}
						priceStore.setDbPrice(frontCard, price);
						priceStore.setDbPriceFoil(frontCard, price_foil);
						priceProvider.setDbPrice(frontCard.getCardId(), price, CurrencyConvertor.USD);
						priceProvider.setDbPriceFoil(frontCard.getCardId(), price_foil, CurrencyConvertor.USD);

					}
				}
			}
		}

		if (!generateFlat) {
			priceString = BuildPrice((JSONObject) elem.get("prices"));

			legalitiesText = "<br>" + legalitiesString.replace(";", "<br>");

			updateString = "<br>Updated on " + java.time.LocalDateTime.now()
					.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

		}

		JSONObject relatedUri = (JSONObject) elem.get("related_uris");
		String gathererUriString = "";

		if (relatedUri != null && relatedUri.size() > 0) {
			Object gatherer = relatedUri.get("gatherer");
			if (gatherer != null) {
				gathererUriString = "   <a href=\"" + ((String) gatherer) + "\">Gatherer</a>";
			}
		}

		if (rulings_uri != null) {
			rulingsUriString = "<BR><a href=\"" + ((String) rulings_uri) + "\">Rulings</a>";
		}

		JSONObject image_uris = (JSONObject) elem.get("image_uris");

		switch (cardLayout) {
		// Standard single face card
		case 0:

			frontCard.setName(elem.get("name").toString());
			frontCard.setLanguage(languageString);
			frontCard.setSet(elem.get("set_name").toString());
			frontCard.setCost(elem.get("mana_cost").toString());
			frontCard.setType(elem.get("type_line").toString().replace("—", "-"));
			frontCard.setRarity(elem.get("rarity").toString());
			frontCard.setPower(elem.get("power") != null ? elem.get("power").toString() : "");
			frontCard.setToughness(elem.get("toughness") != null ? elem.get("toughness").toString() : "");
			String oracle = "";
			if (elem.get("oracle_text") != null) {
				oracle = elem.get("oracle_text").toString();
			} else {
				System.err.println("Card " + frontCard.getName() + " has no oracle text, check for new layout");
			}

			frontCard.setOracleText(oracle.replace("|", "&vert;"));
			frontCard.setArtist(elem.get("artist").toString());
			frontCard.setCollNumber(elem.get("collector_number").toString());

			// Check if an image exist
			if (elem.get("image_status") != "missing") {
				JSONObject images = null;

				// Get the image
				if (image_uris.size() > 0) {
					images = image_uris;
				} else if (card_faces.size() > 0 && frontFace != null) {
					images = (JSONObject) frontFace.get("image_uris");
				}

				if (images != null) {
					// We select the "normal" format, this is the best fit
					String image = images.get("normal").toString();

					if (image == null || image.isEmpty()) {
						image = images.get(0).toString();
					}
					if (image != null && !(image.toString().contains("errors.scryfall"))) {
						frontCard.set(MagicCardField.IMAGE_URL, image);
					}
				}
			}

			// Useful information for collectors
			// We will use the Text field to store and display that information
			cardText = priceString + frontMultiverseString + finishesString + fullArtString + textlessString
					+ boosterString + storySpotlightString + promoTypesString + scryfallUriString + gathererUriString
					+ tcgUriString + rulingsUriString + legalitiesText + updateString;

			frontCard.setText(cardText);
			frontCard.setLanguage(languageString);

			// Add legality if we're doing a live update
			if (!generateFlat) {
				frontCard.set(MagicCardField.LEGALITY, legalitiesString);
			}
			if (gids != null && gids.size() > 0) {
				frontCard.setGathererCardId(gids.get(0).toString());
			}

			if (!generateFlat) {
				if (tcgId != null) {
					frontCard.setTcgCardId(tcgId.toString());
				} else if (tcgEtchedId != null) {
					frontCard.setTcgCardId(tcgEtchedId.toString());
				}
			}

			handler.handleCard(frontCard);
			break;

		// One face, 2 cards
		case 1:
			// 2 faces
		case 2:

			frontCard.setName(elem.get("name").toString());
			backCard.setName(elem.get("name").toString() + " (" + layout.toString() + ")");

			frontCard.setLanguage(languageString);
			backCard.setLanguage(languageString);

			frontCard.setSet(elem.get("set_name").toString());
			backCard.setSet(frontCard.getSet());

			frontCard.setCost(frontFace.get("mana_cost").toString());
			backCard.setCost(backFace.get("mana_cost").toString());

			frontCard.setType(frontFace.get("type_line").toString().replace("—", "-"));
			backCard.setType(
					backFace.get("type_line") != null ? backFace.get("type_line").toString().replace("—", "-") : "");

			frontCard.setRarity(elem.get("rarity").toString());
			backCard.setRarity(frontCard.getRarity());

			frontCard.setPower(frontFace.get("power") != null ? frontFace.get("power").toString() : "");
			backCard.setPower(backFace.get("power") != null ? backFace.get("power").toString() : "");

			frontCard.setToughness(frontFace.get("toughness") != null ? frontFace.get("toughness").toString() : "");
			backCard.setToughness(backFace.get("toughness") != null ? backFace.get("toughness").toString() : "");

			frontCard.setOracleText(frontFace.get("oracle_text") != null
					? (frontFace.get("oracle_text").toString().replace("|", "&vert;"))
					: "");
			backCard.setOracleText(backFace.get("oracle_text") != null
					? (backFace.get("oracle_text").toString().replace("|", "&vert;"))
					: "");

			frontCard.setArtist(frontFace.get("artist") != null ? frontFace.get("artist").toString() : "");
			backCard.setArtist(backFace.get("artist") != null ? backFace.get("artist").toString() : "");

			frontCard.setCollNumber(elem.get("collector_number").toString());
			backCard.setCollNumber(frontCard.getCollNumber() + "b");

			// Check if an image exist
			if (elem.get("image_status") != "missing") {
				JSONObject images = null;

				if (cardLayout == 2) {
					// Get the image
					if (frontFace != null) {
						images = (JSONObject) frontFace.get("image_uris");
					}

					if (images != null) {
						String image = images.get("normal").toString();

						if (image == null || image.isEmpty()) {
							image = images.get(0).toString();
						}
						if (image != null && !(image.toString().contains("errors.scryfall"))) {
							frontCard.set(MagicCardField.IMAGE_URL, image);
						}
					}

					if (backFace != null) {
						images = (JSONObject) backFace.get("image_uris");
					}

					if (images != null) {
						String image = images.get("normal").toString();

						if (image == null || image.isEmpty()) {
							image = images.get(0).toString();
						}
						if (image != null && !(image.toString().contains("errors.scryfall"))) {
							backCard.set(MagicCardField.IMAGE_URL, image);
						}
					}

				} else {
					// Get the image
					if (image_uris.size() > 0) {
						images = image_uris;
					} else if (card_faces.size() > 0 && frontFace != null) {
						images = (JSONObject) frontFace.get("image_uris");
					}

					if (images != null) {
						String image = images.get("normal").toString();

						if (image == null || image.isEmpty()) {
							image = images.get(0).toString();
						}
						if (image != null && !(image.toString().contains("errors.scryfall"))) {
							frontCard.set(MagicCardField.IMAGE_URL, image);
							backCard.set(MagicCardField.IMAGE_URL, image);
						}
					}
				}
			}

			// Useful for collectors
			cardText = finishesString + fullArtString + textlessString + boosterString + storySpotlightString
					+ promoTypesString + scryfallUriString + gathererUriString + tcgUriString + rulingsUriString
					+ legalitiesText + updateString;

			frontCard.setText(priceString + frontMultiverseString + cardText);
			backCard.setText(backMultiverseString + cardText);

			if (!generateFlat) {
				frontCard.set(MagicCardField.LEGALITY, legalitiesString);
				backCard.set(MagicCardField.LEGALITY, frontCard.get(MagicCardField.LEGALITY));
			}

			frontCard.set(MagicCardField.FLIPID, backCard.getCardId());
			backCard.set(MagicCardField.FLIPID, frontCard.getCardId());

			if (gids != null && gids.size() > 0) {
				frontCard.setGathererCardId(gids.get(0).toString());
			}

			if (!generateFlat) {
				if (tcgId != null) {
					frontCard.setTcgCardId(tcgId.toString());
				} else if (tcgEtchedId != null) {
					frontCard.setTcgCardId(tcgEtchedId.toString());
				}
			}

			handler.handleCard(frontCard);
			handler.handleCard(backCard);
			break;
		}

		collectSymbols(frontCard.getCost(), frontCard.getName(), costSymbols);
		collectSymbols(backCard.getCost(), backCard.getName(), costSymbols);
		collectSymbols(frontCard.getOracleText(), frontCard.getName(), textSymbols);
		collectSymbols(backCard.getOracleText(), backCard.getName(), textSymbols);

	}

	@Override
	public URL getSearchQuery(String set) throws MalformedURLException {
		String url;
		if (set != null && set.startsWith("http")) {
			url = set;
		} else {
			// We always want all the prints, extras, all variations
			String out = "&unique=prints&include_extras=true&include_variations=true";
			String abbr = Editions.getInstance().getAbbrByName(set);
			if (abbr == null)
				abbr = set;

			// We don't restrict by lang on the query, accept everyting Scryfall provides by
			// default
			// Search for a specific Set (abbr)
			// Example:
			// https://api.scryfall.com/cards/search?q=e%3ATSPM&unique=prints&include_extras=true&include_variations=true
			String base = BASE_SEARCH_URL + "q=e%3A" + abbr + out;
			url = base;
		}
		return new URL(url);
	}

	public static class SortedOutputHanlder extends OutputHandler {
		private ArrayList<MagicCard> primary = new ArrayList<>();

		public SortedOutputHanlder(PrintStream st, boolean loadLandPrintings, boolean loadOtherPrintings) {
			super(st, loadLandPrintings, loadOtherPrintings);
		}

		@Override
		public void handleCard(MagicCard card) {
			primary.add(card);
			count++;
		}

		@Override
		public void onEnd() {
			printHeader();
			Collections.sort(primary, (o1, o2) -> o1.getCollectorNumberId() - o2.getCollectorNumberId());
			for (MagicCard magicCard : primary) {
				printCard(magicCard);
			}
		}

		public List<MagicCard> getPrimary() {
			return primary;
		}
	}

	// Used to create the flat resource files
	public void saveAllFlat(File dir) throws IOException {

		generateFlat = true;

		this.includeImagesUrl = false;

		Editions editions = Editions.getInstance(true);

		ParseScryFallSets setsLoader = new ParseScryFallSets();

		setsLoader.loadSets(true);

		Collection<Edition> sets = setsLoader.getAll();

		dir.mkdirs();

		try {

			File file = new File(dir, "/editions.txt");

			editions.save(file);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		int size = editions.getEditions().size();
		int i = 0;

		for (Edition x : editions.getEditions()) {
			i++;

			int prog = Math.round(i / (float) size * 100);
			System.out.println("Set \"" + x.getName() + "\" written in " + dir.getAbsolutePath() + "\\"
					+ x.getMainAbbreviation() + ".txt (" + prog + "%)");
			saveEditionText(dir, x);
		}
	}

	// Use to create a set flat file
	public void saveEditionText(File dir, Edition x) {
		String base = x.getBaseFileName() + ".txt";
		for (String abbr : x.getAbbreviations()) {
			File file = new File(dir, base);
			try (PrintStream out = new PrintStream(file)) {
				SortedOutputHanlder handler = new SortedOutputHanlder(out, true, true);
				this.loadSet(abbr, handler, ICoreProgressMonitor.NONE);
				if (handler.getRealCount() > 0)
					break;
			} catch (Exception e) {
				System.err.println(e);
			}
			file.delete();
		}
	}

	public void downloadAndSaveEdition(File dir, String set) {
		System.out.println("Downloading " + set + " from Scryfall");

		loadTcgMediumPrices();

		try (PrintStream out = new PrintStream(dir)) {
			SortedOutputHanlder handler = new SortedOutputHanlder(out, true, true);
			this.loadSet(set, handler, ICoreProgressMonitor.NONE);
		} catch (Exception e) {
			System.err.println(e);
		}
		try {
			priceProvider.save();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Load the locally cached "TCG Player (Medium)" price file into
	 * {@link #priceProvider} so that generated flat files carry price data. Silent
	 * no-op when the file is not present.
	 */
	private void loadTcgMediumPrices() {
		File pricesDir = DataManager.getInstance().getPricesDir();
		// !!! RD For now, hardcoded
		File file = new File(pricesDir, "TCG_Player__Medium_.xml");
		if (!file.exists())
			return;
		try (BufferedInputStream st = new BufferedInputStream(new FileInputStream(file),
				FileUtils.DEFAULT_BUFFER_SIZE)) {
			priceProvider.loadPrices(st);
		} catch (IOException e) {
			MagicLogger.log(e);
		}
	}

	/**
	 * Single streaming pass over the Scryfall "Default Cards" bulk file (see
	 * {@link ScryfallBulkCache}), grouping printings by lower-cased {@code set}
	 * code. The bulk file is gzip-compressed JSON Lines (one card object per
	 * line), so it is never held whole in memory.
	 *
	 * @param bulkFile
	 *            the local Default Cards file ({@code .jsonl.gz})
	 * @param onlyLower
	 *            if non-null, keep only these (lower-cased) set codes; if null,
	 *            keep every set found in the file
	 * @return a map from lower-cased set code to that set's cards
	 */
	private Map<String, List<MagicCard>> parseBulkGrouped(File bulkFile, Set<String> onlyLower) throws IOException {
		loadTcgMediumPrices();
		Map<String, List<MagicCard>> result = new HashMap<>();
		if (onlyLower != null)
			for (String code : onlyLower)
				result.put(code, new ArrayList<>());

		// parseRecord() pushes cards through ILoadCardHander#handleCard; route
		// each card into the bucket for the line currently being parsed.
		@SuppressWarnings("unchecked")
		final List<MagicCard>[] bucket = new List[1];
		ILoadCardHander router = new ILoadCardHander() {
			@Override
			public void handleCard(MagicCard card) {
				if (bucket[0] != null)
					bucket[0].add(card);
			}

			@Override
			public void handleSecondary(MagicCard primary, MagicCard secondary) {
				handleCard(secondary);
			}

			@Override
			public void handleEdition(Edition ed) {
				// set list is refreshed separately
			}

			@Override
			public void setCardCount(int count) {
				// not applicable for a bulk pass
			}

			@Override
			public int getCardCount() {
				return 0;
			}

			@Override
			public int getRealCount() {
				return bucket[0] == null ? 0 : bucket[0].size();
			}
		};

		JSONParser parser = new JSONParser();
		long records = 0;
		long matched = 0;
		long t0 = System.currentTimeMillis();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new GZIPInputStream(new FileInputStream(bulkFile)), FileUtils.CHARSET_UTF_8),
				FileUtils.DEFAULT_BUFFER_SIZE)) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.charAt(0) != '{') {
					// tolerate a stray wrapping "[" / "]" / "," if Scryfall ever
					// switches back to a plain JSON array
					int b = line.indexOf('{');
					if (b < 0)
						continue;
					line = line.substring(b);
				}
				if (line.endsWith(","))
					line = line.substring(0, line.length() - 1);
				records++;
				try {
					JSONObject elem = (JSONObject) parser.parse(line);
					Object set = elem.get("set");
					if (set == null)
						continue;
					String code = set.toString().toLowerCase(Locale.ROOT);
					List<MagicCard> b;
					if (onlyLower == null) {
						b = result.computeIfAbsent(code, k -> new ArrayList<>());
					} else {
						b = result.get(code);
						if (b == null)
							continue;
					}
					bucket[0] = b;
					parseRecord(elem, router);
					matched++;
				} catch (ParseException e) {
					MagicLogger.log(e);
				}
			}
		} finally {
			bucket[0] = null;
		}
		int priceCount = priceProvider.getPriceMap().size();
		try {
			priceProvider.save();
			System.err.println("[ScryfallBulk] saved " + priceCount + " prices to "
					+ com.reflexit.magiccards.core.xml.PricesXmlStreamWriter.getPricesFile(priceProvider));
		} catch (Exception e) {
			System.err.println("[ScryfallBulk] FAILED to save " + priceCount + " prices: " + e);
			MagicLogger.log(e);
		}
		System.err.println("[ScryfallBulk] parsed " + records + " records in " + (System.currentTimeMillis() - t0)
				+ " ms, matched " + matched + " printing(s) for "
				+ (onlyLower == null ? result.size() + " set(s) (full split)" : "abbreviations " + onlyLower));
		return result;
	}

	/**
	 * Cards for the given (lower-cased) set codes, from one filtered pass over the
	 * bulk file. A key is present for every requested code (possibly empty).
	 */
	public Map<String, List<MagicCard>> groupSetsFromBulk(File bulkFile, Set<String> setCodesLower) throws IOException {
		return parseBulkGrouped(bulkFile, setCodesLower);
	}

	/**
	 * One pass over the bulk file, writing {@code <outDir>/<code>.txt.gz} for
	 * every set that has at least one paper printing.
	 *
	 * @return the set codes written
	 */
	public Set<String> splitAllFromBulk(File bulkFile, File outDir) throws IOException {
		Map<String, List<MagicCard>> all = parseBulkGrouped(bulkFile, null);
		outDir.mkdirs();
		Set<String> written = new java.util.HashSet<>();
		for (Map.Entry<String, List<MagicCard>> e : all.entrySet()) {
			if (e.getValue().isEmpty())
				continue;
			writeSetFlatGz(e.getValue(), new File(outDir, e.getKey() + ".txt.gz"));
			written.add(e.getKey());
		}
		return written;
	}

	/**
	 * Write the flat text (header + collector-number-sorted lines) for one set's
	 * cards, using the same {@link SortedOutputHanlder} the REST path uses.
	 */
	public void writeSetFlat(List<MagicCard> cards, PrintStream out) {
		SortedOutputHanlder handler = new SortedOutputHanlder(out, true, true);
		for (MagicCard card : cards)
			handler.handleCard(card);
		handler.onEnd();
	}

	/** {@link #writeSetFlat} to a gzip file, written atomically (temp + rename). */
	public void writeSetFlatGz(List<MagicCard> cards, File gzFile) throws IOException {
		File parent = gzFile.getParentFile();
		if (parent != null)
			parent.mkdirs();
		File tmp = File.createTempFile(gzFile.getName() + "-", ".tmp", parent);
		try {
			try (PrintStream out = new PrintStream(
					new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tmp)),
							FileUtils.DEFAULT_BUFFER_SIZE),
					false, FileUtils.UTF8)) {
				writeSetFlat(cards, out);
			}
			Files.move(tmp.toPath(), gzFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} finally {
			tmp.delete();
		}
	}

	public void printCollectedSymbols() {
		System.out.println("=== Mana Cost Symbols ===");
		costSymbols.forEach(System.out::println);

		System.out.println("\n=== Text Symbols ===");
		textSymbols.forEach(System.out::println);
	}

	public static void main(String[] args) throws MalformedURLException, IOException {
		// Important! Run this to create the flat files required to update the Db
		// resource
		// Files will be located under TEXT_EXPORT_DIR
		// Build the database from Scryfall and export flat files
		ParseScryFallChecklist db = new ParseScryFallChecklist();

		db.saveAllFlat(new File(TEXT_EXPORT_DIR));

		// Print all collected mana/text symbols
		db.printCollectedSymbols();
	}
}
