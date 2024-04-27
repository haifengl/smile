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
 * used in its inner loop. See this <a href="http://murmurhash.googlepages.com/">page</a> for more details.
 * <p>
 * The older MurmurHash2 yields a 32-bit or 64-bit value.
 * <p>
 * This class is adapted from Apache Cassandra.
 *
 * @see MurmurHash3
 *
 * @author Haifeng Li
 */
public interface MurmurHash2 {
    /**
     * 32-bit MurmurHash.
     * @param data the data buffer.
     * @param offset the start offset of data in the buffer.
     * @param length the length of data.
     * @param seed the seed of hash code.
     * @return the hash code.
     */
    static int hash32(ByteBuffer data, int offset, int length, int seed) {
        int m = 0x5bd1e995;
        int r = 24;

        int h = seed ^ length;

        int len_4 = length >> 2;

        for (int i = 0; i < len_4; i++) {
            int i_4 = i << 2;
            int k = data.get(offset + i_4 + 3);
            k = k << 8;
            k = k | (data.get(offset + i_4 + 2) & 0xff);
            k = k << 8;
            k = k | (data.get(offset + i_4 + 1) & 0xff);
            k = k << 8;
            k = k | (data.get(offset + i_4) & 0xff);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }

        // avoid calculating modulo
        int len_m = len_4 << 2;
        int left = length - len_m;

        if (left != 0) {
            if (left >= 3) {
                h ^= (int) data.get(offset + length - 3) << 16;
            }
            if (left >= 2) {
                h ^= (int) data.get(offset + length - 2) << 8;
            }
            if (left >= 1) {
                h ^= (int) data.get(offset + length - 1);
            }

            h *= m;
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        return h;
    }

    /**
     * 64-bit MurmurHash.
     * @param data the data buffer.
     * @param offset the start offset of data in the buffer.
     * @param length the length of data.
     * @param seed the seed of hash code.
     * @return the hash code.
     */
    static long hash64(ByteBuffer data, int offset, int length, long seed) {
        long m64 = 0xc6a4a7935bd1e995L;
        int r64 = 47;

        long h64 = (seed & 0xffffffffL) ^ (m64 * length);

        int lenLongs = length >> 3;

        for (int i = 0; i < lenLongs; ++i) {
            int i_8 = i << 3;

            long k64 = ((long) data.get(offset + i_8) & 0xff)
                    + (((long) data.get(offset + i_8 + 1) & 0xff) << 8)
                    + (((long) data.get(offset + i_8 + 2) & 0xff) << 16)
                    + (((long) data.get(offset + i_8 + 3) & 0xff) << 24)
                    + (((long) data.get(offset + i_8 + 4) & 0xff) << 32)
                    + (((long) data.get(offset + i_8 + 5) & 0xff) << 40)
                    + (((long) data.get(offset + i_8 + 6) & 0xff) << 48)
                    + (((long) data.get(offset + i_8 + 7) & 0xff) << 56);

            k64 *= m64;
            k64 ^= k64 >>> r64;
            k64 *= m64;

            h64 ^= k64;
            h64 *= m64;
        }

        int rem = length & 0x7;

        switch (rem) {
            case 0:
                break;
            case 7:
                h64 ^= (long) data.get(offset + length - rem + 6) << 48;
            case 6:
                h64 ^= (long) data.get(offset + length - rem + 5) << 40;
            case 5:
                h64 ^= (long) data.get(offset + length - rem + 4) << 32;
            case 4:
                h64 ^= (long) data.get(offset + length - rem + 3) << 24;
            case 3:
                h64 ^= (long) data.get(offset + length - rem + 2) << 16;
            case 2:
                h64 ^= (long) data.get(offset + length - rem + 1) << 8;
            case 1:
                h64 ^= (long) data.get(offset + length - rem);
                h64 *= m64;
        }

        h64 ^= h64 >>> r64;
        h64 *= m64;
        h64 ^= h64 >>> r64;

        return h64;
    }
}