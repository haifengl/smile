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
