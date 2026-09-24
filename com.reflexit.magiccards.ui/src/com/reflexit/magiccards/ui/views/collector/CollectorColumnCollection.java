/*
 * Contributors:
 *     Rémi Dutil (2026) - curated to the columns that make sense for whole-database
 *     collection tracking (drop the per-copy deck/collection columns)
 *     Rémi Dutil (2026) - proxy-aware User / Online Price columns
 *     Rémi Dutil (2026) - removed the Community Rating column (community
 *                         rating is not a concept this app tracks anymore)
 *     Rémi Dutil (2026) - Extended Color Identity column, next to the
 *                         existing Color Identity one (now Scryfall's own
 *                         authoritative color_identity, the default)
 *     Rémi Dutil (2026) - Finish column - a printing can offer more than one
 *                         (nonfoil/foil/etched), which the completion math
 *                         doesn't distinguish between (see class-level note)
 *                         but is still worth being able to see and filter by
 *     Rémi Dutil (2026) - createFinishColumn(): +30px here only (+15, then
 *                         +15 more) - a row here is a printing, not a single
 *                         owned copy, so the cell shows every finish the
 *                         printing supports joined together ("Nonfoil, Foil,
 *                         Etched"), which needs more room than a
 *                         single-copy view's one-word value
 *     Rémi Dutil (2026) - Artist/Color Type/TCGplayer ID now built via the
 *                         base class' own createArtistColumn()/
 *                         createColorTypeColumn()/createTcgIdColumn()
 *                         instead of a separate inline GenColumn(...) each -
 *                         so their tuned widths apply here too, not just in
 *                         the other 6 regular views
 */
package com.reflexit.magiccards.ui.views.collector;

import java.util.List;

import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.preferences.CollectorViewPreferencePage;
import com.reflexit.magiccards.ui.views.columns.AbstractColumn;
import com.reflexit.magiccards.ui.views.columns.ColorColumn;
import com.reflexit.magiccards.ui.views.columns.ColorIdentityColumn;
import com.reflexit.magiccards.ui.views.columns.CostColumn;
import com.reflexit.magiccards.ui.views.columns.ExtendedColorIdentityColumn;
import com.reflexit.magiccards.ui.views.columns.FinishColumn;
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
		columns.add(createFinishColumn());             // "Finish" - which finishes this printing supports
		columns.add(createCollectorsNumberColumn());   // "Collector's Number"
		columns.add(createArtistColumn());
		columns.add(new LanguageColumn());
		columns.add(new ReleaseDateColumn());
		columns.add(createGathererIdColumn());         // "Multiverse ID"
		columns.add(createIdColumn());                 // "Card Id"
		columns.add(createTcgIdColumn());
		columns.add(new CollectorUserPriceColumn());   // "User Price" - your valuation of the copies you own
		columns.add(new CollectorOnlinePriceColumn()); // "Online Price" - market value of the copies you own
		columns.add(new CostColumn());
		columns.add(new TypeColumn());
		columns.add(new PowerColumn(MagicCardField.POWER, "P", "Power"));
		columns.add(new PowerColumn(MagicCardField.TOUGHNESS, "T", "Toughness"));
		columns.add(new OracleTextColumn());
		columns.add(createColorTypeColumn());
		columns.add(new ColorColumn());
		columns.add(new ColorIdentityColumn());
		columns.add(new ExtendedColorIdentityColumn());
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

	@Override
	protected AbstractColumn createFinishColumn() {
		return new FinishColumn() {
			@Override
			public int getColumnWidth() {
				return super.getColumnWidth() + 30;
			}
		};
	}
}
