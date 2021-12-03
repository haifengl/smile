/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.feature.imputation;

import smile.data.DataTransform;
import smile.data.Tuple;

/**
 * Simple imputer replaces missing values with the mean/median/mode
 * along each column.
 *
 * @author Haifeng Li
 */
public class SimpleImputer implements DataTransform {
    /**
     * Constructor.
     */
    public SimpleImputer() {

    }

    @Override
    public Tuple apply(Tuple t) {
        return t;
    }
}
