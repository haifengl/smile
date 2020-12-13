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

package smile.validation;

import java.io.Serializable;

/**
 * A bag of random selected samples.
 *
 * @author Haifeng Li
 */
public class Bag implements Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The random samples.
     */
    public final int[] samples;
    /**
     * The index of testing instances.
     */
    public final int[] oob;

    /**
     * Constructor.
     * @param samples the random samples.
     * @param oob the out of bag samples.
     */
    public Bag(int[] samples, int[] oob) {
        this.samples = samples;
        this.oob = oob;
    }
}
