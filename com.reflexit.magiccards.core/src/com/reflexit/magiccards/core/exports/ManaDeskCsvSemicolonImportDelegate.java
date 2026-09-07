/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk
 */
package com.reflexit.magiccards.core.exports;

/**
 * Semicolon-separated variant of {@link ManaDeskCsvImportDelegate}.
 */
public class ManaDeskCsvSemicolonImportDelegate extends ManaDeskCsvImportDelegate {
	@Override
	public char getSeparator() {
		return ';';
	}

	@Override
	protected String label() {
		return "ManaDesk CSV Semicolon";
	}
}
