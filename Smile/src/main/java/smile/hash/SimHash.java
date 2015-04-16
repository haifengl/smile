/**
 * ****************************************************************************
 * Copyright (c) 2010 Haifeng Li
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * *****************************************************************************
 */
package smile.hash;


import java.nio.ByteBuffer;
import java.util.List;

/**
 * @author Qiyang Zuo
 * @since 15/4/9
 */
public class SimHash {
    private static final long seed = System.currentTimeMillis();

    public static long simhash64(List<String> tokens) {
        final int BITS = 64;
        if (tokens == null || tokens.isEmpty()) {
            return 0;
        }
        int[] bits = new int[BITS];
        for (String s : tokens) {
            ByteBuffer buffer = ByteBuffer.wrap(s.getBytes());
            long hc = MurmurHash.hash2_64(buffer, 0, buffer.array().length, seed);
            for (int i = 0; i < BITS; i++) {
                if (((hc >>> i) & 1) == 1) {
                    bits[i]++;
                } else {
                    bits[i]--;
                }
            }
        }
        long hash = 0;
        long one = 1;
        for (int i = 0; i < BITS; i++) {
            if (bits[i] >= 0) {
                hash |= one;
            }
            one <<= 1;
        }
        return hash;
    }
}
