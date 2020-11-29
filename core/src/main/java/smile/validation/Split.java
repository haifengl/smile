/*******************************************************************************
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
 ******************************************************************************/

package smile.validation;

import java.io.Serializable;

/**
 * A (random) split of training and test data.
 *
 * @author Haifeng Li
 */
public class Split implements Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The index of training instances.
     */
    public final int[] train;
    /**
     * The index of testing instances.
     */
    public final int[] test;

    /**
     * Constructor.
     * @param train the index of training instances.
     * @param test the index of test instances.
     */
    public Split(int[] train, int[] test) {
        this.train = train;
        this.test = test;
    }
}
