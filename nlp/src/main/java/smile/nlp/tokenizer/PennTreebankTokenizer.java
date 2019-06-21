/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.nlp.tokenizer;

import java.util.regex.Pattern;

/**
 * A word tokenizer that tokenizes English sentences using the conventions
 * used by the Penn Treebank. Most punctuation is split from adjoining words.
 * Verb contractions and the Anglo-Saxon genitive of nouns are split into their
 * component morphemes, and each morpheme is tagged separately. Examples
 * <ul>
 * <li> children's -&gt; children 's
 * <li> parents' -&gt; parents '
 * <li> won't --&gt; wo n't
 * <li> can't -&gt; ca n't
 * <li> weren't -&gt; were n't
 * <li> cannot -&gt; can not
 * <li> 'tisn't -&gt; 't is n't
 * <li> 'tis -&gt; 't is
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
public class PennTreebankTokenizer implements Tokenizer {
    /**
     * List of contractions adapted from Robert MacIntyre's tokenizer.
     */
    private static final Pattern[] CONTRACTIONS2 = {
        Pattern.compile("(?i)(.)('ll|'re|'ve|n't|'s|'m|'d)\\b"),
        Pattern.compile("(?i)\\b(can)(not)\\b"),
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
        Pattern.compile("(?U)([^\\w\\.\\'\\-\\/,&])"),
        // Separate commas if they're followed by space (e.g., don't separate 2,500)
        Pattern.compile("(?U)(,\\s)"),
        // Separate single quotes if they're followed by a space.
        Pattern.compile("(?U)('\\s)"),
        // Separate periods that come before newline or end of string.
        Pattern.compile("(?U)\\. *(\\n|$)")
    };

    private static final Pattern WHITESPACE = Pattern.compile("(?U)\\s+");

    /**
     * The singleton instance.
     */
    private static PennTreebankTokenizer singleton = new PennTreebankTokenizer();

    /**
     * Constructor.
     */
    private PennTreebankTokenizer() {

    }

    /**
     * Returns the singleton instance.
     */
    public static PennTreebankTokenizer getInstance() {
        return singleton;
    }

    @Override
    public String[] split(String text) {
        for (Pattern regexp : CONTRACTIONS2)
            text = regexp.matcher(text).replaceAll("$1 $2");

        for (Pattern regexp : CONTRACTIONS3)
            text = regexp.matcher(text).replaceAll("$1 $2 $3");

        text = DELIMITERS[0].matcher(text).replaceAll(" $1 ");
        text = DELIMITERS[1].matcher(text).replaceAll(" $1");
        text = DELIMITERS[2].matcher(text).replaceAll(" $1");
        text = DELIMITERS[3].matcher(text).replaceAll(" . ");

        String[] words = WHITESPACE.split(text);
        if (words.length > 1 && words[words.length-1].equals(".")) {
            if (EnglishAbbreviations.contains(words[words.length-2])) {
                words[words.length-2] = words[words.length-2] + ".";
            }
        }

        return words;
    }
}
