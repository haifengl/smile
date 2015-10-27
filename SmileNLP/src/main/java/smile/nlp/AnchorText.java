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
    public String getAnchor();    
    
    /**
     * Sets the anchor text. Note that anchor is all link labels in the corpus
     * pointing to this text. So addAnchor is more appropriate in most cases.
     */
    public AnchorText setAnchor(String anchor);
    
    /**
     * Add a link label to the anchor text.
     */
    public AnchorText addAnchor(String linkLabel);
}
