/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.nlp.tokenizer;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * A word tokenizer that tokenizes English sentences with some differences from
 * TreebankWordTokenizer, notably on handling not-contractions. If a period
 * serves as both the end of sentence and a part of abbreviation, e.g. etc. at
 * the end of sentence, it will generate tokens of "etc." and "." while
 * TreebankWordTokenizer will generate "etc" and ".".
 * <p>
 * Most punctuation is split from adjoining words. Verb contractions and the
 * Anglo-Saxon genitive of nouns are split into their component morphemes,
 * and each morpheme is tagged separately. Examples
 * <ul>
 * <li> children's -&gt; children 's
 * <li> parents' -&gt; parents '
 * <li> won't -&gt; will not
 * <li> can't -&gt; can not
 * <li> shan't -&gt; shall not
 * <li> cannot -&gt; can not
 * <li> weren't -&gt; were not
 * <li> 'tisn't -&gt; it is not
 * <li> 'tis -&gt; it is
 * <li> gonna -&gt; gon na
 * <li> I'm -&gt; I 'm
 * <li> he'll -&gt; he 'll
 * </ul>
 * This tokenizer assumes that the text has already been segmented into
 * sentences. Any periods -- apart from those at the end of a string or before
 * newline -- are assumed to be part of the word they are attached to (e.g. for
 * abbreviations, etc), and are not separately tokenized.
 *
 * @author Haifeng Li
 */
public class SimpleTokenizer implements Tokenizer {
    private static final Pattern WONT_CONTRACTION = Pattern.compile("(?i)\\b(w)(on't)\\b");

    private static final Pattern SHANT_CONTRACTION = Pattern.compile("(?i)\\b(sha)(n't)\\b");

    private static final Pattern AINT_CONTRACTION = Pattern.compile("(?i)\\b(a)(in't)\\b");

    private static final Pattern[] NOT_CONTRACTIONS = {
        Pattern.compile("(?i)\\b(can)('t|not)\\b"),
        Pattern.compile("(?i)(.)(n't)\\b")
    };

    /**
     * List of contractions adapted from Robert MacIntyre's tokenizer.
     */
    private static final Pattern[] CONTRACTIONS2 = {
        Pattern.compile("(?i)(.)('ll|'re|'ve|'s|'m|'d)\\b"),
        Pattern.compile("(?i)\\b(D)('ye)\\b"),
        Pattern.compile("(?i)\\b(Gim)(me)\\b"),
        Pattern.compile("(?i)\\b(Gon)(na)\\b"),
        Pattern.compile("(?i)\\b(Got)(ta)\\b"),
        Pattern.compile("(?i)\\b(Lem)(me)\\b"),
        Pattern.compile("(?i)\\b(Mor)('n)\\b"),
        Pattern.compile("(?i)\\b(T)(is)\\b"),
        Pattern.compile("(?i)\\b(T)(was)\\b"),
        Pattern.compile("(?i)\\b(Wan)(na)\\b")
    };

    private static final Pattern[] CONTRACTIONS3 = {
        Pattern.compile("(?i)\\b(Whad)(dd)(ya)\\b"),
        Pattern.compile("(?i)\\b(Wha)(t)(cha)\\b")
    };

    private static final Pattern[] DELIMITERS = {
        // Separate most punctuation
        Pattern.compile("((?U)[^\\w\\.\\'\\-\\/,&])"),
        // Separate commas if they're followed by space (e.g., don't separate 2,500)
        Pattern.compile("(?U)(,\\s)"),
        // Separate single quotes if they're followed by a space.
        Pattern.compile("(?U)('\\s)"),
        // Separate periods that come before newline or end of string.
        Pattern.compile("(?U)\\. *(\\n|$)"),
        // Separate continuous periods such as ... in ToC.
        Pattern.compile("(?U)(\\.{3,})")

    };

    private static final Pattern WHITESPACE = Pattern.compile("(?U)\\s+");

    private boolean splitContraction;
    /**
     * Constructor.
     */
    public SimpleTokenizer() {
        this(false);
    }

    /**
     * Constructor.
     * @param splitContraction if true, split adjoining words.
     */
    public SimpleTokenizer(boolean splitContraction) {
        this.splitContraction = splitContraction;
    }

    @Override
    public String[] split(String text) {
        if (splitContraction) {
            text = WONT_CONTRACTION.matcher(text).replaceAll("$1ill not");
            text = SHANT_CONTRACTION.matcher(text).replaceAll("$1ll not");
            text = AINT_CONTRACTION.matcher(text).replaceAll("$1m not");

            for (Pattern regexp : NOT_CONTRACTIONS) {
                text = regexp.matcher(text).replaceAll("$1 not");
            }

            for (Pattern regexp : CONTRACTIONS2) {
                text = regexp.matcher(text).replaceAll("$1 $2");
            }

            for (Pattern regexp : CONTRACTIONS3) {
                text = regexp.matcher(text).replaceAll("$1 $2 $3");
            }
        }

        text = DELIMITERS[0].matcher(text).replaceAll(" $1 ");
        text = DELIMITERS[1].matcher(text).replaceAll(" $1");
        text = DELIMITERS[2].matcher(text).replaceAll(" $1");
        text = DELIMITERS[3].matcher(text).replaceAll(" . ");
        text = DELIMITERS[4].matcher(text).replaceAll(" $1 ");

        String[] words = WHITESPACE.split(text);
        if (words.length > 1 && words[words.length-1].equals(".")) {
            if (EnglishAbbreviations.contains(words[words.length-2])) {
                words[words.length-2] = words[words.length-2] + ".";
            }
        }
        
        ArrayList<String> result = new ArrayList<>();
        for (String token : words) {
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        
        return result.toArray(new String[result.size()]);
    }
}
