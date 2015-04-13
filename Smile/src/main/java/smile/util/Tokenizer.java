package smile.util;

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * @author Qiyang Zuo
 * @since 15/4/9
 */
public class Tokenizer {
    public static Set<String> words(String text) {
        Set<String> set = Sets.newHashSet();
        set.addAll(Splitter.on(" ").omitEmptyStrings().splitToList(text));
        return set;
    }
}
