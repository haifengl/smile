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

package smile.nlp.stemmer;

/**
 * Porter's stemming algorithm. The stemmer is based on the idea that the
 * suffixes in the English language are mostly made up of a combination of
 * smaller and simpler suffixes. This is a linear step stemmer.
 * Specifically it has five steps applying rules within each step. Within
 * each step, if a suffix rule matched to a word, then the conditions
 * attached to that rule are tested on what would be the resulting stem,
 * if that suffix was removed, in the way defined by the rule. Once a Rule
 * passes its conditions and is accepted the rule fires and the suffix is
 * removed and control moves to the next step. If the rule is not accepted
 * then the next rule in the step is tested, until either a rule from that
 * step fires and control passes to the next step or there are no more rules
 * in that step whence control moves to the next step. For details, see
 * <p>
 * Martin Porter, An algorithm for suffix stripping, Program, 14(3), 130-137, 1980.
 * <p>
 * Note that this class is NOT multi-thread safe.
 *
 * The code is based on http://www.tartarus.org/~martin/PorterStemmer
 * 
 * History:
 * 
 * Release 1
 * 
 * Bug 1 (reported by Gonzalo Parra 16/10/99) fixed as marked below.
 * The words 'aed', 'eed', 'oed' leave k at 'a' for step 3, and b[k-1]
 * is then out outside the bounds of b.
 * 
 * Release 2
 * 
 * Similarly,
 * 
 * Bug 2 (reported by Steve Dyrdahl 22/2/00) fixed as marked below.
 * 'ion' by itself leaves j = -1 in the test for 'ion' in step 5, and
 * b[j] is then outside the bounds of b.
 * 
 * Release 3
 * 
 * Considerably revised 4/9/00 in the light of many helpful suggestions
 * from Brian Goetz of Quiotix Corporation (brian@quiotix.com).
 * 
 * Release 4
 */
public class PorterStemmer implements Stemmer {

    /**
     * Working buffer.
     */
    private char[] b;
    /**
     * A general offset into the string
     */
    private int j;
    /**
     * The offset to the current working character.
     */
    private int k;

    /**
     * Constructor.
     */
    public PorterStemmer() {
    }

    /**
     * Returns true if b[i] is a consonant.
     */
    private final boolean isConsonant(int i) {
        switch (b[i]) {
            case 'a':
            case 'e':
            case 'i':
            case 'o':
            case 'u':
                return false;
            case 'y':
                return (i == 0) ? true : !isConsonant(i - 1);
            default:
                return true;
        }
    }

    /**
     * m() measures the number of consonant sequences between 0 and j. if c is
     * a consonant sequence and v a vowel sequence, and <..> indicates arbitrary
     * presence,
     * <ul>
     * <li> <c><v>       gives 0
     * <li> <c>vc<v>     gives 1
     * <li> <c>vcvc<v>   gives 2
     * <li> <c>vcvcvc<v> gives 3
     * <li> ....
     * </ul>
     */
    private final int m() {
        int n = 0;
        int i = 0;
        while (true) {
            if (i > j) {
                return n;
            }
            if (!isConsonant(i)) {
                break;
            }
            i++;
        }
        i++;
        while (true) {
            while (true) {
                if (i > j) {
                    return n;
                }
                if (isConsonant(i)) {
                    break;
                }
                i++;
            }
            i++;
            n++;
            while (true) {
                if (i > j) {
                    return n;
                }
                if (!isConsonant(i)) {
                    break;
                }
                i++;
            }
            i++;
        }
    }

    /**
     * Returns true if 0,...j contains a vowel
     */
    private final boolean vowelinstem() {
        int i;
        for (i = 0; i <= j; i++) {
            if (!isConsonant(i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if j,(j-1) contain a double consonant.
     */
    private final boolean doublec(int j) {
        if (j < 1) {
            return false;
        }
        if (b[j] != b[j - 1]) {
            return false;
        }
        return isConsonant(j);
    }

    /**
     *  cvc(i) is true <=> i-2,i-1,i has the form consonant - vowel - consonant
     *  and also if the second c is not w,x or y. this is used when trying to
     *  restore an e at the end of a short word. e.g.
     *  cav(e), lov(e), hop(e), crim(e), but snow, box, tray.
     */
    private final boolean cvc(int i) {
        if (i < 2 || !isConsonant(i) || isConsonant(i - 1) || !isConsonant(i - 2)) {
            return false;
        }
        {
            int ch = b[i];
            if (ch == 'w' || ch == 'x' || ch == 'y') {
                return false;
            }
        }
        return true;
    }

    /** Returns true if the buffer ends with the given string. */
    private final boolean endsWith(String s) {
        int l = s.length();
        int o = k - l + 1;
        if (o < 0) {
            return false;
        }
        for (int i = 0; i < l; i++) {
            if (b[o + i] != s.charAt(i)) {
                return false;
            }
        }
        j = k - l;
        return true;
    }

    /**
     * Sets (j+1),...k to the characters in the string s, readjusting k.
     */
    private final void setto(String s) {
        int l = s.length();
        int o = j + 1;
        for (int i = 0; i < l; i++) {
            b[o + i] = s.charAt(i);
        }
        k = j + l;
    }

    /**
     * Used further down.
     */
    private final void r(String s) {
        if (m() > 0) {
            setto(s);
        }
    }

    /**
     * step1 without special handling ending y.
     */
    private final void step1() {
        step1(false);
    }
    
    /**
     * step1() gets rid of plurals and -ed or -ing. e.g. If the argument y is true,
     * do the special handling of ending ies and ied.
     *
     * caresses  ->  caress
     * ponies    ->  poni
     * ties      ->  ti
     * caress    ->  caress
     * cats      ->  cat
     *
     * feed      ->  feed
     * agreed    ->  agree
     * disabled  ->  disable
     *
     * matting   ->  mat
     * mating    ->  mate
     * meeting   ->  meet
     * milling   ->  mill
     * messing   ->  mess
     *
     * meetings  ->  meet
     */
    private final void step1(boolean y) {
        if (b[k] == 's') {
            if (endsWith("sses")) {
                k -= 2;
            } else if (endsWith("ies")) {
                if (y && k-3 >= 0 && isConsonant(k-3)) {
                    setto("y");
                } else {
                    setto("i");
                }
            } else if (b[k - 1] != 's') {
                k--;
            }
        }
        if (endsWith("eed")) {
            if (m() > 0) {
                k--;
            }
        } else if ((endsWith("ed") || endsWith("ing")) && vowelinstem()) {
            k = j;
            if (endsWith("at")) {
                setto("ate");
            } else if (endsWith("bl")) {
                setto("ble");
            } else if (endsWith("iz")) {
                setto("ize");
            } else if (y && endsWith("i") && k-1 >= 0 && isConsonant(k-1)) {
                setto("y");
            } else if (doublec(k)) {
                k--;
                {
                    int ch = b[k];
                    if (ch == 'l' || ch == 's' || ch == 'z') {
                        k++;
                    }
                }
            } else if (m() == 1 && cvc(k)) {
                setto("e");
            }
        }
    }

    /**
     * step2() turns terminal y to i when there is another vowel in the stem.
     */
    private final void step2() {
        if (endsWith("y") && vowelinstem()) {
            b[k] = 'i';
        }
    }

    /**
     * step3() maps double suffices to single ones. so -ization ( = -ize plus
     * -ation) maps to -ize etc. note that the string before the suffix must give
     * m() > 0.
     */
    private final void step3() {
        if (k == 0) {
            return;
        }

        switch (b[k - 1]) {
            case 'a':
                if (endsWith("ational")) {
                    r("ate");
                    break;
                }
                if (endsWith("tional")) {
                    r("tion");
                    break;
                }
                break;
            case 'c':
                if (endsWith("enci")) {
                    r("ence");
                    break;
                }
                if (endsWith("anci")) {
                    r("ance");
                    break;
                }
                break;
            case 'e':
                if (endsWith("izer")) {
                    r("ize");
                    break;
                }
                break;
            case 'l':
                if (endsWith("bli")) {
                    r("ble");
                    break;
                }
                if (endsWith("alli")) {
                    r("al");
                    break;
                }
                if (endsWith("entli")) {
                    r("ent");
                    break;
                }
                if (endsWith("eli")) {
                    r("e");
                    break;
                }
                if (endsWith("ousli")) {
                    r("ous");
                    break;
                }
                break;
            case 'o':
                if (endsWith("ization")) {
                    r("ize");
                    break;
                }
                if (endsWith("ation")) {
                    r("ate");
                    break;
                }
                if (endsWith("ator")) {
                    r("ate");
                    break;
                }
                break;
            case 's':
                if (endsWith("alism")) {
                    r("al");
                    break;
                }
                if (endsWith("iveness")) {
                    r("ive");
                    break;
                }
                if (endsWith("fulness")) {
                    r("ful");
                    break;
                }
                if (endsWith("ousness")) {
                    r("ous");
                    break;
                }
                break;
            case 't':
                if (endsWith("aliti")) {
                    r("al");
                    break;
                }
                if (endsWith("iviti")) {
                    r("ive");
                    break;
                }
                if (endsWith("biliti")) {
                    r("ble");
                    break;
                }
                break;
            case 'g':
                if (endsWith("logi")) {
                    r("log");
                    break;
                }
        }
    }

    /**
     * step4() deals with -ic-, -full, -ness etc. similar strategy to step3.
     */
    private final void step4() {
        switch (b[k]) {
            case 'e':
                if (endsWith("icate")) {
                    r("ic");
                    break;
                }
                if (endsWith("ative")) {
                    r("");
                    break;
                }
                if (endsWith("alize")) {
                    r("al");
                    break;
                }
                break;
            case 'i':
                if (endsWith("iciti")) {
                    r("ic");
                    break;
                }
                break;
            case 'l':
                if (endsWith("ical")) {
                    r("ic");
                    break;
                }
                if (endsWith("ful")) {
                    r("");
                    break;
                }
                break;
            case 's':
                if (endsWith("ness")) {
                    r("");
                    break;
                }
                break;
        }
    }

    /**
     * step5() takes off -ant, -ence etc., in context <c>vcvc<v>.
     */
    private final void step5() {
        if (k == 0) {
            return;
        }
        
        switch (b[k - 1]) {
            case 'a':
                if (endsWith("al")) {
                    break;
                }
                return;
            case 'c':
                if (endsWith("ance")) {
                    break;
                }
                if (endsWith("ence")) {
                    break;
                }
                return;
            case 'e':
                if (endsWith("er")) {
                    break;
                }
                return;
            case 'i':
                if (endsWith("ic")) {
                    break;
                }
                return;
            case 'l':
                if (endsWith("able")) {
                    break;
                }
                if (endsWith("ible")) {
                    break;
                }
                return;
            case 'n':
                if (endsWith("ant")) {
                    break;
                }
                if (endsWith("ement")) {
                    break;
                }
                if (endsWith("ment")) {
                    break;
                }
                /* element etc. not stripped before the m */
                if (endsWith("ent")) {
                    break;
                }
                return;
            case 'o':
                if (endsWith("ion") && j >= 0 && (b[j] == 's' || b[j] == 't')) {
                    break;
                }
                if (endsWith("ou")) {
                    break;
                }
                return;
            /* takes care of -ous */
            case 's':
                if (endsWith("ism")) {
                    break;
                }
                return;
            case 't':
                if (endsWith("ate")) {
                    break;
                }
                if (endsWith("iti")) {
                    break;
                }
                return;
            case 'u':
                if (endsWith("ous")) {
                    break;
                }
                return;
            case 'v':
                if (endsWith("ive")) {
                    break;
                }
                return;
            case 'z':
                if (endsWith("ize")) {
                    break;
                }
                return;
            default:
                return;
        }
        if (m() > 1) {
            k = j;
        }
    }

    /**
     * step6() removes a final -e if m() > 1.
     */
    private final void step6() {
        j = k;
        if (b[k] == 'e') {
            int a = m();
            if (a > 1 || a == 1 && !cvc(k - 1)) {
                k--;
            }
        }
        if (b[k] == 'l' && doublec(k) && m() > 1) {
            k--;
        }
    }

    @Override
    public String stem(String word) {
        b = word.toCharArray();

        k = word.length() - 1;
        if (k > 1) {
            step1();
            step2();
            step3();
            step4();
            step5();
            step6();
        }

        return new String(b, 0, k+1);
    }
    
    /**
     * Remove plurals and participles.
     */
    public String stripPluralParticiple(String word) {
        b = word.toCharArray();

        k = word.length() - 1;
        if (k > 1 && !word.equalsIgnoreCase("is") && !word.equalsIgnoreCase("was") && !word.equalsIgnoreCase("has") && !word.equalsIgnoreCase("his") && !word.equalsIgnoreCase("this")) {
            step1(true);
            return new String(b, 0, k+1);
        }

        return word;
    }
}

