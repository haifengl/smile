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
 * A minimal interface of text in the corpus.
 * 
 * @author Haifeng Li
 */
public class Text {
    /**
     * The id of document in the corpus.
     */
    private String id;
    /**
     * The title of document;
     */
    private String title;
    /**
     * The text body.
     */
    private String body;
    
    public Text(String id, String title, String body) {
        this.id = id;
        this.title = title;
        this.body = body;
    }
    /**
     * Returns the id of document in the corpus.
     */
    public String getID() {
        return id;
    }

    public Text setID(String id) {
        this.id = id;
        return this;
    }
    
    /**
     * Returns the title of text.
     */
    public String getTitle() {
        return title;
    }

    public Text setTitle(String title) {
        this.title = title;
        return this;
    }
    
    /**
     * Returns the body of text.
     */
    public String getBody() {
        return body;
    }

    public Text setBody(String body) {
        this.body = body;
        return this;
    }
}
