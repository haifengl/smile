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

package smile.nlp.stemmer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * The Paice/Husk Lancaster stemming algorithm. The stemmer is a conflation
 * based iterative stemmer. The stemmer, although remaining efficient and
 * easily implemented, is known to be very strong and aggressive. The stemmer
 * utilizes a single table of rules, each of which may specify
 * the removal or replacement of an ending. For details, see
 *
 * <h2>References</h2>
 * <ol>
 * <li> Paice, Another stemmer, SIGIR Forum, 24(3), 56-61, 1990. </li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class LancasterStemmer implements Stemmer {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LancasterStemmer.class);

    /**
     * Array of rules
     */
    private final ArrayList<String> rules = new ArrayList<>();
    /**
     * ruleIndex is set up to provide faster access to relevant rules.
     */
    private final int[] index = new int[26];
    /**
     * Strip prefix if true.
     */
    private final boolean stripPrefix;

    /** Loads the rules. */
    private void readRules(InputStream is) throws IOException {
        // Load rules from Lancaster_rules.txt
        try (BufferedReader input = new BufferedReader(new InputStreamReader(is))) {
            input.lines().map(String::trim).filter(line -> !line.isEmpty()).forEach(rule -> {
                int j = rule.indexOf(' ');
                if (j != -1) {
                    rule = rule.substring(0, j);
                }
                rules.add(rule);
            });
        }

        // Now assign the number of the first rule that starts with each letter
        // (if any) to an alphabetic array to facilitate selection of sections
        char ch = 'a';

        for (int j = 0; j < rules.size(); j++) {
            while (rules.get(j).charAt(0) != ch) {
                ch++;
                index[charCode(ch)] = j;
            }
        }
    }

    /**
     * Constructor with default rules. By default, the stemmer will not strip prefix from words.
     */
    public LancasterStemmer() {
        this(false);
    }

    /**
     * Constructor with default rules.
     * 
     * @param stripPrefix true if the stemmer will strip prefix such as kilo,
     * micro, milli, intra, ultra, mega, nano, pico, pseudo.
     */
    public LancasterStemmer(boolean stripPrefix) {
        this.stripPrefix = stripPrefix;
        try {
            readRules(LancasterStemmer.class.getResourceAsStream("/smile/nlp/stemmer/Lancaster_rules.txt"));
        } catch (IOException ex) {
            logger.error("Failed to load /smile/nlp/stemmer/Lancaster_rules.txt", ex);
        }
    }


    /**
     * Constructor with customized rules. By default, the stemmer will not strip prefix from words.
     * @param customizedRules an input stream to read customized rules.
     * @throws IOException when fails to read the rule file.
     */
    public LancasterStemmer(InputStream customizedRules) throws IOException {
        this(customizedRules, false);
    }

    /**
     * Constructor with customized rules.
     *
     * @param customizedRules an input stream to read customized rules.
     * @param stripPrefix true if the stemmer will strip prefix such as kilo,
     * micro, milli, intra, ultra, mega, nano, pico, pseudo.
     * @throws IOException when fails to read the rule file.
     */
    public LancasterStemmer(InputStream customizedRules, boolean stripPrefix) throws IOException {
        this.stripPrefix = stripPrefix;
        readRules(customizedRules);
    }

    /**
     * Checks lowercase word for position of the first vowel
     */
    private int firstVowel(String word, int last) {
        int i = 0;
        if ((i < last) && (!(vowel(word.charAt(i), 'a')))) {
            i++;
        }

        if (i != 0) {
            while ((i < last) && (!(vowel(word.charAt(i), word.charAt(i - 1))))) {
                i++;
            }
        }

        return Math.min(i, last);
    }

    /**
     * Strips suffix off word
     */
    private String stripSuffixes(String word) {
        // 1 is positive, 0 undecided, -1 negative equivalent of pun vars positive undecided negative
        int ruleok;
        int Continue = 0;

        int pll = 0; //position of last letter
        int xl;  //counter for nuber of chars to be replaced and length of stemmed word if rule was aplied
        int pfv; //poition of first vowel
        int prt; //pointer into rule table
        int ir;  //index of rule
        int iw;  //index of word

        //char variables

        char ll; // last letter

        String rule; //variable holding the current rule
        String stem; // string holding the word as it is being stemmed this is returned as a stemmed word.

        boolean intact = true; //intact if the word has not yet been stemmed to determin a requirement of some stemming rules

        //set stem = to word
        stem = cleanup(word.toLowerCase());

        //move through the word to find the position of the last letter before a non letter char
        while ((pll + 1 < stem.length()) && ((stem.charAt(pll + 1) >= 'a') && (stem.charAt(pll + 1) <= 'z'))) {
            pll++;
        }
        if (pll < 1) {
            Continue = -1;
        }
        //find the position of the first vowel
        pfv = firstVowel(stem, pll);
        iw = stem.length() - 1;

        //repeat until continue == negative ie. -1
        while (Continue != -1) {
            Continue = 0;

            //SEEK RULE FOR A NEW FINAL LETTER
            ll = stem.charAt(pll);

            //last letter
            //Check to see if there are any possible rules for stemming
            if ((ll >= 'a') && (ll <= 'z')) {
                prt = index[charCode(ll)]; //pointer into rule-table
            } else {
                prt = -1; //0 is a vaild rule
            }

            if (prt == -1) {
                Continue = -1; //no rule available
            }

            if (Continue == 0) {
                // THERE IS A POSSIBLE RULE (OR RULES) : SEE IF ONE WORKS
                rule = rules.get(prt);
                // Take first rule
                while (Continue == 0) {
                    ruleok = 0;
                    if (rule.charAt(0) != ll) {
                        //rule-letter changes
                        Continue = -1;
                        ruleok = -1;
                    }
                    ir = 1;
                    //index of rule: 2nd character
                    iw = pll - 1;
                    //index of word: next-last letter
                    //repeat untill the rule is not undecided find a rule that is acceptable
                    while (ruleok == 0) {
                        if ((rule.charAt(ir) >= '0') && (rule.charAt(ir) <= '9')) //rule fully matched
                        {
                            ruleok = 1;
                        } else if (rule.charAt(ir) == '*') {
                            //match only if word intact
                            if (intact) {
                                ir = ir + 1;
                                // move forwards along rule
                                ruleok = 1;
                            } else {
                                ruleok = -1;
                            }
                        } else if (rule.charAt(ir) != stem.charAt(iw)) {
                            // mismatch of letters
                            ruleok = -1;
                        } else if (iw <= pfv) {
                            //insufficient stem remains
                            ruleok = -1;
                        } else {
                            //  move on to compare next pair of letters
                            ir = ir + 1; // move forwards along rule
                            iw = iw - 1; // move backwards along word
                        }
                    }

                    //if the rule that has just been checked is valid
                    if (ruleok == 1) {
                        // CHECK ACCEPTABILITY CONDITION FOR PROPOSED RULE
                        xl = 0; //count any replacement letters
                        while (!((rule.charAt(ir + xl + 1) >= '.') && (rule.charAt(ir + xl + 1) <= '>'))) {
                            xl++;
                        }
                        xl = pll + xl + 48 - ((int) (rule.charAt(ir)));
                        // position of last letter if rule used
                        if (pfv == 0) {
                            //if word starts with vowel...
                            if (xl < 1) {
                                // ...minimal stem is 2 letters
                                ruleok = -1;
                            }
                        } //if word start swith consonant...
                        else if ((xl < 2) | (xl < pfv)) {
                            ruleok = -1;
                            // ...minimal stem is 3 letters...
                            // ...including one or more vowel
                        }
                    }
                    // if using the rule passes the assertion tests
                    if (ruleok == 1) {
                        //  APPLY THE MATCHING RULE
                        intact = false;
                        // move end of word marker to position...
                        // ... given by the numeral.
                        pll = pll + 48 - ((int) (rule.charAt(ir)));
                        ir++;
                        StringBuilder sb = new StringBuilder(stem.substring(0, (pll + 1)));
                        // append any letters following numeral to the word
                        while ((ir < rule.length()) && (('a' <= rule.charAt(ir)) && (rule.charAt(ir) <= 'z'))) {
                            sb.append(rule.charAt(ir));
                            ir++;
                            pll++;
                        }
                        stem = sb.toString();
                        //if rule ends with '.' then terminate
                        if ((rule.charAt(ir)) == '.') {
                            Continue = -1;
                        } else {
                            //if rule ends with '>' then Continue
                            Continue = 1;
                        }
                    } else {
                        //if rule did not match then look for another
                        prt = prt + 1;
                        // move to next rule in RULETABLE
                        if (prt >= rules.size()) {
                            Continue = -1;
                        } else {
                            rule = rules.get(prt);
                            if (rule.charAt(0) != ll) {
                                //rule-letter changes
                                Continue = -1;
                            }
                        }
                    }
                }
            }
        }
        
        return stem;
    }

    /**
     * Determin whether ch is a vowel or not uses prev determination
     * when ch == y
     */
    private boolean vowel(char ch, char prev) {
        return switch (ch) {
            case 'a', 'e', 'i', 'o', 'u' -> true;
            case 'y' -> switch (prev) {
                case 'a', 'e', 'i', 'o', 'u' -> false;
                default -> true;
            };
            default -> false;
        };
    }

    /**
     * Returns the relavent array index for specified char 'a' to 'z'.
     */
    private static int charCode(char ch) {
        return ((int) ch) - 97;
    }

    /**
     * Removes prefixes so that suffix removal can commence.
     */
    private String stripPrefixes(String word) {
        String[] prefixes = {"kilo", "micro", "milli", "intra", "ultra", "mega",
            "nano", "pico", "pseudo"};

        for (String prefix : prefixes) {
            if ((word.startsWith(prefix)) && (word.length() > prefix.length())) {
                word = word.substring(prefix.length());
                return word;
            }
        }

        return word;
    }

    /**
     * Remove all non letter or digit characters from word
     */
    private String cleanup(String word) {
        int last = word.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < last; i++) {
            if ((word.charAt(i) >= 'a') & (word.charAt(i) <= 'z')) {
                sb.append(word.charAt(i));
            }
        }
        return sb.toString();
    }

    @Override
    public String stem(String word) {
        // Convert input to lowercase and remove all chars that are not a letter.
        word = cleanup(word.toLowerCase());

        //if str's length is greater than 2 then remove prefixes
        if ((word.length() > 3) && (stripPrefix)) {
            word = stripPrefixes(word);
        }

        // if str is not null remove suffix
        if (word.length() > 3) {
            word = stripSuffixes(word);
        }

        return word;
    }
}

