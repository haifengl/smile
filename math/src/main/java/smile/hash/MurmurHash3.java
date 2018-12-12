/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package smile.hash;

import java.nio.ByteBuffer;

/**
 * MurmurHash is a very fast, non-cryptographic hash suitable for general hash-based
 * lookup. The name comes from two basic operations, multiply (MU) and rotate (R),
 * used in its inner loop. See http://murmurhash.googlepages.com/ for more details.
 *
 * The current version is MurmurHash3, which yields a 32-bit or 128-bit hash value.
 * When using 128-bits, the x86 and x64 versions do not produce the same values,
 * as the algorithms are optimized for their respective platforms.
 *
 * This class is adapted from Apache Cassandra.
 */
public class MurmurHash3 {

    private static long getblock(ByteBuffer key, int offset, int index) {
        int i_8 = index << 3;
        int blockOffset = offset + i_8;
        return ((long) key.get(blockOffset + 0) & 0xff)
                + (((long) key.get(blockOffset + 1) & 0xff) << 8)
                + (((long) key.get(blockOffset + 2) & 0xff) << 16)
                + (((long) key.get(blockOffset + 3) & 0xff) << 24)
                + (((long) key.get(blockOffset + 4) & 0xff) << 32)
                + (((long) key.get(blockOffset + 5) & 0xff) << 40)
                + (((long) key.get(blockOffset + 6) & 0xff) << 48)
                + (((long) key.get(blockOffset + 7) & 0xff) << 56);
    }

    private static long rotl64(long v, int n) {
        return ((v << n) | (v >>> (64 - n)));
    }

    private static long fmix(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;

        return k;
    }

    /**
     * 32-bit MurmurHash3.
     */
    public static int hash32(byte[] data, int offset, int len, int seed) {
        int c1 = 0xcc9e2d51;
        int c2 = 0x1b873593;
        int h1 = seed;
        // round down to 4 byte block
        int roundedEnd = offset + (len & 0xfffffffc);
        for (int i = offset; i < roundedEnd; i += 4) {
            // little endian load order
            int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) | ((data[i + 2] & 0xff) << 16)
                    | (data[i + 3] << 24);
            k1 *= c1;
            // ROTL32(k1,15);
            k1 = (k1 << 15) | (k1 >>> 17);
            k1 *= c2;
            h1 ^= k1;
            // ROTL32(h1,13);
            h1 = (h1 << 13) | (h1 >>> 19);
            h1 = h1 * 5 + 0xe6546b64;
        }
        // tail
        int k1 = 0;
        switch (len & 0x03) {
            case 3:
                k1 = (data[roundedEnd + 2] & 0xff) << 16;
                // fallthrough
            case 2:
                k1 |= (data[roundedEnd + 1] & 0xff) << 8;
                // fallthrough
            case 1:
                k1 |= (data[roundedEnd] & 0xff);
                k1 *= c1;
                // ROTL32(k1,15);
                k1 = (k1 << 15) | (k1 >>> 17);
                k1 *= c2;
                h1 ^= k1;
        }
        // finalization
        h1 ^= len;
        // fmix(h1);
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;
        return h1;
    }

    /**
     * 128-bit MurmurHash3 for x64.
     * When using 128-bits, the x86 and x64 versions do not produce
     * the same values, as the algorithms are optimized for their
     * respective platforms.
     */
    public static void hash128(ByteBuffer key, int offset, int length, long seed, long[] result) {
        final int nblocks = length >> 4; // Process as 128-bit blocks.

        long h1 = seed;
        long h2 = seed;

        long c1 = 0x87c37b91114253d5L;
        long c2 = 0x4cf5ad432745937fL;

        // ----------
        // body

        for (int i = 0; i < nblocks; i++) {
            long k1 = getblock(key, offset, i * 2 + 0);
            long k2 = getblock(key, offset, i * 2 + 1);

            k1 *= c1;
            k1 = rotl64(k1, 31);
            k1 *= c2;
            h1 ^= k1;

            h1 = rotl64(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729;

            k2 *= c2;
            k2 = rotl64(k2, 33);
            k2 *= c1;
            h2 ^= k2;

            h2 = rotl64(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5;
        }

        // ----------
        // tail

        // Advance offset to the unprocessed tail of the data.
        offset += nblocks * 16;

        long k1 = 0;
        long k2 = 0;

        switch (length & 15) {
            case 15:
                k2 ^= ((long) key.get(offset + 14)) << 48;
            case 14:
                k2 ^= ((long) key.get(offset + 13)) << 40;
            case 13:
                k2 ^= ((long) key.get(offset + 12)) << 32;
            case 12:
                k2 ^= ((long) key.get(offset + 11)) << 24;
            case 11:
                k2 ^= ((long) key.get(offset + 10)) << 16;
            case 10:
                k2 ^= ((long) key.get(offset + 9)) << 8;
            case 9:
                k2 ^= ((long) key.get(offset + 8)) << 0;
                k2 *= c2;
                k2 = rotl64(k2, 33);
                k2 *= c1;
                h2 ^= k2;

            case 8:
                k1 ^= ((long) key.get(offset + 7)) << 56;
            case 7:
                k1 ^= ((long) key.get(offset + 6)) << 48;
            case 6:
                k1 ^= ((long) key.get(offset + 5)) << 40;
            case 5:
                k1 ^= ((long) key.get(offset + 4)) << 32;
            case 4:
                k1 ^= ((long) key.get(offset + 3)) << 24;
            case 3:
                k1 ^= ((long) key.get(offset + 2)) << 16;
            case 2:
                k1 ^= ((long) key.get(offset + 1)) << 8;
            case 1:
                k1 ^= ((long) key.get(offset));
                k1 *= c1;
                k1 = rotl64(k1, 31);
                k1 *= c2;
                h1 ^= k1;
        }

        // ----------
        // finalization

        h1 ^= length;
        h2 ^= length;

        h1 += h2;
        h2 += h1;

        h1 = fmix(h1);
        h2 = fmix(h2);

        h1 += h2;
        h2 += h1;

        result[0] = h1;
        result[1] = h2;
    }
}