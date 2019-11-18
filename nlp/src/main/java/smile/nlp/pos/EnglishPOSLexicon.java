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

package smile.nlp.pos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

/**
 * An English lexicon with part-of-speech tags.
 *
 * @author Haifeng Li
 */
public class EnglishPOSLexicon {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EnglishPOSLexicon.class);

    /** Utility classes should not have public constructors. */
    private EnglishPOSLexicon() {

    }

    /**
     * A list of English words with POS tags.
     */
    private static final HashMap<String, PennTreebankPOS[]> dict = new HashMap<>();

    /**
     * The part-of-speech.txt file contains is a combination of
     * "Moby (tm) Part-of-Speech II" and the WordNet database.
     * 
     * The latest version can be found at http://aspell.sourceforge.net/wl/.
     * 
     * The format of each entry is
     * <word><tab><POS tag(s)><unix newline>
     * 
     * Where the POS tag is one or more of the following:
     * 
     * N    Noun
     * P    Plural
     * h    Noun Phrase
     * V    Verb (usu participle)
     * t    Verb (transitive)
     * i    Verb (intransitive)
     * A    Adjective
     * v    Adverb
     * C    Conjunction
     * P    Preposition
     * !    Interjection
     * r    Pronoun
     * D    Definite Article
     * I    Indefinite Article
     * o    Nominative
     * 
     * The parts of speech before any '|' (if at all) come from the original
     * Moby database.  Anything after the '|' comes from the WordNet
     * database.  The part of speech tags from the original Moby database are
     * in priority order where the principle usage is listed first.  The ones
     * from the WordNet database are not.  Entries from the moby database
     * have had any accents removed, the "ae" character expanded, the
     * pound, yen, and peseta sign converted to a '$' and the CP437
     * character 0xBE replaced with a '~'.
     */
    static {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(EnglishPOSLexicon.class.getResourceAsStream("/smile/nlp/pos/part-of-speech_en.txt")))) {
            input.lines().forEach(line -> {
                String[] pos = line.trim().split("\t");
                if (pos.length == 2) {
                    int len = pos[1].length();
                    if (pos[1].indexOf('|') != -1) {
                        len -= 1;
                    }

                    PennTreebankPOS[] tag = new PennTreebankPOS[len];

                    for (int i = 0, k = 0; i < pos[1].length(); i++) {
                        switch (pos[1].charAt(i)) {
                            case 'N':
                                tag[k++] = PennTreebankPOS.NN;
                                break;
                            case 'p':
                                tag[k++] = PennTreebankPOS.NNS;
                                break;
                            case 'h':
                                tag[k++] = PennTreebankPOS.NN;
                                break;
                            case 'V':
                            case 't':
                            case 'i':
                                tag[k++] = PennTreebankPOS.VB;
                                break;
                            case 'A':
                                tag[k++] = PennTreebankPOS.JJ;
                                break;
                            case 'v':
                                tag[k++] = PennTreebankPOS.RB;
                                break;
                            case 'C':
                                tag[k++] = PennTreebankPOS.CC;
                                break;
                            case 'P':
                                tag[k++] = PennTreebankPOS.IN;
                                break;
                            case '!':
                                tag[k++] = PennTreebankPOS.UH;
                                break;
                            case 'r':
                                tag[k++] = PennTreebankPOS.PRP;
                                break;
                            case 'D':
                            case 'I':
                                tag[k++] = PennTreebankPOS.DT;
                                break;
                            case 'o':
                                tag[k++] = PennTreebankPOS.NN;
                                break;
                        }
                    }

                    dict.put(pos[0], tag);
                }
            });
        } catch (IOException ex) {
            logger.error("Failed to load /smile/nlp/pos/part-of-speech_en.txt", ex);
        }
    }

    /**
     * Returns part-of-speech tags for given word, or null if the word does
     * not exist in the dictionary.
     */
    public static PennTreebankPOS[] get(String word) {
        return dict.get(word);
    }
}
