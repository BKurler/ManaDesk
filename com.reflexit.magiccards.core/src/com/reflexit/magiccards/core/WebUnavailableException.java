/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - replaces OfflineException: thrown when the web (or
 *                         one host) cannot be reached - no connection, DNS
 *                         failure, refused / timed-out connection - or when
 *                         the debug-only "Simulate web not working" switch is
 *                         on; callers degrade quietly instead of reporting an
 *                         error.
 *******************************************************************************/
package com.reflexit.magiccards.core;

import java.io.IOException;

/**
 * The web (or one host on it) cannot be reached: no connection, DNS failure,
 * connection refused / timed out - or the debug-only "Simulate web not
 * working" switch is on. Callers catch it to degrade quietly (cached data,
 * "image not available", a friendly message) instead of reporting an error.
 */
@SuppressWarnings("serial")
public class WebUnavailableException extends IOException {
	public WebUnavailableException(String host) {
		super("The web is not accessible" + (host == null || host.isEmpty() ? "" : " (" + host + ")"));
	}

	public WebUnavailableException(String host, Throwable cause) {
		this(host);
		initCause(cause);
	}
}
