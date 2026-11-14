/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *     Rémi Dutil (2026) - marked @GeneratedDisabledFeature (disabled format, excluded from code coverage)
 *******************************************************************************/
package com.reflexit.magiccards.core.exports;

/**
 * Import for CVS deck format
 */
@GeneratedDisabledFeature("Commented out of plugin.xml - kept for later")
public class ScsvImportDelegate extends CsvImportDelegate {
	@Override
	public char getSeparator() {
		return ';';
	}
}
