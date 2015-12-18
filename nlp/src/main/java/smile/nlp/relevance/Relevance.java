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

package smile.nlp.relevance;

import smile.nlp.Text;

/**
 * In the context of information retrieval, relevance denotes how well a
 * retrieved set of documents meets the information need of the user.
 *
 * @author Haifeng Li
 */
public class Relevance implements Comparable<Relevance> {

    /**
     * The document to rank.
     */
    private Text doc;

    /**
     * The relevance score.
     */
    private double score;

    /**
     * Constructor.
     * @param doc the document to rank.
     * @param score the relevance score.
     */
    public Relevance(Text doc, double score) {
        this.doc = doc;
        this.score = score;
    }

    /**
     * Returns the document to rank.
     */
    public Text doc() {
        return doc;
    }

    /**
     * Returns the relevance score.
     */
    public double score() {
        return score;
    }

    @Override
    public int compareTo(Relevance o) {
        return (int) Math.signum(score - o.score);
    }
}
