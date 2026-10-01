/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio.kernel;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Variable}.
 *
 * @author Haifeng Li
 */
public class VariableTest {

    @Test
    public void testRecordAccessors() {
        System.out.println("Variable: record accessors");
        var v = new Variable("myVar", "java.lang.String");
        assertEquals("myVar", v.name());
        assertEquals("java.lang.String", v.typeName());
    }

    @Test
    public void testToStringReturnsName() {
        System.out.println("Variable: toString returns name");
        var v = new Variable("counter", "int");
        assertEquals("counter", v.toString(),
                "toString() must return the variable name so JTree can display it");
    }

    @Test
    public void testEqualityByRecord() {
        System.out.println("Variable: record equality");
        var a = new Variable("x", "double");
        var b = new Variable("x", "double");
        assertEquals(a, b, "Two Variable records with same fields must be equal");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testInequalityOnName() {
        System.out.println("Variable: inequality on different name");
        var a = new Variable("x", "double");
        var b = new Variable("y", "double");
        assertNotEquals(a, b);
    }

    @Test
    public void testInequalityOnType() {
        System.out.println("Variable: inequality on different type");
        var a = new Variable("x", "double");
        var b = new Variable("x", "int");
        assertNotEquals(a, b);
    }
}

