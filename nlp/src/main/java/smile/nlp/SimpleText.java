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

package smile.nlp;

import java.util.Arrays;
import java.util.HashMap;

/**
 * A list-of-words representation of documents.
 *
 * @author Haifeng Li
 */
public class SimpleText extends Text implements TextTerms, AnchorText {
    /**
     * All anchor text in the corpus pointing to this text.
     */
    private String anchor;

    /**
     * The list of words.
     */
    private String[] words;
    /**
     * The term frequency.
     */
    private HashMap<String, Integer> freq = new HashMap<>();
    /**
     * The maximum term frequency over all terms in the documents;
     */
    private int maxtf;

    /**
     * Constructor.
     * @param id the id of document.
     * @param words the word list of document.
     */
    public SimpleText(String id, String title, String body, String[] words) {
        super(id, title, body);

        this.words = words;

        for (String w : words) {
            Integer f = freq.get(w);
            if (f == null) {
                f = 1;
            } else {
                f = f + 1;
            }

            freq.put(w, f);

            if (f > maxtf) {
                maxtf = f;
            }
        }
    }
    
    @Override
    public int size() {
        return words.length;
    }

    @Override
    public Iterable<String> words() {
        return Arrays.asList(words);
    }

    @Override
    public Iterable<String> unique() {
        return freq.keySet();
    }
    
    @Override
    public int tf(String term) {
        Integer f = freq.get(term);
        
        if (f == null) {
            return 0;
        }
        
        return f;
    }

    @Override
    public int maxtf() {
        return maxtf;
    }

    /**
     * Returns the anchor text if any. The anchor text is the visible,
     * clickable text in a hyperlink. The anchor text is all the
     * anchor text in the corpus pointing to this text.
     */
    public String getAnchor() {
        return anchor;
    }
    
    /**
     * Sets the anchor text. Note that anchor is all link labels in the corpus
     * pointing to this text. So addAnchor is more appropriate in most cases.
     */
    public SimpleText setAnchor(String anchor) {
        this.anchor = anchor;
        return this;
    }
    
    public SimpleText addAnchor(String linkLabel) {
        if (anchor == null) {
            anchor = linkLabel;
        } else {
            anchor = anchor + " " + linkLabel;
        }
        return this;
    }
    
    @Override
    public String toString() {
        return String.format("Document[%s%s]", getID(), getTitle() == null ? "" : " -- "+ getTitle());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        final SimpleText other = (SimpleText) obj;
        return getID().equals(other.getID());
    }

    @Override
    public int hashCode() {
        return getID().hashCode();
    }
}
