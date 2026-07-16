/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.tensor;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static smile.tensor.ScalarType.*;

/**
 * Tests for ScalarType enum.
 *
 * @author Haifeng Li
 */
public class ScalarTypeTest {

    @Test
    public void testByteSizes() {
        System.out.println("ScalarType byte sizes");
        assertEquals(1, Int8.byteSize());
        assertEquals(1, QInt8.byteSize());
        assertEquals(1, QUInt8.byteSize());
        assertEquals(2, Int16.byteSize());
        assertEquals(4, Int32.byteSize());
        assertEquals(8, Int64.byteSize());
        assertEquals(2, Float16.byteSize());
        assertEquals(2, BFloat16.byteSize());
        assertEquals(4, Float32.byteSize());
        assertEquals(8, Float64.byteSize());
    }

    @Test
    public void testIsFloating() {
        System.out.println("ScalarType.isFloating()");
        assertTrue(Float16.isFloating());
        assertTrue(BFloat16.isFloating());
        assertTrue(Float32.isFloating());
        assertTrue(Float64.isFloating());
        assertFalse(Int8.isFloating());
        assertFalse(Int16.isFloating());
        assertFalse(Int32.isFloating());
        assertFalse(Int64.isFloating());
        assertFalse(QInt8.isFloating());
        assertFalse(QUInt8.isFloating());
    }

    @Test
    public void testIsInteger() {
        System.out.println("ScalarType.isInteger()");
        assertTrue(Int8.isInteger());
        assertTrue(Int16.isInteger());
        assertTrue(Int32.isInteger());
        assertTrue(Int64.isInteger());
        assertTrue(QInt8.isInteger());
        assertTrue(QUInt8.isInteger());
        assertFalse(Float32.isInteger());
        assertFalse(Float64.isInteger());
        assertFalse(Float16.isInteger());
        assertFalse(BFloat16.isInteger());
    }

    @Test
    public void testIsCompatible() {
        System.out.println("ScalarType.isCompatible()");
        assertTrue(Float64.isCompatible(Float64));
        assertTrue(Float32.isCompatible(Float32));
        assertFalse(Float64.isCompatible(Float32));
        assertFalse(Float32.isCompatible(Float64));
        assertFalse(Int32.isCompatible(Float32));
    }

    @Test
    public void testIsFloatingIsIntegerMutuallyExclusive() {
        System.out.println("isFloating/isInteger mutually exclusive");
        for (ScalarType st : ScalarType.values()) {
            assertFalse(st.isFloating() && st.isInteger(),
                    st + " should not be both floating and integer");
        }
    }
}

