/*
 * Contributors:
 *     Rémi Dutil (2026) - curated to the columns that make sense for whole-database
 *     collection tracking (drop the per-copy deck/collection columns)
 */
package com.reflexit.magiccards.ui.views.collector;

import java.util.List;

import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.preferences.CollectorViewPreferencePage;
import com.reflexit.magiccards.ui.views.columns.AbstractColumn;
import com.reflexit.magiccards.ui.views.columns.ColorColumn;
import com.reflexit.magiccards.ui.views.columns.ColorIdentityColumn;
import com.reflexit.magiccards.ui.views.columns.CommunityRatingColumn;
import com.reflexit.magiccards.ui.views.columns.CostColumn;
import com.reflexit.magiccards.ui.views.columns.GenColumn;
import com.reflexit.magiccards.ui.views.columns.GroupColumn;
import com.reflexit.magiccards.ui.views.columns.LanguageColumn;
import com.reflexit.magiccards.ui.views.columns.LegalityColumn;
import com.reflexit.magiccards.ui.views.columns.MagicColumnCollection;
import com.reflexit.magiccards.ui.views.columns.OracleTextColumn;
import com.reflexit.magiccards.ui.views.columns.OwnCountColumn;
import com.reflexit.magiccards.ui.views.columns.OwnTotalCountColumn;
import com.reflexit.magiccards.ui.views.columns.OwnUniqueColumn;
import com.reflexit.magiccards.ui.views.columns.PowerColumn;
import com.reflexit.magiccards.ui.views.columns.PriceColumn;
import com.reflexit.magiccards.ui.views.columns.ReleaseDateColumn;
import com.reflexit.magiccards.ui.views.columns.TextColumn;
import com.reflexit.magiccards.ui.views.columns.TypeColumn;

/**
 * The Collector view lists every printing in the database (owned or not) with
 * ownership progress on top. Its rows are base {@code MagicCard}s, so the
 * per-copy columns of {@link MagicColumnCollection} (count, location, ownership,
 * comment, special, condition, for-trade, sideboard, ...) either always read the
 * same value or aggregate to blank and are never editable here - they are left
 * out. What is kept: the card-database facts, the collection progress columns,
 * the own-count family, and User Price (the user's own valuation, summed over
 * the copies owned - a meaningful "what is my collection worth" total).
 */
public class CollectorColumnCollection extends MagicColumnCollection {
	public CollectorColumnCollection() {
		super(CollectorViewPreferencePage.class.getName());
	}

	@Override
	protected void createColumns(List<AbstractColumn> columns) {
		columns.add(createGroupColumn());              // "Name" - structural
		columns.add(new ProgressColumn());
		columns.add(new Progress4Column());
		columns.add(new OwnCountColumn());
		columns.add(new OwnUniqueColumn());
		columns.add(new OwnTotalCountColumn());
		columns.add(createSetColumn());                // "Set"
		columns.add(new GenColumn(MagicCardField.RARITY, "Rarity"));
		columns.add(createCollectorsNumberColumn());   // "Collector's Number"
		columns.add(new GenColumn(MagicCardField.ARTIST, "Artist"));
		columns.add(new LanguageColumn());
		columns.add(new ReleaseDateColumn());
		columns.add(createGathererIdColumn());         // "Multiverse ID"
		columns.add(createIdColumn());                 // "Card Id"
		columns.add(new GenColumn(MagicCardField.TCGID, "TCGplayer ID"));
		columns.add(new PriceColumn());                // "User Price" - your valuation, summed over your copies
		columns.add(new CollectorOnlinePriceColumn()); // "Online Price" - market value of the copies you own
		columns.add(new CommunityRatingColumn());      // "Rating"
		columns.add(new CostColumn());
		columns.add(new TypeColumn());
		columns.add(new PowerColumn(MagicCardField.POWER, "P", "Power"));
		columns.add(new PowerColumn(MagicCardField.TOUGHNESS, "T", "Toughness"));
		columns.add(new OracleTextColumn());
		columns.add(new GenColumn(MagicCardField.CTYPE, "Color Type"));
		columns.add(new ColorColumn());
		columns.add(new ColorIdentityColumn());
		columns.add(new LegalityColumn());
		columns.add(new TextColumn());
		if (MagicUIActivator.TRACE_EXPORT) {
			columns.add(new GenColumn(MagicCardField.HASHCODE, "HashCode"));
		}
	}

	@Override
	protected GroupColumn createGroupColumn() {
		return new GroupColumn(false, true, false);
	}
}
