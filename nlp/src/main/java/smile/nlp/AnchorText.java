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

/**
 * The anchor text is the visible, clickable text in a hyperlink.
 * This provides an interface to all the anchor text in the corpus
 * pointing to a text.
 * 
 * @author Haifeng Li
 */
public interface AnchorText {
    
    /**
     * Returns the anchor text if any. The anchor text is the visible,
     * clickable text in a hyperlink. The anchor text is all the
     * anchor text in the corpus pointing to this text.
     */
    String getAnchor();
    
    /**
     * Sets the anchor text. Note that anchor is all link labels in the corpus
     * pointing to this text. So addAnchor is more appropriate in most cases.
     */
    AnchorText setAnchor(String anchor);
    
    /**
     * Add a link label to the anchor text.
     */
    AnchorText addAnchor(String linkLabel);
}
