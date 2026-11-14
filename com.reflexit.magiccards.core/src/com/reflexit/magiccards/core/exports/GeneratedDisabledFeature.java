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
package com.reflexit.magiccards.core.exports;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an importer/exporter that is switched off (commented out of
 * {@code plugin.xml}, nothing live uses it) but kept in the code for later.
 * <p>
 * It exists only to keep such code out of the code coverage statistics: JaCoCo (and so
 * EclEmma) ignores every class annotated with an annotation whose name contains
 * "Generated" and whose retention is CLASS or RUNTIME. That is why the name says
 * "Generated" although nothing here is generated. It has no effect at run time.
 * <p>
 * <b>When a format is re-enabled</b> (put back into {@code plugin.xml}), remove this
 * annotation from its classes and write its tests.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface GeneratedDisabledFeature {
	/** Why the class is disabled, free text. */
	String value() default "";
}
