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
