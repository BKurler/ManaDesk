/*
 * Contributors:
 *     Rémi Dutil 2026 - updated for ManaDesk creation and Eclipse 2.0 migration
 */
package com.reflexit.magiccards.ui.views.editions;

import org.eclipse.swt.graphics.Image;

import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.Rarity;
import com.reflexit.magiccards.ui.utils.ImageCreator;

public class EditionNameColumn extends AbstractEditionColumn {
	public EditionNameColumn() {
		super("Name", EditionField.NAME);
	}

	@Override
	public String getText(Object element) {
		Edition ed = (Edition) element;
		return ed.getDisplayName(); // "(coming soon)" while not released
	}

	@Override
	public int getColumnWidth() {
		return 180;
	}

	@Override
	public Image getImage(Object element) {
		Edition ed = (Edition) element;
		MagicCard fake = new MagicCard();
		fake.setSet(ed.getName());
		// Always used Rare for edition name column	
		fake.setRarity(Rarity.RARE);
		fake.setCardId(1);
		return ImageCreator.getInstance().getSetImage(fake);
	}

	@Override
	public void setText(Edition edition, String string) {
		if (string.equals(edition.getName()) || string.equals(edition.getDisplayName()))
			return;
		throw new UnsupportedOperationException("Cannot change edition name (unsupported)");
	}
}