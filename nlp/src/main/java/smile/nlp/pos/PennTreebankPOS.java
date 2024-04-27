/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.nlp.pos;

import java.util.HashMap;
import java.util.Map;

/**
 * The Penn Treebank Tag set.
 *
 * @author Haifeng Li
 */
public enum PennTreebankPOS {

    /**
     * Coordinating conjunction. This category includes and, but, nor, or, yet
     * (as in Yet it's cheap, cheap yet good), as well as the mathematical
     * operators plus, minus, less, times (in the sense of "multiplied by")
     * and over (in the sense of "divided by"), when they are spelled out.
     * FOR in the sense of "because" is a coordinating conjunction (CC) rather
     * than a subordinating conjunction (IN) -
     * <p>
     * He asked to be transferred, for/CC he was unhappy.
     * <p>
     * SO in the sense of "so that," on the other hand, is a subordinating
     * conjunction (IN).
     */
    CC(false),

    /**
     * Cardinal number.
     */
    CD(true),

    /**
     * Determiner. This category includes the articles a(n), every, no and the,
     * the indefinite determiners another, any and some, each, either (as in
     * either way), neither (as in neither decision), that, these, this and
     * those, and instances of all and both when they do not precede a
     * determiner or possessive pronoun (as in all roads or both times).
     * (Instances of all or both that do precede a determiner or possessive
     * pronoun are tagged as predeterminers (PDT).) Since any noun phrase can
     * contain at most one determiner, the fact that such can occur together
     * with a determiner (as in the only such case) means that it should be
     * tagged as an adjective (JJ), unless it precedes a determiner, as in such
     * a good time, in which case it is a predeterminer (PDT).
     */
    DT(false),

    /**
     * Existential there. Existential there is the unstressed there that
     * triggers inversion of the inflected verb and the logical subject of
     * a sentence. Examples:
     * <p>
     * There/EX was a party in progress.
     * <p>
     * There/EX ensued a melee.
     */
    EX(false),

    /**
     * Foreign word.
     */
    FW(true),

    /**
     * Preposition or subordinating conjunction. We make no explicit distinction
     * between prepositions and subordinating conjunctions. (The distinction is
     * not lost, however -- a preposition is an IN that precedes a noun phrase
     * or a prepositional phrase, and a subordinate conjunction is an IN that
     * precedes a clause.) The preposition to has its own special tag TO.
     */
    IN(false),

    /**
     * Adjective. Hypenated compounds that are used as modifiers, like
     * happy-go-lucky, one-of-a-kind and run-of-the-mill, are tagged as JJ.
     * Ordinal numbers are tagged as JJ, as are compounds of the form n-th
     * x-est, like fourth-largest.
     */
    JJ(true),

    /**
     * Adjective, comparative. Adjectives with the comparative ending -er and
     * a comparative meaning. Adjectives with a comparative meaning but without
     * the comparative ending -er, like superior, should simply be tagged as JJ.
     * Adjectives with the ending -er but without a strictly comparative meaning,
     * like further in further details, should also simply be tagged as JJ.
     */
    JJR(true),

    /**
     * Adjective, superlative. Adjectives with the superlative ending -est.
     * Adjectives with a superlative meaning but without the superlative ending
     * -est, like first, last or unsurpassed, should simply be tagged as JJ.
     */
    JJS(true),

    /**
     * List item marker. This category includes letters and numerals when
     * they are used to identify items in a list.
     */
    LS(true),

    /**
     * Modal verb. This category includes all verbs that don't take an -s
     * ending in the third-person singular present: can, could, (dare), may,
     * might, must, ought, shall, should, will, would.
     */
    MD(false),

    /**
     * Noun, singular or mass.
     */
    NN(true),

    /**
     * Noun, plural.
     */
    NNS(true),
    /**
     * Proper noun, singular.
     */
    NNP(true),

    /**
     * Proper noun, plural.
     */
    NNPS(true),

    /**
     * Predeterminer. This category includes the following determinerlike
     * elements when they precede an article or possessive pronoun. Examples:
     * <p>
     * all/PDT his marbles
     * <p>
     * nary/PDT a soul
     * <p>
     * both/PDT the girls
     * <p>
     * quite/PDT a mess
     * <p>
     * half/PDT his time
     * <p>
     * rather/PDT a nuisance
     * <p>
     * many/PDT a moon
     * <p>
     * such/PDT a good time
     */
    PDT(false),

    /**
     * Possessive ending. The possessive ending on nouns ending in 's or ' is
     * split off by the tagging algorithm and tagged as if it were a separate word.
     * Examples:
     * <p>
     * JohnINP 's/POS idea
     * <p>
     * the parents/NNS '/POS distress
     */
    POS(false),

    /**
     * Personal pronoun. This category includes the personal pronouns proper,
     * without regard for case distinctions (I, me, you, he, him, etc.), the
     * reflexive pronouns ending in -selfor -selves, and the nominal possessive
     * pronouns mine, yours, his, hers, ours and theirs. The adjectival
     * possessive forms my, your, his, her, its, our and their, on the other
     * hand, are tagged PP$.
     */
    PRP(false),

    /**
     * Possessive pronoun. This category includes the adjectival possessive
     * forms my, your, his, her, ids, one's, our and their. The nominal
     * possessive pronouns mine, yours, his, hers, ours and theirs are tagged
     * as personal pronouns (PP).
     */
    PRP$(false),

    /**
     * Adverb. This category includes most words that end in -ly as well as
     * degree words like quite, too and very, posthead modifiers like enough
     * and indeed (as in good enough, very well indeeed), and negative markers
     * like not, n't, and never.
     */
    RB(true),

    /**
     * Adverb, comparative. Adverbs with the comparative ending -er but without
     * a strictly comparative meaning, like later in "We can always come by
     * later", should simply be tagged as RB.
     */
    RBR(true),

    /**
     * Adverb, superlative.
     */
    RBS(true),

    /**
     * Particle. This category includes a number of mostly monosyllabic words
     * that also double as directional adverbs and prepositions.
     */
    RP(false),

    /**
     * Symbol. This includes / [ = *, #, etc. This tag should be used for
     * mathematical, scientific and technical symbols or expressions that
     * aren't words of English. It should not be used for any and all technical
     * expressions. For instance, the names of chemicals, units of measurements
     * (including abbreviations thereof) and the like should be tagged as nouns.
     */
    SYM(true),

    /**
     * to.
     */
    TO(false),

    /**
     * Interjection. This category includes my (as in My, what a gorgeous day),
     * oh, please, see (as in See, it's like this), ah, well and yes, among others.
     */
    UH(true),

    /**
     * Verb, base form. This tag subsumes imperatives, infinitives and subjunctives.
     */
    VB(true),

    /**
     * Verb, past tense. This category includes the conditional form of the verb to be.
     * Examples:
     * <p>
     * If I were/VBD rich, ...
     * <p>
     * If I were/VBD to win the lottery, ...
     */
    VBD(true),

    /**
     * Verb, gerund or present participle.
     */
    VBG(true),

    /**
     * Verb, past participle.
     */
    VBN(true),

    /**
     * Verb, non-3rd person singular present.
     */
    VBP(true),

    /**
     * Verb, 3rd person singular present.
     */
    VBZ(true),

    /**
     * Wh-determiner. This category includes which, as well as that when it is
     * used as a relative pronoun.
     */
    WDT(false),

    /**
     * Wh-pronoun. This category includes what, who and whom.
     */
    WP(false),

    /**
     * Possessive wh-pronoun. This category includes the wh-word whose.
     */
    WP$(false),

    /**
     * Wh-adverb. This category includes how, where, why, etc. When in a
     * temporal sense is tagged WRB. In the sense of "if," on the other hand,
     * it is a subordinating conjunction (IN). Examples:
     * <p>
     * When/WRB he finally arrived, I was on my way out.
     * <p>
     * I like it when/IN you make dinner for me.
     */
    WRB(false),

    /**
     * Punctuation $
     */
    $(false),

    /**
     * Sentence-break punctuation . ? !
     */
    SENT(false) {
        @Override
        public String toString() {
            return ".";
        }
    },

    /**
     * Punctuation #
     */
    POUND(false) {
        @Override
        public String toString() {
            return "#";
        }
    },

    /**
     * Punctuation -
     */
    DASH(false) {
        @Override
        public String toString() {
            return "-";
        }
    },

    /**
     * Punctuation ,
     */
    COMMA(false) {
        @Override
        public String toString() {
            return ",";
        }
    },

    /**
     * Punctuation ; : ...
     */
    COLON(false) {
        @Override
        public String toString() {
            return ":";
        }
    },

    /**
     * Punctuation ( [ {
     */
    OPENING_PARENTHESIS(false) {
        @Override
        public String toString() {
            return "(";
        }
    },

    /**
     * Punctuation ) ] }
     */
    CLOSING_PARENTHESIS(false) {
        @Override
        public String toString() {
            return ")";
        }
    },

    /**
     * Punctuation ` or ``
     */
    OPENING_QUOTATION(false) {
        @Override
        public String toString() {
            return "``";
        }
    },

    /**
     * Punctuation ' or ''
     */
    CLOSING_QUOTATION(false) {
        @Override
        public String toString() {
            return "''";
        }
    };

    /**
     * True if the POS is an open class.
     */
    public final boolean open;

    /**
     * Constructor.
     */
    PennTreebankPOS(boolean open) {
        this.open = open;
    }

    /**
     * Map of punctuation to its enum string value.
     */
    private static final Map<String, String> map;
    static {
        map = new HashMap<>();

        map.put(".", "SENT");
        map.put("?", "SENT");
        map.put("!", "SENT");

        map.put("#", "POUND");
        map.put("-", "DASH");
        map.put(",", "COMMA");


        map.put(";", "COLON");
        map.put(":", "COLON");
        map.put("...", "COLON");

        map.put("(", "OPENING_PARENTHESIS");
        map.put("[", "OPENING_PARENTHESIS");
        map.put("{", "OPENING_PARENTHESIS");
        
        map.put(")", "CLOSING_PARENTHESIS");
        map.put("]", "CLOSING_PARENTHESIS");
        map.put("}", "CLOSING_PARENTHESIS");

        map.put("`", "OPENING_QUOTATION");
        map.put("``", "OPENING_QUOTATION");

        map.put("'", "CLOSING_QUOTATION");
        map.put("''", "CLOSING_QUOTATION");
    }
    
    /**
     * Returns an enum value from a string. Note that valueOf cannot be
     * overridden, so we have to use this workaround for converting custom
     * strings to enum values without using valueOf method.
     *
     * @param value the string value.
     * @return the enum value.
     */
    public static PennTreebankPOS getValue(String value) {
        String s = map.get(value);
        return valueOf(s == null ? value : s);
    }
}
