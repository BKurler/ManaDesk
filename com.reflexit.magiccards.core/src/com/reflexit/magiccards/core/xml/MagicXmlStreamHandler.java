/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk; build a fresh MagicXmlStreamWriter
 *                         per call so "Update Card Database" can write the per-set
 *                         files on several threads (the old shared static writer
 *                         was not reentrant)
 */
package com.reflexit.magiccards.core.xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.model.IMagicCard;

public class MagicXmlStreamHandler implements IStoreHandler {
	private static final MagicXmlStreamReader reader = new MagicXmlStreamReader();

	@Override
	public CardCollectionStoreObject load(File file) throws IOException {
		return reader.load(file);
	}

	public CardCollectionStoreObject load(InputStream st) throws IOException {
		return reader.load(st);
	}

	@Override
	public void save(CardCollectionStoreObject object) throws IOException {
		// A fresh writer per call: MagicXmlStreamWriter keeps a mutable stream
		// field, so a shared instance is not reentrant. Per-call instances let
		// "Update Card Database" write the ~1000 set files on several threads.
		new MagicXmlStreamWriter().write(object);
	}

	public void save(CardCollectionStoreObject object, OutputStream st) throws IOException {
		new MagicXmlStreamWriter().write(object, st);
	}

	public String toXML(IMagicCard card) {
		CardCollectionStoreObject object = new CardCollectionStoreObject();
		object.list = new ArrayList<IMagicCard>();
		object.list.add(card);
		ByteArrayOutputStream st = new ByteArrayOutputStream();
		try {
			save(object, st);
		} catch (IOException e) {
			// ignore
		}
		try {
			st.close();
		} catch (IOException e) {
			// ignore
		}
		return st.toString();
	}

	public CardCollectionStoreObject fromXML(String xml) {
		try {
			return load(new ByteArrayInputStream(xml.getBytes(FileUtils.CHARSET_UTF_8)));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
