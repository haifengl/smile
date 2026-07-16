/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
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

