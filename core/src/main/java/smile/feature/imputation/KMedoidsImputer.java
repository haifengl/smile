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
package smile.feature.imputation;

import smile.clustering.CentroidClustering;
import smile.clustering.KMedoids;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.transform.Transform;
import smile.math.distance.Distance;

/**
 * Missing value imputation by K-Medoids clustering. The k-medoids algorithm
 * is an adaptation of the k-means algorithm. Rather than calculate the mean
 * of the items in each cluster, a representative item, or medoid, is chosen
 * for each cluster at each iteration. The missing values of an instance are
 * replaced the corresponding ones of the nearest medoid.
 *
 * @author Haifeng Li
 */
public class KMedoidsImputer implements Transform {
    /** The K-Medoids clustering. */
    private final CentroidClustering<Tuple, Tuple> kmedoids;

    /**
     * Constructor.
     * @param kmedoids the K-Medoids clustering.
     */
    public KMedoidsImputer(CentroidClustering<Tuple, Tuple> kmedoids) {
        this.kmedoids = kmedoids;
    }

    @Override
    public Tuple apply(Tuple x) {
        if (!SimpleImputer.hasMissing(x)) {
            return x;
        }

        Tuple medioid = kmedoids.center(kmedoids.predict(x));
        return new smile.data.AbstractTuple(x.schema()) {
            @Override
            public Object get(int i) {
                Object xi = x.get(i);
                return SimpleImputer.isMissing(xi) ? medioid.get(i) : xi;
            }
        };
    }

    /**
     * Fits the missing value imputation values.
     * @param data the training data.
     * @param k        the number of clusters.
     * @param distance the lambda of distance measure.
     * @return the imputer.
     */
    public static KMedoidsImputer fit(DataFrame data, Distance<Tuple> distance, int k) {
        Tuple[] tuples = new Tuple[data.size()];
        for (int i = 0; i < tuples.length; i++) {
            tuples[i] = data.get(i);
        }

        var kmedoids = KMedoids.fit(tuples, distance, k);
        return new KMedoidsImputer(kmedoids);
    }
}
