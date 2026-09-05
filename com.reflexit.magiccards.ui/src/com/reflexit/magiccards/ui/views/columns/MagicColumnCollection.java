

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.ui.views.columns;

import java.util.List;

import com.reflexit.magiccards.core.model.MagicCardField;
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
		groupColumn = createGroupColumn();
		columns.add(groupColumn);
		idColumn = createIdColumn();
		columns.add(idColumn);
		columns.add(new GenColumn(MagicCardField.GATHERERID, "Multiverse ID"));
		columns.add(new CostColumn());
		columns.add(new TypeColumn());
		columns.add(new PowerColumn(MagicCardField.POWER, "P", "Power"));
		columns.add(new PowerColumn(MagicCardField.TOUGHNESS, "T", "Toughness"));
		columns.add(new OracleTextColumn());
		setColumn = createSetColumn();
		columns.add(setColumn);
		columns.add(new GenColumn(MagicCardField.RARITY, "Rarity"));
		columns.add(new GenColumn(MagicCardField.CTYPE, "Color Type"));
		if (myCards) {
			countColumn = createCountColumn();
			columns.add(countColumn);
			columns.add(new LocationColumn());
			ownershipColumn = createOwnershipColumn();
			columns.add(ownershipColumn);
			commentColumn = createCommentColumn();
			columns.add(commentColumn);
			columns.add(new PriceColumn());
		}
		columns.add(new ColorColumn());
		columns.add(new ColorIdentityColumn());
		columns.add(new SellerPriceColumn());
		columns.add(new CommunityRatingColumn());
		columns.add(new GenColumn(MagicCardField.ARTIST, "Artist"));
		columns.add(new CollectorsNumberColumn());
		if (myCards) {

			specialColumn = createSpecialColumn();
			columns.add(specialColumn);
			columns.add(new ForTradeCountColumn());
		}
		columns.add(new LanguageColumn());
		columns.add(new TextColumn());
		columns.add(new OwnCountColumn());
		columns.add(new OwnUniqueColumn());
		columns.add(new OwnTotalCountColumn());
		columns.add(new LegalityColumn());
		if (myCards) {
			columns.add(new GenColumn(MagicCardField.SIDEBOARD, "Sideboard"));
			columns.add(new GenColumn(MagicCardField.EXTRA, "Extra"));
			columns.add(new GenColumn(MagicCardField.ERROR, "Error"));
			columns.add(new CreationDateColumn());
		}
		columns.add(new ReleaseDateColumn());
		if (MagicUIActivator.TRACE_EXPORT) {
			columns.add(new GenColumn(MagicCardField.HASHCODE, "HashCode"));
		}
		columns.add(new GenColumn(MagicCardField.TCGID, "TCGplayer ID"));
	}

	protected SetColumn createSetColumn() {
		return new SetColumn();
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
