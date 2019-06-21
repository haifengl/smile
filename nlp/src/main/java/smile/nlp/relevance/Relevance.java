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
