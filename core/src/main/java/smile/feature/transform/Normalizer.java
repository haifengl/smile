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
package smile.feature.transform;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import smile.data.Tuple;
import smile.data.transform.Transform;
import smile.data.type.StructType;

/**
 * Normalize samples individually to unit norm. Each sample (i.e. each row of
 * the data matrix) with at least one non-zero component is rescaled
 * independently of other samples so that its norm (L1 or L2) equals one.
 * <p>
 * Scaling inputs to unit norms is a common operation for text
 * classification or clustering for instance.
 *
 * @author Haifeng Li
 */
public class Normalizer implements Transform {
    /** Vector norm. */
    public enum Norm {
        /** Normalize L1 vector norm. */
        L1,
        /** Normalize L2 vector norm. */
        L2,
        /** Normalize L-infinity vector norm. Maximum absolute value. */
        L_INF
    }

    /** The vector norm. */
    private final Norm norm;
    /** The columns to transform. */
    private final Set<String> columns;

    /**
     * Constructor.
     * @param norm the vector norm.
     * @param columns the columns to transform.
     */
    public Normalizer(Norm norm, String... columns) {
        if (columns.length == 0) {
            throw new IllegalArgumentException("Empty list of columns to transform");
        }

        this.norm = norm;
        this.columns = new HashSet<>(Arrays.asList(columns));
    }

    @Override
    public Tuple apply(Tuple x) {
        StructType schema = x.schema();
        double norm = 0.0;
        for (String column : columns) {
            double xi = x.getDouble(column);
            switch (this.norm) {
                case L1:
                    norm += Math.abs(xi);
                    break;
                case L2:
                    norm += xi * xi;
                    break;
                case L_INF:
                    norm = Math.max(norm, Math.abs(xi));
                    break;
            }
        }

        if (this.norm == Norm.L2) {
                norm = Math.sqrt(norm);
        }

        final double scale = norm;
        return new smile.data.AbstractTuple(schema) {
            @Override
            public Object get(int i) {
                if (columns.contains(schema.field(i).name())) {
                    return x.getDouble(i) / scale;
                } else {
                    return x.get(i);
                }
            }
        };
    }

    @Override
    public String toString() {
        return norm + "_Normalizer(" + String.join(", ", columns) + ")";
    }
}
