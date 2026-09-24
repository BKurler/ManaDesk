

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - Proxy column in the deck / collection lists
 *     Rémi Dutil (2026) - removed the Community Rating column (community
 *                         rating is not a concept this app tracks anymore)
 *     Rémi Dutil (2026) - Finish column, right next to Condition
 *     Rémi Dutil (2026) - Extended Color Identity column, next to the
 *                         existing Color Identity one (now Scryfall's own
 *                         authoritative color_identity, the default)
 *     Rémi Dutil (2026) - Error column no longer offered in any of the 8
 *                         regular views (an import-only diagnostic field,
 *                         meaningless once a card is actually in your
 *                         library) - gated behind the new, default-false
 *                         includeErrorColumn() hook instead, which
 *                         DeckImportPreviewPage's own column collection
 *                         overrides back to true, since it's the one place
 *                         this field is actually populated and useful
 *     Rémi Dutil (2026) - Own Count/Own Unique/Own Total dropped from the
 *                         shared base entirely - they're global ownership
 *                         totals, not scoped to the pile a Deck/Collection/
 *                         My Cards/Instances/Scryfall Database row belongs
 *                         to, unlike Collector (which keeps its own copies,
 *                         untouched - it never inherited these from here)
 *     Rémi Dutil (2026) - createColumns(): Count is now added before
 *                         groupColumn instead of after - see the method's
 *                         own comment (a Win32 SWT quirk in
 *                         TableItem#getBounds(0) was corrupting the Name
 *                         column's own icon/text painting once Count was
 *                         configured ahead of it in the display order)
 *     Rémi Dutil (2026) - createColorTypeColumn()/createArtistColumn()/
 *                         createTcgIdColumn() pulled out of createColumns()
 *                         as their own factory methods (matching
 *                         createGathererIdColumn(), now also given its own
 *                         width) so their now-tuned widths are shared by
 *                         every subclass that calls them - including
 *                         CollectorColumnCollection, which used to build
 *                         these three inline with its own separate
 *                         GenColumn(...) calls instead of reusing the base
 *                         class' own columns
 *     Rémi Dutil (2026) - createGathererIdColumn()/createTcgIdColumn():
 *                         getActualText() override added - a Collector
 *                         Name-group spanning several printings was always
 *                         showing the first printing's Multiverse ID/
 *                         TCGplayer ID, because AbstractColumn's own
 *                         "transient group -> show the first child" shortcut
 *                         always wins for these (isTransient() is
 *                         unconditionally true for a Name-group) - it never
 *                         reached the field's own CollisionAggregator, which
 *                         would show "*" once the printings actually
 *                         disagree (the common case for a per-printing id).
 *                         ColorColumn needed the identical fix for the same
 *                         reason. ColorIdentityColumn/
 *                         ExtendedColorIdentityColumn/LegalityColumn were
 *                         already fine - they fully override getText()
 *                         themselves and call get()/getString() directly,
 *                         never going through this shortcut at all.
 */

package com.reflexit.magiccards.ui.views.columns;

import java.util.List;

import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.ui.MagicUIActivator;

public class MagicColumnCollection extends ColumnCollection {
	private String id;
	private GroupColumn groupColumn;
	private SetColumn setColumn;
	private CountColumn countColumn;
	private StringEditorColumn specialColumn;
	private CommentColumn commentColumn;
	private OwnershipColumn ownershipColumn;
	private IdColumn idColumn;

	public MagicColumnCollection(String prefPageId) {
		this.id = prefPageId;
	}

	@Override
	protected void createColumns(List<AbstractColumn> columns) {
		boolean myCards = true;
		// Count is added here, before groupColumn, so that - in the common case
		// where a view's default order also puts Count before Name - GroupColumn
		// never actually needs to be moved away from its natural creation-index
		// slot at all: Table/TreeItem#getBounds(0) has a well-known Win32 SWT
		// quirk where column-creation-index 0's bounds don't reliably follow
		// Table#setColumnOrder() once something else is displayed ahead of it,
		// which left GroupColumn's custom-painted icon+text drawing at the
		// table's literal left edge (x=4) instead of its actual on-screen cell -
		// overlapping whatever now sits to its left. Keeping creation order
		// aligned with the default display order sidesteps that entirely,
		// instead of trying to work around getBounds() being unreliable.
		if (myCards) {
			countColumn = createCountColumn();
			columns.add(countColumn);
		}
		groupColumn = createGroupColumn();
		columns.add(groupColumn);
		idColumn = createIdColumn();
		columns.add(idColumn);
		columns.add(createGathererIdColumn());
		columns.add(new CostColumn());
		columns.add(new TypeColumn());
		columns.add(new PowerColumn(MagicCardField.POWER, "P", "Power"));
		columns.add(new PowerColumn(MagicCardField.TOUGHNESS, "T", "Toughness"));
		columns.add(new OracleTextColumn());
		setColumn = createSetColumn();
		columns.add(setColumn);
		columns.add(new GenColumn(MagicCardField.RARITY, "Rarity"));
		columns.add(createColorTypeColumn());
		if (myCards) {
			columns.add(new LocationColumn());
			ownershipColumn = createOwnershipColumn();
			columns.add(ownershipColumn);
			columns.add(createConditionColumn());
			columns.add(createFinishColumn());
			columns.add(createProxyColumn());
			commentColumn = createCommentColumn();
			columns.add(commentColumn);
			columns.add(new PriceColumn());
		}
		columns.add(new ColorColumn());
		columns.add(new ColorIdentityColumn());
		columns.add(new ExtendedColorIdentityColumn());
		columns.add(new SellerPriceColumn());
		columns.add(createArtistColumn());
		columns.add(createCollectorsNumberColumn());
		if (myCards) {

			specialColumn = createSpecialColumn();
			columns.add(specialColumn);
			columns.add(new ForTradeCountColumn());
		}
		columns.add(new LanguageColumn());
		columns.add(new TextColumn());
		columns.add(new LegalityColumn());
		if (myCards) {
			columns.add(new GenColumn(MagicCardField.SIDEBOARD, "Sideboard"));
			columns.add(new GenColumn(MagicCardField.EXTRA, "Extra"));
			if (includeErrorColumn())
				columns.add(createErrorColumn());
			columns.add(new CreationDateColumn());
		}
		columns.add(new ReleaseDateColumn());
		if (MagicUIActivator.TRACE_EXPORT) {
			columns.add(new GenColumn(MagicCardField.HASHCODE, "HashCode"));
		}
		columns.add(createTcgIdColumn());
	}

	protected SetColumn createSetColumn() {
		return new SetColumn();
	}

	protected GenColumn createCollectorsNumberColumn() {
		return new CollectorsNumberColumn();
	}

	protected GenColumn createGathererIdColumn() {
		return new GenColumn(MagicCardField.GATHERERID, "Multiverse ID") {
			@Override
			public int getColumnWidth() {
				return 85; // -15px from AbstractColumn's inherited 100
			}

			@Override
			protected String getActualText(Object element) {
				return collisionAwareText(this, element);
			}
		};
	}

	protected GenColumn createTcgIdColumn() {
		return new GenColumn(MagicCardField.TCGID, "TCGplayer ID") {
			@Override
			public int getColumnWidth() {
				return 85; // -15px from AbstractColumn's inherited 100
			}

			@Override
			protected String getActualText(Object element) {
				return collisionAwareText(this, element);
			}
		};
	}

	/** Bypasses AbstractColumn#getActualText()'s "transient group -> just show
	 *  the first child" shortcut, which always wins for a Collector Name-group
	 *  (isTransient() is unconditionally true for those) and made a group
	 *  spanning several printings of the same name silently show only the
	 *  first one's id, instead of running the field's own CollisionAggregator.
	 *  That aggregator's own collision sentinel for these two fields is the
	 *  int 0 (kept as-is - it's also what a single card with no known id
	 *  reports, and other code may rely on get() always returning an
	 *  Integer here) - shown as "*" instead of a bare, confusing "0" once
	 *  it's actually representing a multi-printing group, not one real
	 *  card's own id. Same fix FinishColumn/ProxyColumn already needed for
	 *  their own fields. */
	private static String collisionAwareText(AbstractColumn column, Object element) {
		if (element instanceof ICard) {
			Object value = ((ICard) element).get(column.getDataField());
			if (element instanceof ICardGroup && Integer.valueOf(0).equals(value))
				return "*";
			return value == null ? "" : value.toString();
		}
		return "";
	}

	protected GenColumn createColorTypeColumn() {
		return new GenColumn(MagicCardField.CTYPE, "Color Type") {
			@Override
			public int getColumnWidth() {
				return 90; // -10px from AbstractColumn's inherited 100
			}
		};
	}

	protected GenColumn createArtistColumn() {
		return new GenColumn(MagicCardField.ARTIST, "Artist") {
			@Override
			public int getColumnWidth() {
				return 115; // +15px from AbstractColumn's inherited 100
			}
		};
	}

	protected GenColumn createErrorColumn() {
		return new GenColumn(MagicCardField.ERROR, "Error");
	}

	/** Off by default - the Error column is only ever populated during import
	 *  (an unresolved name/set/language while matching a pasted card list
	 *  against the database) and is meaningless once a card is actually in
	 *  your library, so none of the 8 regular views offer it. Overridden to
	 *  {@code true} by DeckImportPreviewPage's own column collection. */
	protected boolean includeErrorColumn() {
		return false;
	}

	protected CountColumn createCountColumn() {
		return new CountColumn();
	}

	protected StringEditorColumn createSpecialColumn() {
		return new StringEditorColumn(MagicCardField.SPECIAL, "Special");
	}

	protected CommentColumn createCommentColumn() {
		return new CommentColumn();
	}

	protected IdColumn createIdColumn() {
		return new IdColumn();
	}

	protected OwnershipColumn createOwnershipColumn() {
		return new OwnershipColumn();
	}

	protected AbstractColumn createConditionColumn() {
		return new ConditionColumn();
	}

	protected AbstractColumn createFinishColumn() {
		return new FinishColumn();
	}

	protected AbstractColumn createProxyColumn() {
		return new ProxyColumn();
	}

	protected GroupColumn createGroupColumn() {
		return new GroupColumn();
	}

	public GroupColumn getGroupColumn() {
		if (groupColumn == null)
			groupColumn = createGroupColumn();
		return groupColumn;
	}

	public SetColumn getSetColumn() {
		if (setColumn == null)
			setColumn = createSetColumn();
		return setColumn;
	}

	public CountColumn getCountColumn() {
		if (countColumn == null)
			countColumn = createCountColumn();
		return countColumn;
	}

	public StringEditorColumn getSpecialColumn() {
		if (specialColumn == null)
			specialColumn = createSpecialColumn();
		return specialColumn;
	}

	public CommentColumn getCommentColumn() {
		if (commentColumn == null)
			commentColumn = createCommentColumn();
		return commentColumn;
	}

	public IdColumn getIdColumn() {
		if (idColumn == null)
			idColumn = createIdColumn();
		return idColumn;
	}

	public OwnershipColumn getOwnershipColumn() {
		if (ownershipColumn == null)
			ownershipColumn = createOwnershipColumn();
		return ownershipColumn;
	}

	@Override
	public String getId() {
		return id;
	}
}
