

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - isBoxed()/setBoxed(): manual "physically boxed up"
 *                         marker for the Proxier view (independent of
 *                         virtual/unsorted/readonly - no automatic behavior
 *                         attached)
 *     Rémi Dutil (2026) - getDefaultFormat()/setDefaultFormat(): a deck's own
 *                         default legality format (Standard/Modern/Commander/...),
 *                         used by the Legality tab to decide which format to
 *                         validate against without the user re-picking it
 *                         every time. Deck-only in spirit (not applicable to
 *                         collections) - same "no automatic enforcement"
 *                         contract as the other flags, callers decide what to
 *                         do with it. Promotes what DeckLegalityPage2 already
 *                         stored ad hoc under the raw "format" property key
 *                         into a proper typed accessor - same key, no
 *                         migration needed
 */

package com.reflexit.magiccards.core.model.storage;

public interface IStorageInfo {
	public static final String DECK_TYPE = "deck";
	public static final String COLLECTION_TYPE = "collection";

	public String getComment();

	public String getProperty(String key);

	public String getType();

	public void setComment(String text);

	public void setProperty(String key, String value);

	public void setType(String string);

	public void setVirtual(boolean value);

	public void setUnsorted(boolean value);

	public boolean isVirtual();

	public boolean isUnsorted();

	public boolean isReadOnly();

	public String getName();

	public void setReadOnly(boolean value);

	public void setBoxed(boolean value);

	public boolean isBoxed();

	/** The deck's default legality format ("Standard", "Modern", ...), or
	 *  {@code null} if never set - see the class header. */
	public String getDefaultFormat();

	public void setDefaultFormat(String format);
}
