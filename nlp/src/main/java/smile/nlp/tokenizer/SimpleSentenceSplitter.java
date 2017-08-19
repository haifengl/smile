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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import smile.nlp.dictionary.EnglishDictionary;

/**
 * This is a simple sentence splitter for English. Given a string, assumed to
 * be English text, it returns a list of strings, where each element is an
 * English sentence. By default, it treats occurrences of '.', '?' and '!' as
 * sentence delimiters, but does its best to determine when an occurrence of '.'
 * does not have this role (e.g. in abbreviations, URLs, numbers, etc.).
 * <p>
 * Recognizing the end of a sentence is not an easy task for a computer.
 * In English, punctuation marks that usually appear at the end of a sentence
 * may not indicate the end of a sentence. The period is the worst offender.
 * A period can end a sentence but it can also be part of an abbreviation
 * or acronym, an ellipsis, a decimal number, or part of a bracket of periods
 * surrounding a Roman numeral. A period can even act both as the end of an
 * abbreviation and the end of a sentence at the same time. Other the other
 * hand, some poems may not contain any sentence punctuation at all.
 * <p>
 * Another problem punctuation mark is the single quote, which can introduce
 * a quote or start a contraction such as 'tis. Leading-quote contractions
 * are uncommon in contemporary English texts, but appear frequently in Early
 * Modern English texts.
 *
 * This tokenizer assumes that the text has already been segmented into
 * paragraphs. Any carriage returns will be replaced by whitespace.
 *
 * <h2>References</h2>
 * <ol>
 * <li> Paul Clough. A Perl program for sentence splitting using rules. <li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class SimpleSentenceSplitter implements SentenceSplitter {

    /**
     * Regular expression to remove carriage returns.
     */
    private static final Pattern REGEX_CARRIAGE_RETURN = Pattern.compile("[\\n\\r]+");
    /**
     * Regular expression to insert forgotten space after the end of sentences.
     */
    private static final Pattern REGEX_FORGOTTEN_SPACE = Pattern.compile("(.)([\\.!?])([\\D&&\\S&&[^\\.\"'`\\)\\}\\]]])");
    /**
     * Regular expression to match sentences with basic rules.
     */
    private static final Pattern REGEX_SENTENCE = Pattern.compile("(['\"`]*[\\(\\{\\[]?[a-zA-Z0-9]+.*?)([\\.!?:])(?:(?=([\\(\\[\\{\"'`\\)\\}\\]<]*[ \031]+)[\\(\\[\\{\"'`\\)\\}\\] ]*([A-Z0-9][a-z]*))|(?=([\\(\\)\"'`\\)\\}<\\] \031]+)\\s))");
    /**
     * Regular expression to split words.
     */
    private static final Pattern REGEX_WHITESPACE = Pattern.compile("\\s+");
    /**
     * Regular expression of last word (maybe only one word in the sentence).
     */
    private static final Pattern REGEX_LAST_WORD = Pattern.compile("\\b([\\w0-9\\.']+)$");
    /**
     * The singleton instance.
     */
    private static SimpleSentenceSplitter singleton = new SimpleSentenceSplitter();

    /**
     * Constructor.
     */
    private SimpleSentenceSplitter() {
    }

    /**
     * Returns the singleton instance.
     */
    public static SimpleSentenceSplitter getInstance() {
        return singleton;
    }

    @Override
    public String[] split(String text) {
        ArrayList<String> sentences = new ArrayList<>();

        // The number of words in the sentence.
        int len = 0;

        // Remove any carriage returns etc.
        text = REGEX_CARRIAGE_RETURN.matcher(text).replaceAll(" ");

        // We will use oct 031 (hex 19) as a special character for missing
        // space after punctuation. Oct 031 means "end of medium", which
        // probably never appears in a string in real applications.
        text = text.replace('\031', ' ');
        
        // make sure there are always spaces following punctuation to enable
        // splitter to work properly - covers such cases as "believe.I ...",
        // where a space has forgotten to be.
        text = REGEX_FORGOTTEN_SPACE.matcher(text).replaceAll("$1$2\031$3");

        text = text + "\n";

        // sentence ends with [.!?], followed by capital or number. Use base-line
        // splitter and then use some heuristics to improve upon this e.g.
        // dealing with Mr. and etc.  In this rather large regex we allow for
        // quotes, brackets etc.
        // $1 = the complete sentence including beginning punctuation and brackets
        // $2 = the punctuation mark - either [.!?:]
        // $3 = the brackets or quotes after the [!?.:]. This is non-grouping i.e. does not consume.
        // $4 = the next word after the [.?!:].This is non-grouping i.e. does not consume.
        // $5 = rather than a next word, it may have been the last sentence in the file. Therefore capture
        //      punctuation and brackets before end of file. This is non-grouping i.e. does not consume.
        Matcher matcher = REGEX_SENTENCE.matcher(text);
        StringBuilder currentSentence = new StringBuilder();
        int end = 0; // The offset of the end of sentence
        while (matcher.find()) {
            end = matcher.end();
            String sentence = matcher.group(1).trim();
            String punctuation = matcher.group(2);

            String stuffAfterPeriod = matcher.group(3);
            if (stuffAfterPeriod == null) {
                stuffAfterPeriod = matcher.group(5);
                if (stuffAfterPeriod == null) {
                    stuffAfterPeriod = "";
                } else {
                    end = matcher.end(5);
                }
            } else {
                end = matcher.end(3);
            }

            String[] words = REGEX_WHITESPACE.split(sentence);
            len += words.length;

            String nextWord = matcher.group(4);
            if (nextWord == null) {
                nextWord = "";
            }

            if (punctuation.compareTo(".") == 0) {
                // Consider the word before the period.
                // Is it an abbreviation? (then not full-stop)
                // Abbreviation if:
                //  1) all consonants and not all capitalised (and contain no lower case y e.g. shy, sly
                //  2) a span of single letters followed by periods
                //  3) a single letter (except I).
                //  4) in the known abbreviations list.
                // In above cases, then the period is NOT a full stop.

                // perhaps only one word e.g. P.S rather than a whole sentence
                Matcher lastWordMatcher = REGEX_LAST_WORD.matcher(sentence);
                String lastWord = "";
                if (lastWordMatcher.find()) {
                    lastWord = lastWordMatcher.group();
                }

                if ((!lastWord.matches(".*[AEIOUaeiou]+.*") && lastWord.matches(".*[a-z]+.*") && !lastWord.matches(".*[y]+.*"))
                        || lastWord.matches("([a-zA-Z][\\.])+")
                        || (lastWord.matches("^[A-Za-z]$") && !lastWord.matches("^[I]$"))
                        || EnglishAbbreviations.contains(lastWord.toLowerCase())) {

                    // We have an abbreviation, but this could come at the middle or end of a
                    // sentence. Therefore we assume that the abbreviation is not at the end of
                    // a sentence if the next word is a common word and the abbreviation occurs
                    // less than 5 words from the start of the sentence.
                    if (EnglishDictionary.CONCISE.contains(nextWord) && len > 6) {
                        // a sentence break
                        currentSentence.append(sentence);
                        currentSentence.append(punctuation);
                        currentSentence.append(stuffAfterPeriod.trim());
                        sentences.add(currentSentence.toString());
                        currentSentence = new StringBuilder();
                        len = 0;
                    } else {
                        // not a sentence break
                        currentSentence.append(sentence);
                        currentSentence.append(punctuation);
                        if (stuffAfterPeriod.indexOf('\031') == -1) {
                            currentSentence.append(' ');
                        }
                    }
                } else {
                    // a sentence break
                    currentSentence.append(sentence);
                    currentSentence.append(punctuation);
                    currentSentence.append(stuffAfterPeriod.trim());
                    sentences.add(currentSentence.toString());
                    currentSentence = new StringBuilder();
                    len = 0;
                }

            } else {
                // only consider sentences if : comes after at least 6 words from start of sentence
                if (punctuation.matches("[!?]") || (punctuation.compareTo(":") == 0 && len > 6)) {
                    // a sentence break
                    currentSentence.append(sentence);
                    currentSentence.append(punctuation);
                    currentSentence.append(stuffAfterPeriod.trim());
                    sentences.add(currentSentence.toString());
                    currentSentence = new StringBuilder();
                    len = 0;
                } else {
                    // not a sentence break
                    currentSentence.append(sentence);
                    currentSentence.append(punctuation);
                    if (stuffAfterPeriod.indexOf('\031') == -1) {
                        currentSentence.append(' ');
                    }
                }
            }
        }

        if (end < text.length()) {
            // There may be something after the last sentence.
            String lastPart = text.substring(end);
            if (!lastPart.isEmpty()) {
                currentSentence.append(lastPart);
            }
        }

        // If currentSentence is not empty (e.g. break at abbrev), add it to the results.
        if (currentSentence.length() > 0) {
            sentences.add(currentSentence.toString().trim());
        }

        String[] result = new String[sentences.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = sentences.get(i).replaceAll("\031", "");
        }
        
        return result;
    }
}
