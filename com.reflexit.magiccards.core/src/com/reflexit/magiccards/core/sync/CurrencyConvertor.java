/*
 * Contributors:
 *     Rémi Dutil (2026) - avoid re-fetching exchange rates several times per session
 */
package com.reflexit.magiccards.core.sync;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.reflexit.magiccards.core.Activator;
import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;

public class CurrencyConvertor {
	public static String EUR = "EUR";
	public static Currency USD = Currency.getInstance("USD");
	private static final String FILE = "currency.txt";
	private static HashMap<String, Double> rates = new HashMap<>();
	private static HashMap<String, Date> dates = new HashMap<>();
	private static Currency currency = USD;
	private static SimpleDateFormat DATE_PARSER = new SimpleDateFormat("MM/dd/yyyy hh:mmaa", Locale.ENGLISH);
	private static String EURO_BANK = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml";

	public static double loadRate(String from, String to) {
		if (rates.get(convertCu(EUR, "USD")) == null) {
			update();
		}
		return calculateRate(from, to);
	}

	private static synchronized Double calculateRate(String from, String to) {
		if (from.equals(to))
			return 1.0;
		Double rate = rates.get(convertCu(from, to));
		if (rate != null)
			return rate;
		Double one = rates.get(convertCu(from, EUR));
		Double two = rates.get(convertCu(EUR, to));
		if (one != null && two != null) {
			rate = one * two;
			saveRate(from, to, rate, null);
			return rate;
		}
		MagicLogger.log("Cannot convert currency "+from+" "+to);
		return 1.0;
	}

	private static synchronized double doLoadRateFromCC(String cu) {
		try {
			URL url = getURL(convertCu(cu));
			// {"USD_PHP":50.310001}
			String res = WebUtils.openUrlText(url);
			String[] parts = res.split(":");
			if (parts.length < 2)
				throw new IllegalArgumentException("Cannot parse " + res);
			double rate = Double.parseDouble(parts[1].replace("}", ""));
			rates.put(cu, rate);
			Date date = Calendar.getInstance().getTime();
			MagicLogger.log("Convertion rate " + cu + "=" + rate);
			dates.put(cu, date);
			return rate;
		} catch (Exception e) {
			return 0;
		}
	}

	private static URL getURL(String cu) throws MalformedURLException {
		URL url = new URL("https://free.currencyconverterapi.com/api/v5/convert?q=" + cu
				+ "&compact=ultra&apiKey=8280553d986bdbde5834");
		return url;
	}

	/** When {@link #loadEuroBankRates()} last succeeded, to avoid re-fetching the
	 *  ECB rates several times per session (the split, the price store and the
	 *  "check for updates" job all trigger currency loading). */
	private static volatile long lastRatesFetch = 0L;
	private static final long RATES_TTL_MS = 6L * 60L * 60L * 1000L; // 6 hours

	public static synchronized void update() {
		update(false);
	}

	public static synchronized void update(boolean force) {
		if (WebUtils.isWorkOffline())
			return;
		if (!force && System.currentTimeMillis() - lastRatesFetch < RATES_TTL_MS
				&& rates.get(convertCu(EUR, "USD")) != null)
			return; // already have fresh rates this session
		loadEuroBankRates();
	}

	private static Pattern ratePattern = Pattern.compile("currency='([^']*)' rate='([^']*)'");

	private static void loadEuroBankRates() {
		try {
			URL url = new URL(EURO_BANK);
			String res = WebUtils.openUrlText(url);
			Date date = Calendar.getInstance().getTime();
			try (BufferedReader st = FileUtils.openBufferedReader(res)) {
				String line;
				while ((line = st.readLine()) != null) {
					Matcher matcher = ratePattern.matcher(line);
					if (matcher.find()) {
						String cur = matcher.group(1);
						String srate = matcher.group(2);
						double rate = Double.parseDouble(srate);
						saveRate(EUR, cur, rate, date);
					}
				}
			}
			lastRatesFetch = System.currentTimeMillis();
		} catch (Exception e) {
			MagicLogger.log(e);
		}
	}

	private static void saveRate(String from, String to, Double rate, Date date) {
		if (rate == null || rate == 0.0)
			return;
		saveRate(convertCu(from, to), rate, date);
		saveRate(convertCu(to, from), 1.0 / rate, date);
	}

	private static void saveRate(String cu, Double rate, Date date) {
		if (date == null)
			date = Calendar.getInstance().getTime();
		rates.put(cu, rate);
		dates.put(cu, date);
		MagicLogger.log("Convertion rate " + cu + "=" + rate);
	}

	public static void main(String[] args) {
		update();
	}

	static {
		try {
			load();
		} catch (IOException e) {
			MagicLogger.log(e);
		}
	}

	public static synchronized void save() throws FileNotFoundException {
		File file = getCurrencyFile();
		save(file);
	}

	public static File getCurrencyFile() {
		File file = new File(DataManager.getInstance().getTablesDir(), FILE);
		return file;
	}

	public synchronized static void save(File file) throws FileNotFoundException {
		PrintStream st = new PrintStream(file);
		try {
			Date date = Calendar.getInstance().getTime();
			for (String cu : rates.keySet()) {
				double rate = rates.get(cu);
				st.print(cu + "|" + rate + "|" + DATE_PARSER.format(date));
				st.print('\n');
			}
		} finally {
			st.close();
		}
	}

	private static synchronized void load() throws IOException {
		File file = getCurrencyFile();
		if (!file.exists()) {
			initialize();
			save();
		} else {
			try {
				initialize();
			} catch (Exception e) {
				// ignore
			}
			InputStream st = new FileInputStream(file);
			load(st);
		}
	}

	private static synchronized void initialize() throws IOException, FileNotFoundException {
		Date date;
		try {
			date = DATE_PARSER.parse("08/08/2020 8:42pm");
		} catch (ParseException e) {
			date = new Date();
		}
		saveRate("USD", EUR, 0.89, date);
		saveRate("USD", "CAD", 1.36, date);
		saveRate("USD", "RUB", 70.0, date);
	}

	private static synchronized void load(InputStream st) throws IOException {
		BufferedReader r = new BufferedReader(new InputStreamReader(st));
		try {
			String line;
			while ((line = r.readLine()) != null) {
				try {
					String[] attrs = line.split("\\|", 3);
					String cu = attrs[0].trim();
					String srate = attrs.length >= 2 ? attrs[1].trim() : "0";
					String sdate = attrs.length >= 3 ? attrs[2].trim() : "";
					double rate = Double.valueOf(srate);
					Date date;
					try {
						date = DATE_PARSER.parse(sdate);
					} catch (ParseException e) {
						date = new Date();
					}
					if (rate != 0) {
						cu = convertCu(cu);
						if (!cu.isEmpty()) {
							saveRate(cu, rate, date);
						}
					}
				} catch (Exception e) {
					MagicLogger.log("bad currency record: " + line);
					MagicLogger.log(e);
				}
			}
		} finally {
			r.close();
		}
	}

	private static String convertCu(String from, String to) {
		return from + "_" + to;
	}

	private static String convertCu(String cu) {
		if (cu == null || cu.isEmpty())
			return "";
		if (cu.length() == 6) {
			return cu.substring(0, 3) + "_" + cu.substring(3);
		}
		if (cu.contains("_"))
			return cu;
		return "";
	}

	public static void setCurrency(String string) {
		currency = Currency.getInstance(string);
	}

	public static void setCurrency(Currency string) {
		currency = string;
	}

	public static Currency getCurrency() {
		return currency;
	}

	public static float convertFromInto(float price, Currency from, Currency cur) {
		if (from == cur)
			return price;
		return (float) (price * calculateRate(from.getCurrencyCode(), cur.getCurrencyCode()));
	}

	public static double getRate(Currency c1, Currency c2) {
		return calculateRate(c1.getCurrencyCode(), c2.getCurrencyCode());
	}
}
