/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.nlp.relevance;

import smile.nlp.Text;

/**
 * In the context of information retrieval, relevance denotes how well a
 * retrieved set of documents meets the information need of the user.
 *
 * @param text the document to rank.
 * @param score the relevance score.
 * @author Haifeng Li
 */
public record Relevance(Text text, double score) implements Comparable<Relevance> {
    @Override
    public int compareTo(Relevance o) {
        return Double.compare(score, o.score);
    }
}
