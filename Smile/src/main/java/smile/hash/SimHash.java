package smile.hash;

import com.google.common.base.Charsets;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.util.Set;

import static smile.util.Tokenizer.words;

/**
 * @author Qiyang Zuo
 * @since 15/4/9
 */
public class SimHash {
    private static HashFunction hf = Hashing.murmur3_128();

    public static long simhash64(String text) {
        Set<String> set = words(text);
        return simhash64(set);
    }

    public static long simhash64(Set<String> words) {
        final int BITS = 64;
        if (words == null || words.isEmpty()) {
            return 0;
        }
        int[] bits = new int[BITS];
        for (String s : words) {
            long hc = hf.hashString(s, Charsets.UTF_8).padToLong();
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
