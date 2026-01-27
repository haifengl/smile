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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.vector;

import smile.data.type.StructField;
import smile.math.MathEx;

/**
 * Abstract base class implementation of ValueVector interface.
 *
 * @author Haifeng Li
 */
public abstract class PrimitiveVector extends AbstractVector {
    /**
     * Constructor.
     * @param field The struct field of the vector.
     */
    public PrimitiveVector(StructField field) {
        super(field);
    }

    /**
     * Returns the mean.
     * @return the mean.
     */
    public double mean() {
        return doubleStream().filter(Double::isFinite).average().orElse(0);
    }

    /**
     * Returns the standard deviation.
     * @return the standard deviation.
     */
    public double stdev() {
        double[] data = doubleStream().filter(Double::isFinite).toArray();
        return MathEx.stdev(data);
    }

    /**
     * Returns the minimal value.
     * @return the minimal value.
     */
    public double min() {
        return doubleStream().filter(Double::isFinite).min().orElse(0);
    }

    /**
     * Returns the maximal value.
     * @return the maximal value.
     */
    public double max() {
        return doubleStream().filter(Double::isFinite).max().orElse(0);
    }

    /**
     * Returns the median.
     * @return the median.
     */
    public double median() {
        double[] data = doubleStream().filter(Double::isFinite).toArray();
        return MathEx.median(data);
    }

    /**
     * Returns the 25% quantile.
     * @return the 25% quantile.
     */
    public double q1() {
        double[] data = doubleStream().filter(Double::isFinite).toArray();
        return MathEx.q1(data);
    }

    /**
     * Returns the 75% quantile.
     * @return the 75% quantile.
     */
    public double q3() {
        double[] data = doubleStream().filter(Double::isFinite).toArray();
        return MathEx.q3(data);
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public boolean isNullAt(int i) {
        return false;
    }

    @Override
    public int getNullCount() {
        return 0;
    }
}
