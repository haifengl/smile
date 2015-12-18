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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is a simple paragraph splitter. Given a string, it returns a list of
 * strings, where each element is a paragraph.
 * <p>
 * The beginning of a paragraph is indicated by
 * <ul>
 * <li> the beginning of the content, that is, the paragraph is the first
 * content in the document, or
 * <li> exactly one blank line preceding the paragraph text
 * </ul>
 * The end of a paragraph is indicated by
 * <ul>
 * <li> the end of the content, that is, the paragraph is the last content in
 * the document, or
 * <li> one or more blank lines following the paragraph text
 * </ul>
 * A blank line contains zero or more non-printing characters, such as
 * space or tab, followed by a new line.
 * 
 * @author Haifeng Li
 */
public class SimpleParagraphSplitter implements ParagraphSplitter {
    /**
     * Remove whitespaces in an blank line. Note to turn multiline mode.
     */
    private static Pattern REGEX_BLANK_LINE = Pattern.compile("(?m)^\\s+$");
    /**
     * Pattern to split paragraphs. Note that \u2029 is paragraph-separator character
     */
    private static Pattern REGEX_PARAGRAPH = Pattern.compile("(\\n|(\\n\\r)|(\\r\\n)){2,}+|'\u2029+");

    /**
     * The singleton instance for standard unweighted Euclidean distance.
     */
    private static SimpleParagraphSplitter singleton = new SimpleParagraphSplitter();

    /**
     * Constructor.
     */
    private SimpleParagraphSplitter() {
    }

    /**
     * Returns the singleton instance.
     */
    public static SimpleParagraphSplitter getInstance() {
        return singleton;
    }

    @Override
    public String[] split(String text) {
        Matcher matcher = REGEX_BLANK_LINE.matcher(text);
        text = matcher.replaceAll("");

        return REGEX_PARAGRAPH.split(text);
    }
}
