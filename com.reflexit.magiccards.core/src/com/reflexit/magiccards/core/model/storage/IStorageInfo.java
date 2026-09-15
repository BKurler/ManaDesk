

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - isBoxed()/setBoxed(): manual "physically boxed up"
 *                         marker for the Proxier view (independent of
 *                         virtual/unsorted/readonly - no automatic behavior
 *                         attached)
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
}
