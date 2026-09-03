

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.model;

import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.expr.TextValue;

public interface IMagicCard extends ICard {
	public static final MagicCard DEFAULT = new MagicCard();
	public static final float STAR_POWER = 0.99F;
	public static final float NOT_APPLICABLE_POWER = Float.NaN;

	public abstract String getCost();

	public abstract String getCardId();

	public abstract String getGathererCardId();

	public abstract String getTcgCardId();

	public abstract int getGathererId();

	public abstract int getTcgId();

	@Override
	public abstract String getName();

	public abstract String getOracleText();

	public abstract String getRarity();

	public abstract String getSet();

	public abstract String getType();

	public abstract String getPower();

	public abstract String getToughness();

	public abstract String getColorType();

	public abstract int getCmc();

	public abstract float getDbPrice();

	public abstract float getCommunityRating();

	public abstract String getArtist();

	public abstract String getRulings();

	/** Encoded list of physical accessories the card needs (tokens/emblems/counters/dice/markers). */
	public abstract String getAccessories();

	@Override
	public IMagicCard cloneCard();

	public abstract MagicCard getBase();

	public abstract String getText();

	public abstract String getLanguage();

	public abstract boolean matches(ICardField left, TextValue right);

	public abstract String getEnglishCardId();

	public abstract String getFlipId();

	public int getUniqueCount();

	public abstract int getSide();

	public abstract String getCollectorId();

	public abstract int getCollectorNumberId();

	public LegalityMap getLegalityMap();

	public boolean isBasicLand();

	public String getEnglishName();

	public abstract Edition getEdition();
}