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

package smile.sequence;

import java.io.Serializable;

/**
 * A sequence labeler assigns a class label to each position of the sequence.
 *
 * @author Haifeng Li
 */
public interface SequenceLabeler<T> extends Serializable {
    /**
     * Predicts the sequence labels.
     * @param x a sequence. At each position, it may be the original symbol or
     * a feature set about the symbol, its neighborhood, and/or other information.
     * @return the predicted sequence labels.
     */
    int[] predict(T[] x);
}
