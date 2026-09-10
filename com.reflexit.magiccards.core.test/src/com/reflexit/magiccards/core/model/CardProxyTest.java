/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *******************************************************************************/
package com.reflexit.magiccards.core.model;

import com.reflexit.magiccards.core.model.expr.Expr;
import com.reflexit.unittesting.CardGenerator;

import junit.framework.TestCase;

/**
 * The per-copy {@link MagicCardField#PROXY} flag: storage, the "never for trade"
 * rule, that copying a copy carries the flag, and the {@link Proxies} filter
 * property.
 */
public class CardProxyTest extends TestCase {

	public void testFieldMetadata() {
		assertTrue(MagicCardField.PROXY.isPhysical());
		assertFalse(MagicCardField.PROXY.isTransient());
		assertEquals("proxy", MagicCardField.PROXY.getTag());
	}

	public void testGetSetDefaultsToGenuine() {
		MagicCardPhysical phi = CardGenerator.generatePhysicalCardWithValues();
		assertFalse(phi.isProxy());
		phi.setProxy(true);
		assertTrue(phi.isProxy());
		assertEquals(Boolean.TRUE, phi.get(MagicCardField.PROXY));
		phi.setProxy(false);
		assertFalse(phi.isProxy());
		// cleared, not stored as false
		assertNull(phi.getProperties() == null ? null : phi.getProperties().get(MagicCardField.PROXY));
	}

	public void testSetViaFieldAcceptsStringAndBoolean() {
		MagicCardPhysical phi = CardGenerator.generatePhysicalCardWithValues();
		phi.set(MagicCardField.PROXY, "true");
		assertTrue(phi.isProxy());
		phi.set(MagicCardField.PROXY, Boolean.FALSE);
		assertFalse(phi.isProxy());
	}

	public void testProxyIsNeverForTrade() {
		MagicCardPhysical phi = CardGenerator.generatePhysicalCardWithValues();
		phi.setCount(4);
		phi.set(MagicCardField.FORTRADECOUNT, 2);
		assertEquals(2, phi.getForTrade());
		phi.setProxy(true);
		assertEquals(0, phi.getForTrade());
	}

	public void testCopyCarriesTheFlag() {
		MagicCardPhysical src = CardGenerator.generatePhysicalCardWithValues();
		src.setProxy(true);
		MagicCardPhysical copy = new MagicCardPhysical(src, src.getLocation());
		assertTrue("copying a copy keeps the proxy flag", copy.isProxy());
		assertTrue(src.matching(copy));
	}

	public void testMatchingDistinguishesProxyFromGenuine() {
		MagicCardPhysical a = CardGenerator.generatePhysicalCardWithValues();
		MagicCardPhysical b = new MagicCardPhysical(a, a.getLocation());
		assertTrue(a.matching(b));
		b.setProxy(true);
		assertFalse("a genuine and a proxy copy are not the same pile", a.matching(b));
	}

	public void testProxiesSearchableProperty() {
		Proxies p = Proxies.getInstance();
		assertEquals(FilterField.PROXY, p.getFilterField());
		assertEquals(2, p.getIds().size());
		boolean genuine = false, proxy = false;
		for (String id : p.getIds()) {
			if (Proxies.GENUINE.equals(p.getNameById(id)))
				genuine = true;
			else if (Proxies.PROXY.equals(p.getNameById(id)))
				proxy = true;
		}
		assertTrue(genuine);
		assertTrue(proxy);
	}

	public void testFilterExpressions() {
		Expr proxy = FilterField.PROXY.valueExpr(Proxies.PROXY);
		Expr genuine = FilterField.PROXY.valueExpr(Proxies.GENUINE);
		assertNotNull(proxy);
		assertNotNull(genuine);
		assertFalse("Proxy and Genuine filter differently", proxy.toString().equals(genuine.toString()));
	}
}
