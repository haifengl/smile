package smile.feature.imputation;

import java.util.Arrays;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.measure.NominalScale;
import smile.data.transform.Transform;
import smile.data.type.StructField;
import smile.math.distance.Distance;
import smile.math.MathEx;
import smile.neighbor.KNNSearch;
import smile.neighbor.LinearSearch;
import smile.neighbor.Neighbor;

/**
 * Missing value imputation with k-nearest neighbors. The KNN-based method
 * selects instances similar to the instance of interest to impute
 * missing values. If we consider instance A that has one missing value on
 * attribute i, this method would find K other instances, which have a value
 * present on attribute i, with values most similar (in terms of some distance,
 * e.g. Euclidean distance) to A on other attributes without missing values.
 * The average of values on attribute i from the K nearest
 * neighbors is then used as an estimate for the missing value in instance A.
 * In the weighted average, the contribution of each instance is weighted by
 * similarity between it and instance A.
 *
 * @author Haifeng Li
 */public class KNNImputer implements Transform {
    /** The number of nearest neighbors used for imputation. */
    private final int k;
    /** K-nearest neighbor search algorithm. */
    private final KNNSearch<Tuple, Tuple> knn;

    /**
     * Constructor.
     * @param data the map of column name to the constant value.
     * @param k the number of nearest neighbors used for imputation.
     * @param distance the distance measure.
     */
    public KNNImputer(DataFrame data, int k, Distance<Tuple> distance) {
        this.k = k;
        this.knn = LinearSearch.of(data.stream().map(row -> (Tuple) row).toList(), distance);
    }

    /**
     * Constructor with Euclidean distance on selected columns.
     * @param data the map of column name to the constant value.
     * @param k the number of nearest neighbors used for imputation.
     * @param columns the columns used in Euclidean distance computation.
     *                If empty, all columns will be used.
     */
    public KNNImputer(DataFrame data, int k, String... columns) {
        this(data, k, (x, y) -> {
            double[] xd = x.toArray(columns);
            double[] yd = y.toArray(columns);
            return MathEx.distanceWithMissingValues(xd, yd);
        });
    }

    @Override
    public Tuple apply(Tuple x) {
        Neighbor<Tuple, Tuple>[] neighbors = knn.search(x, k);
        return new smile.data.AbstractTuple(x.schema()) {
            @Override
            public Object get(int i) {
                Object xi = x.get(i);
                if (!SimpleImputer.isMissing(xi)) {
                    return xi;
                } else {
                    StructField field = schema.field(i);
                    if (field.dtype().isBoolean()) {
                        int[] vector = MathEx.omit(
                                Arrays.stream(neighbors)
                                        .mapToInt(neighbor -> neighbor.key().getInt(i)).toArray(),
                                Integer.MIN_VALUE);
                        return vector.length == 0 ? null : MathEx.mode(vector) != 0;
                    } else if (field.dtype().isChar()) {
                        int[] vector = MathEx.omit(
                                Arrays.stream(neighbors)
                                        .mapToInt(neighbor -> neighbor.key().getInt(i)).toArray(),
                                Integer.MIN_VALUE);
                        return vector.length == 0 ? null : (char) MathEx.mode(vector);
                    } else if (field.measure() instanceof NominalScale) {
                        int[] vector = MathEx.omit(
                                Arrays.stream(neighbors)
                                        .mapToInt(neighbor -> neighbor.key().getInt(i)).toArray(),
                                Integer.MIN_VALUE);
                        return vector.length == 0 ? null : MathEx.mode(vector);
                    } else if (field.dtype().isNumeric()) {
                        double[] vector = MathEx.omit(
                                Arrays.stream(neighbors)
                                        .mapToDouble(neighbor -> neighbor.key().getDouble(i)).toArray(),
                                Integer.MIN_VALUE);
                        return vector.length == 0 ? null : MathEx.mean(vector);
                    } else {
                        return null;
                    }
                }
            }
        };
    }
}
