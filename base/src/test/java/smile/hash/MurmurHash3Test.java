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
package smile.hash;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MurmurHash3}.
 *
 * @author Haifeng Li
 */
public class MurmurHash3Test {

    public MurmurHash3Test() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testHash32EmptyString() {
        System.out.println("MurmurHash3 hash32 empty string");
        int h = MurmurHash3.hash32("", 0);
        assertEquals(h, MurmurHash3.hash32("", 0));
    }

    @Test
    public void testHash32Deterministic() {
        System.out.println("MurmurHash3 hash32 deterministic");
        int h1 = MurmurHash3.hash32("hello world", 0);
        int h2 = MurmurHash3.hash32("hello world", 0);
        assertEquals(h1, h2);
    }

    @Test
    public void testHash32SeedChangesHash() {
        System.out.println("MurmurHash3 hash32 seed changes result");
        assertNotEquals(
            MurmurHash3.hash32("test", 0),
            MurmurHash3.hash32("test", 1)
        );
    }

    @Test
    public void testHash32KnownValue() {
        System.out.println("MurmurHash3 hash32 known value");
        // Regression against reference C implementation output
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        int h = MurmurHash3.hash32(data, 0, data.length, 0);
        // Actual value: 592631239 = 0x2353F587
        assertEquals(592631239, h,
            "MurmurHash3-32 of 'Hello, World!' with seed 0 changed");
    }

    @Test
    public void testHash32VariousLengths() {
        System.out.println("MurmurHash3 hash32 various lengths");
        for (int len = 0; len <= 7; len++) {
            byte[] data = new byte[len];
            for (int i = 0; i < len; i++) data[i] = (byte)(i + 1);
            int h = MurmurHash3.hash32(data, 0, len, 0);
            assertEquals(h, MurmurHash3.hash32(data, 0, len, 0));
        }
    }

    @Test
    public void testHash32DifferentDataDifferentHash() {
        System.out.println("MurmurHash3 hash32 different data -> different hash");
        assertNotEquals(
            MurmurHash3.hash32("hello", 0),
            MurmurHash3.hash32("world", 0)
        );
    }

    @Test
    public void testHash128Deterministic() {
        System.out.println("MurmurHash3 hash128 deterministic");
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.wrap(data);
        long[] r1 = new long[2];
        long[] r2 = new long[2];
        MurmurHash3.hash128(buf, 0, data.length, 0L, r1);
        buf.rewind();
        MurmurHash3.hash128(buf, 0, data.length, 0L, r2);
        assertArrayEquals(r1, r2);
    }

    @Test
    public void testHash128SeedChangesHash() {
        System.out.println("MurmurHash3 hash128 seed changes result");
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        long[] r0 = new long[2];
        long[] r1 = new long[2];
        MurmurHash3.hash128(ByteBuffer.wrap(data), 0, data.length, 0L, r0);
        MurmurHash3.hash128(ByteBuffer.wrap(data), 0, data.length, 1L, r1);
        assertFalse(r0[0] == r1[0] && r0[1] == r1[1]);
    }

    @Test
    public void testHash128KnownValue() {
        System.out.println("MurmurHash3 hash128 known value");
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        long[] result = new long[2];
        MurmurHash3.hash128(ByteBuffer.wrap(data), 0, data.length, 0L, result);
        // Regression lock-in of the x64 128-bit output
        // Actual h1: -7801248908042526272, h2: -6178049680326152513
        assertEquals(-7801248908042526272L, result[0],
            "MurmurHash3-128 h1 of 'Hello, World!' with seed 0 changed");
        assertEquals(-6178049680326152513L, result[1],
            "MurmurHash3-128 h2 of 'Hello, World!' with seed 0 changed");
    }

    @Test
    public void testHash128TailHandling() {
        System.out.println("MurmurHash3 hash128 tail handling");
        for (int len = 0; len <= 19; len++) {
            byte[] data = new byte[len];
            for (int i = 0; i < len; i++) data[i] = (byte)(i + 1);
            long[] r1 = new long[2];
            long[] r2 = new long[2];
            MurmurHash3.hash128(ByteBuffer.wrap(data), 0, len, 0L, r1);
            MurmurHash3.hash128(ByteBuffer.wrap(data), 0, len, 0L, r2);
            assertArrayEquals(r1, r2, "Not deterministic at length " + len);
        }
    }

    @Test
    public void testHash128DifferentDataDifferentHash() {
        System.out.println("MurmurHash3 hash128 different data -> different hash");
        byte[] a = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] b = "world".getBytes(StandardCharsets.UTF_8);
        long[] ra = new long[2];
        long[] rb = new long[2];
        MurmurHash3.hash128(ByteBuffer.wrap(a), 0, a.length, 0L, ra);
        MurmurHash3.hash128(ByteBuffer.wrap(b), 0, b.length, 0L, rb);
        assertFalse(ra[0] == rb[0] && ra[1] == rb[1]);
    }
}

