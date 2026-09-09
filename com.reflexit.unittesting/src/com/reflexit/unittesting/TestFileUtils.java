/*******************************************************************************
 * $QNXLicenseC:
 * Copyright 2008, QNX Software Systems. All Rights Reserved.
 * 
 * You must obtain a written license from and pay applicable license fees to QNX 
 * Software Systems before you may reproduce, modify or distribute this software, 
 * or any work that includes all or part of this software.   Free development 
 * licenses are available for evaluation and non-commercial purposes.  For more 
 * information visit http://licensing.qnx.com or email licensing@qnx.com.
 *  
 * This file may contain contributions from others.  Please review this entire 
 * file for other proprietary rights or license notices, as well as the QNX 
 * Development Suite License Guide at http://licensing.qnx.com/license-guide/ 
 * for other information.
 * $
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - resetDb() seeds a small card fixture (testdb/*.txt); the
 *                         shipped app no longer bundles a card database
 */

/*
 * Created by: Elena Laskavaia
 * Created on: 2011-01-27
 * Last modified by: $Author: elaskavaia $
 */
package com.reflexit.unittesting;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICardField;

/**
 * static utils
 */
public class TestFileUtils {
	public static boolean deleteOnExit = true;

	/**
	 * The app no longer ships a bundled card database, so tests that look real
	 * cards up in the DB (DataManagerTest, ImportUtilsTest, …) must seed one. These
	 * are the sets they need, as pipe-separated fixtures under {@code src/testdb/}.
	 */
	private static final String[] TESTDB_SETS = { "ARN", "LEA", "LEB", "2ED", "3ED", "10E", "HML", "UNH", "LRW", "M10",
			"M11", "M14", "ISD", "THS", "PTHS", "DDG", "DDH", "CMR", "STX", "ONS" };

	public static void resetDb() {
		String user = System.getProperty("user.name");
		File tmpDir = new File(System.getProperty("java.io.tmpdir"));
		File temp = new File(tmpDir, "magiccardsTest-" + user);
		temp.deleteOnExit();
		FileUtils.deleteTree(temp);

		DataManager.getInstance().reset(temp);
		DataManager.getInstance().syncInitDb();
		seedTestCardDb();
		DataManager.getInstance().reconcile();
	}

	/** Load the {@code testdb/*.txt} card fixtures into the (freshly reset) card DB. */
	private static void seedTestCardDb() {
		@SuppressWarnings("unchecked")
		com.reflexit.magiccards.core.model.storage.IDbCardStore<IMagicCard> db = DataManager.getInstance()
				.getMagicDBStore();
		for (String set : TESTDB_SETS) {
			try (InputStream in = TestFileUtils.class.getClassLoader().getResourceAsStream("testdb/" + set + ".txt")) {
				if (in == null) {
					System.err.println("test card fixture missing: testdb/" + set + ".txt");
					continue;
				}
				List<IMagicCard> cards = parseFlatCards(in);
				if (!cards.isEmpty())
					db.addAll(cards);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static List<IMagicCard> parseFlatCards(InputStream in) throws IOException {
		BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
		String header = r.readLine();
		if (header == null)
			return new ArrayList<>();
		ICardField[] fields = MagicCardField.toFields(header, "\\Q|");
		List<IMagicCard> list = new ArrayList<>();
		String line;
		while ((line = r.readLine()) != null) {
			if (line.isEmpty())
				continue;
			String[] v = line.split("\\|", -1);
			MagicCard card = new MagicCard();
			for (int i = 0; i < fields.length && i < v.length; i++)
				card.set(fields[i], v[i]);
			if (card.getCardId() != null)
				list.add(card);
		}
		return list;
	}

	public static void readWriteStream(InputStream readStream, OutputStream writeStream) throws IOException {
		byte[] buffer = new byte[1024 * 4];
		int bytesRead = readStream.read(buffer);
		// write the required bytes
		while (bytesRead > 0) {
			writeStream.write(buffer, 0, bytesRead);
			bytesRead = readStream.read(buffer);
		}
	}

	public static File saveResourceToDir(String path, File dir) throws IOException {
		return saveResource(path, new File(dir, path));
	}

	public static File saveResourceToDirFlat(String path, File dir) throws IOException {
		return saveResource(path, new File(dir, new File(path).getName()));
	}

	public static String saveResourceToString(String path, Class<?> clazz) throws IOException {
		InputStream resourceAsStream = clazz.getClassLoader().getResourceAsStream(path);
		if (resourceAsStream == null)
			throw new FileNotFoundException("Resource not found " + path);
		ByteArrayOutputStream writeStream = new ByteArrayOutputStream();
		readWriteStream(resourceAsStream, writeStream);
		resourceAsStream.close();
		return writeStream.toString();
	}

	public static File saveStringToFile(String string, File saveTo) throws IOException {
		InputStream resourceAsStream = new ByteArrayInputStream(string.getBytes());
		saveTo.getParentFile().mkdirs();
		FileOutputStream out = new FileOutputStream(saveTo);
		readWriteStream(resourceAsStream, out);
		out.close();
		resourceAsStream.close();
		if (deleteOnExit)
			saveTo.deleteOnExit();
		return saveTo;
	}

	public static String loadStringFromFile(File loadFrom) throws IOException {
		InputStream resourceAsStream = new FileInputStream(loadFrom);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		readWriteStream(resourceAsStream, out);
		out.close();
		resourceAsStream.close();
		return out.toString();
	}

	public static File saveResource(String path, File saveTo) throws IOException {
		InputStream resourceAsStream = TestFileUtils.class.getClassLoader().getResourceAsStream(path);
		if (resourceAsStream == null)
			throw new FileNotFoundException("Resource not found " + path);
		saveTo.getParentFile().mkdirs();
		FileOutputStream out = new FileOutputStream(saveTo);
		readWriteStream(resourceAsStream, out);
		out.close();
		resourceAsStream.close();
		if (deleteOnExit)
			saveTo.deleteOnExit();
		return saveTo;
	}

	/**
	 * Returns an array of StringBuilder objects for each comment section found preceding the named test in the source code.
	 *
	 * @param bundle
	 *            the bundle containing the source, if {@code null} can try to load using classpath (source folder has to be in the
	 *            classpath for this to work)
	 * @param srcRoot
	 *            the directory inside the bundle containing the packages
	 * @param clazz
	 *            the name of the class containing the test
	 * @param testName
	 *            the name of the test
	 * @param numSections
	 *            the number of comment sections preceding the named test to return. Pass zero to get all available sections.
	 * @return an array of StringBuilder objects for each comment section found preceding the named test in the source code.
	 * @throws IOException
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 */
	public static StringBuilder[] getContentsForTest(
	        String srcRoot,
	        Class<?> clazz,
	        String testName,
	        int numSections
	) throws IOException, NoSuchMethodException {

	    // 1. Trouver la classe qui déclare réellement la méthode de test
	    while (clazz.getMethod(testName).getDeclaringClass() != clazz) {
	        clazz = clazz.getSuperclass();
	    }

	    while (true) {
	        // 2. Construire le chemin vers le fichier source .java
	        String fqn = clazz.getName().replace('.', '/');
	        int dollar = fqn.indexOf('$');
	        if (dollar != -1) {
	            fqn = fqn.substring(0, dollar); // enlever les classes internes
	        }

	        Path path = Paths.get(srcRoot, fqn + ".java");

	        if (!Files.exists(path)) {
	            throw new IOException("Source not found: " + path);
	        }

	        // 3. Lire le fichier source
	        try (InputStream in = Files.newInputStream(path)) {
	            StringBuilder[] comments = extractComments(testName, numSections, in);
	            if (comments != null) {
	                return comments;
	            }
	        }

	        // 4. Remonter dans la hiérarchie si nécessaire
	        Class<?> superclass = clazz.getSuperclass();
	        if (superclass == null || !superclass.getPackage().equals(clazz.getPackage())) {
	            throw new IOException("Test data not found for " + clazz.getName() + "." + testName);
	        }

	        clazz = superclass;
	    }
	}

	public static StringBuilder[] extractComments(final String testName, int numSections, InputStream in) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		try {
			// Read the java file collecting comments until we encounter the
			// test method.
			List<StringBuilder> contents = new ArrayList<StringBuilder>();
			StringBuilder content = new StringBuilder();
			boolean inComment = false;
			for (String line1 = br.readLine(); line1 != null; line1 = br.readLine()) {
				// Replace leading whitespace, preserve trailing
				String line = line1.replaceFirst("^\\s*", "");
				if (inComment && line.contains("*/")) {
					inComment = false;
				} else if (inComment) {
					content.append(line1.replaceFirst("\\t+", "") + "\n");
				} else if (line.startsWith("//")) {
					content.append(line.substring(2) + "\n");
				} else if (line.startsWith("@")) {
					// ignore annotations
				} else if (line.trim().isEmpty()) {
					// ignore empty lines
				} else if (line.contains("/*-")) {
					inComment = true;
				} else {
					if (content.length() > 0) {
						// add new section
						contents.add(content);
						if (numSections > 0 && contents.size() == numSections + 1)
							contents.remove(0);
						content = new StringBuilder();
					}
					if (line.matches("^[^=]*\\b" + testName + "\\b.*")) {
						return contents.toArray(new StringBuilder[contents.size()]);
					}
					// was not our function, reset sections
					contents.clear();
				}
			}
		} finally {
			br.close();
		}
		return null;
	}
}
