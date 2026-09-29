/*
 * Contributors:
 *     Rémi Dutil (2026) - added BROWSER: text captured from the New Deck/
 *                         Collection wizard's "Browse Website..." dialog (an
 *                         embedded SWT Browser the user navigates, and logs
 *                         into, themselves) - distinct from URL, which is a
 *                         server-side raw HTTP GET (WebUtils.openUrlText)
 *                         that cannot render JS or survive a login wall.
 */
package com.reflexit.magiccards.core.exports;

public enum ImportSource {
	FILE, TEXT, URL, BROWSER
}