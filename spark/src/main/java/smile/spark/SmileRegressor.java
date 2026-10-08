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
package smile.spark;

import java.io.Serializable;
import java.util.Objects;
import org.apache.spark.ml.linalg.Vector;
import org.apache.spark.ml.param.ParamMap;
import org.apache.spark.ml.regression.Regressor;
import org.apache.spark.ml.util.DefaultParamsReader;
import org.apache.spark.ml.util.DefaultParamsWriter;
import org.apache.spark.ml.util.Identifiable$;
import org.apache.spark.ml.util.MLReader;
import org.apache.spark.ml.util.MLWritable;
import org.apache.spark.ml.util.MLWriter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.storage.StorageLevel;

/**
 * A Spark ML {@link org.apache.spark.ml.Estimator} that trains a SMILE regression model.
 *
 * @author Haifeng Li
 */
public class SmileRegressor
        extends Regressor<Vector, SmileRegressor, SmileRegressionModel>
        implements MLWritable {

    private static final ThreadLocal<String> INIT_UID = new ThreadLocal<>();

    /**
     * Functional interface for training a SMILE regression model on double feature arrays.
     */
    @FunctionalInterface
    public interface Trainer extends Serializable {
        /**
         * Fits a SMILE regression model.
         *
         * @param x the feature matrix.
         * @param y the continuous target values.
         * @return the fitted regression model.
         */
        smile.regression.Regression<double[]> fit(double[][] x, double[] y);
    }

    private final String uid;
    private Trainer trainer;

    /**
     * Default constructor with a random UID.
     */
    public SmileRegressor() {
        this(Identifiable$.MODULE$.randomUID("SmileRegressor"));
    }

    /**
     * Constructor with a custom UID.
     *
     * @param uid the instance UID.
     */
    public SmileRegressor(String uid) {
        this(prepareUid(uid), true);
    }

    private static String prepareUid(String uid) {
        Objects.requireNonNull(uid, "uid cannot be null");
        INIT_UID.set(uid);
        return uid;
    }

    private SmileRegressor(String uid, boolean ignored) {
        super();
        this.uid = uid;
        INIT_UID.remove();
    }

    @Override
    public String uid() {
        if (uid != null) {
            return uid;
        }
        String init = INIT_UID.get();
        return init != null ? init : Identifiable$.MODULE$.randomUID("SmileRegressor");
    }

    /**
     * Returns the trainer function.
     *
     * @return the trainer function.
     */
    public Trainer getTrainer() {
        return trainer;
    }

    /**
     * Sets the trainer function.
     *
     * @param trainer the trainer function.
     * @return this estimator.
     */
    public SmileRegressor setTrainer(Trainer trainer) {
        this.trainer = trainer;
        return this;
    }

    @Override
    public SmileRegressor copy(ParamMap extra) {
        SmileRegressor copy = new SmileRegressor(uid);
        copyValues(copy, extra);
        copy.setTrainer(trainer);
        return copy;
    }

    @Override
    public SmileRegressionModel train(Dataset<?> dataset) {
        Objects.requireNonNull(trainer, "trainer must be set before fitting");
        Dataset<Row> df = dataset.select(getLabelCol(), getFeaturesCol());
        boolean persist = dataset.storageLevel() == StorageLevel.NONE() && df.storageLevel() == StorageLevel.NONE();
        if (persist) {
            df.persist(StorageLevel.MEMORY_AND_DISK());
        }

        var rows = df.collectAsList();
        int n = rows.size();
        double[][] x = new double[n][];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            Row row = rows.get(i);
            y[i] = row.getDouble(0);
            x[i] = ((Vector) row.get(1)).toArray();
        }

        if (persist) {
            df.unpersist();
        }

        smile.regression.Regression<double[]> model = trainer.fit(x, y);

        SmileRegressionModel regressionModel =
                new SmileRegressionModel(Identifiable$.MODULE$.randomUID("SmileRegressionModel"), model);
        copyValues(regressionModel, new ParamMap());
        return regressionModel;
    }

    @Override
    public MLWriter write() {
        return new SmileRegressorWriter(this);
    }

    /**
     * Returns an MLReader instance to load a persisted SmileRegressor.
     *
     * @return the reader.
     */
    public static MLReader<SmileRegressor> read() {
        return new SmileRegressorReader();
    }

    /**
     * Loads a persisted SmileRegressor from the given path.
     *
     * @param path the path.
     * @return the loaded estimator.
     */
    public static SmileRegressor load(String path) {
        return read().load(path);
    }

    private static class SmileRegressorWriter extends MLWriter {
        private final SmileRegressor instance;

        SmileRegressorWriter(SmileRegressor instance) {
            this.instance = instance;
        }

        @Override
        public void saveImpl(String path) {
            DefaultParamsWriter.saveMetadata(instance, path, sparkSession());
        }
    }

    private static class SmileRegressorReader extends MLReader<SmileRegressor> {
        @Override
        public SmileRegressor load(String path) {
            DefaultParamsReader.Metadata metadata = DefaultParamsReader.loadMetadata(path, sparkSession(), SmileRegressor.class.getName());
            SmileRegressor instance = new SmileRegressor(metadata.uid());
            metadata.getAndSetParams(instance, metadata.getAndSetParams$default$2());
            return instance;
        }
    }
}
