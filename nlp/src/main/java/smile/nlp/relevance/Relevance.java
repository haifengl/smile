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
    public final Text text;

    /**
     * The relevance score.
     */
    public final double score;

    /**
     * Constructor.
     * @param text the document to rank.
     * @param score the relevance score.
     */
    public Relevance(Text text, double score) {
        this.text = text;
        this.score = score;
    }

    @Override
    public int compareTo(Relevance o) {
        return Double.compare(score, o.score);
    }
}
