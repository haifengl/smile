/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.nlp;

import java.util.UUID;

/**
 * A minimal interface of text in the corpus.
 * 
 * @author Haifeng Li
 */
public class Text {
    /**
     * The id of document in the corpus.
     */
    public final String id;
    /**
     * The title of document;
     */
    public final String title;
    /**
     * The text body.
     */
    public final String body;

    /**
     * Constructor.
     * @param body the text body of document.
     */
    public Text(String body) {
        this("", body);
    }

    /**
     * Constructor.
     * @param title the title of document.
     * @param body the text body of document.
     */
    public Text(String title, String body) {
        this(UUID.randomUUID().toString(), title, body);
    }

    /**
     * Constructor.
     * @param id the id of document.
     * @param title the title of document.
     * @param body the text body of document.
     */
    public Text(String id, String title, String body) {
        this.id = id;
        this.title = title;
        this.body = body;
    }
}
