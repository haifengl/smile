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
 * Tests for {@link MurmurHash2}.
 *
 * @author Haifeng Li
 */
public class MurmurHash2Test {

    public MurmurHash2Test() {
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
    public void testHash32EmptyBuffer() {
        System.out.println("MurmurHash2 hash32 empty buffer");
        ByteBuffer buf = ByteBuffer.allocate(0);
        int h = MurmurHash2.hash32(buf, 0, 0, 0);
        // Empty input with seed 0: result is deterministic
        assertEquals(h, MurmurHash2.hash32(buf, 0, 0, 0));
    }

    @Test
    public void testHash32Deterministic() {
        System.out.println("MurmurHash2 hash32 deterministic");
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.wrap(data);
        int h1 = MurmurHash2.hash32(buf, 0, data.length, 42);
        int h2 = MurmurHash2.hash32(buf, 0, data.length, 42);
        assertEquals(h1, h2);
    }

    @Test
    public void testHash32SeedChangesHash() {
        System.out.println("MurmurHash2 hash32 seed changes result");
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.wrap(data);
        int h0 = MurmurHash2.hash32(buf, 0, data.length, 0);
        int h1 = MurmurHash2.hash32(buf, 0, data.length, 1);
        assertNotEquals(h0, h1);
    }

    @Test
    public void testHash32KnownValue() {
        System.out.println("MurmurHash2 hash32 known value");
        // Regression: known hash of "Hello, World!" with seed 0
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.wrap(data);
        int h = MurmurHash2.hash32(buf, 0, data.length, 0);
        assertEquals(h, MurmurHash2.hash32(buf, 0, data.length, 0)); // idempotent
        // Exact value locks in the algorithm output (actual: 765620163 = 0x2DA21183)
        assertEquals(765620163, h,
            "MurmurHash2-32 of 'Hello, World!' with seed 0 changed");
    }

    @Test
    public void testHash32VariousLengths() {
        System.out.println("MurmurHash2 hash32 various lengths (tail handling)");
        // Test tail bytes: 0, 1, 2, 3 remainder bytes
        for (int len = 0; len <= 7; len++) {
            byte[] data = new byte[len];
            for (int i = 0; i < len; i++) data[i] = (byte)(i + 1);
            ByteBuffer buf = ByteBuffer.wrap(data);
            int h = MurmurHash2.hash32(buf, 0, len, 0);
            // Must be deterministic
            assertEquals(h, MurmurHash2.hash32(ByteBuffer.wrap(data), 0, len, 0));
        }
    }

    @Test
    public void testHash64EmptyBuffer() {
        System.out.println("MurmurHash2 hash64 empty buffer");
        ByteBuffer buf = ByteBuffer.allocate(0);
        long h = MurmurHash2.hash64(buf, 0, 0, 0);
        assertEquals(h, MurmurHash2.hash64(buf, 0, 0, 0));
    }

    @Test
    public void testHash64Deterministic() {
        System.out.println("MurmurHash2 hash64 deterministic");
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.wrap(data);
        long h1 = MurmurHash2.hash64(buf, 0, data.length, 0);
        long h2 = MurmurHash2.hash64(buf, 0, data.length, 0);
        assertEquals(h1, h2);
    }

    @Test
    public void testHash64SeedChangesHash() {
        System.out.println("MurmurHash2 hash64 seed changes result");
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.wrap(data);
        long h0 = MurmurHash2.hash64(buf, 0, data.length, 0L);
        long h1 = MurmurHash2.hash64(buf, 0, data.length, 1L);
        assertNotEquals(h0, h1);
    }

    @Test
    public void testHash64KnownValue() {
        System.out.println("MurmurHash2 hash64 known value");
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.wrap(data);
        long h = MurmurHash2.hash64(buf, 0, data.length, 0L);
        assertEquals(h, MurmurHash2.hash64(buf, 0, data.length, 0L));
        // Actual value: 4068140818813804954
        assertEquals(4068140818813804954L, h,
            "MurmurHash2-64 of 'Hello, World!' with seed 0 changed");
    }

    @Test
    public void testHash64TailHandling() {
        System.out.println("MurmurHash2 hash64 tail handling (1-7 remainder bytes)");
        for (int len = 1; len <= 15; len++) {
            byte[] data = new byte[len];
            for (int i = 0; i < len; i++) data[i] = (byte)(i + 1);
            ByteBuffer buf = ByteBuffer.wrap(data);
            long h = MurmurHash2.hash64(buf, 0, len, 0);
            assertEquals(h, MurmurHash2.hash64(ByteBuffer.wrap(data), 0, len, 0),
                "Hash not deterministic for length " + len);
        }
    }

    @Test
    public void testHash64DifferentDataDifferentHash() {
        System.out.println("MurmurHash2 hash64 different data -> different hash");
        byte[] a = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] b = "world".getBytes(StandardCharsets.UTF_8);
        long ha = MurmurHash2.hash64(ByteBuffer.wrap(a), 0, a.length, 0);
        long hb = MurmurHash2.hash64(ByteBuffer.wrap(b), 0, b.length, 0);
        assertNotEquals(ha, hb);
    }
}

