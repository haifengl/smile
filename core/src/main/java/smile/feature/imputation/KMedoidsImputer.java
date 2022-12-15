package smile.feature.imputation;

import smile.clustering.CLARANS;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.transform.Transform;
import smile.data.type.StructType;
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
    private final CLARANS<Tuple> kmedoids;

    /**
     * Constructor.
     * @param kmedoids the K-Medoids clustering.
     */
    public KMedoidsImputer(CLARANS<Tuple> kmedoids) {
        this.kmedoids = kmedoids;
    }

    @Override
    public Tuple apply(Tuple x) {
        if (!SimpleImputer.hasMissing(x)) {
            return x;
        }

        StructType schema = x.schema();
        Tuple medioid = kmedoids.centroids[kmedoids.predict(x)];
        return new smile.data.AbstractTuple() {
            @Override
            public Object get(int i) {
                Object xi = x.get(i);
                return SimpleImputer.isMissing(xi) ? medioid.get(i) : xi;
            }

            @Override
            public StructType schema() {
                return schema;
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

        CLARANS<Tuple> kmedoids = CLARANS.fit(tuples, distance, k);
        return new KMedoidsImputer(kmedoids);
    }
}
