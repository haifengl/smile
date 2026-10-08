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
import java.util.Arrays;
import java.util.Objects;
import org.apache.spark.ml.classification.Classifier;
import org.apache.spark.ml.linalg.Vector;
import org.apache.spark.ml.param.ParamMap;
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
 * A Spark ML {@link org.apache.spark.ml.Estimator} that trains a SMILE classification model.
 *
 * @author Haifeng Li
 */
public class SmileClassifier
        extends Classifier<Vector, SmileClassifier, SmileClassificationModel>
        implements MLWritable {

    private static final ThreadLocal<String> INIT_UID = new ThreadLocal<>();

    /**
     * Functional interface for training a SMILE classifier on double feature arrays.
     */
    @FunctionalInterface
    public interface Trainer extends Serializable {
        /**
         * Fits a SMILE classifier.
         *
         * @param x the feature matrix.
         * @param y the class labels.
         * @return the fitted classifier.
         */
        smile.classification.Classifier<double[]> fit(double[][] x, int[] y);
    }

    private final String uid;
    private Trainer trainer;
    private int numClasses = -1;

    /**
     * Default constructor with a random UID.
     */
    public SmileClassifier() {
        this(Identifiable$.MODULE$.randomUID("SmileClassifier"));
    }

    /**
     * Constructor with a custom UID.
     *
     * @param uid the instance UID.
     */
    public SmileClassifier(String uid) {
        this(prepareUid(uid), true);
    }

    private static String prepareUid(String uid) {
        Objects.requireNonNull(uid, "uid cannot be null");
        INIT_UID.set(uid);
        return uid;
    }

    private SmileClassifier(String uid, boolean ignored) {
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
        return init != null ? init : Identifiable$.MODULE$.randomUID("SmileClassifier");
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
    public SmileClassifier setTrainer(Trainer trainer) {
        this.trainer = trainer;
        return this;
    }

    /**
     * Returns the configured number of classes, or -1 if auto-detected.
     *
     * @return the number of classes.
     */
    public int getNumClasses() {
        return numClasses;
    }

    /**
     * Sets the number of classes explicitly.
     *
     * @param numClasses the number of classes.
     * @return this estimator.
     */
    public SmileClassifier setNumClasses(int numClasses) {
        this.numClasses = numClasses;
        return this;
    }

    @Override
    public SmileClassifier copy(ParamMap extra) {
        SmileClassifier copy = new SmileClassifier(uid);
        copyValues(copy, extra);
        copy.setTrainer(trainer);
        copy.setNumClasses(numClasses);
        return copy;
    }

    @Override
    public SmileClassificationModel train(Dataset<?> dataset) {
        Objects.requireNonNull(trainer, "trainer must be set before fitting");
        Dataset<Row> df = dataset.select(getLabelCol(), getFeaturesCol());
        boolean persist = dataset.storageLevel() == StorageLevel.NONE() && df.storageLevel() == StorageLevel.NONE();
        if (persist) {
            df.persist(StorageLevel.MEMORY_AND_DISK());
        }

        var rows = df.collectAsList();
        int n = rows.size();
        double[][] x = new double[n][];
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            Row row = rows.get(i);
            y[i] = (int) row.getDouble(0);
            x[i] = ((Vector) row.get(1)).toArray();
        }

        if (persist) {
            df.unpersist();
        }

        smile.classification.Classifier<double[]> model = trainer.fit(x, y);

        int k = this.numClasses;
        if (k <= 0) {
            try {
                k = getNumClasses(dataset, getNumClasses$default$2());
            } catch (Exception ex) {
                k = Arrays.stream(y).max().orElse(0) + 1;
            }
        }

        SmileClassificationModel classificationModel =
                new SmileClassificationModel(Identifiable$.MODULE$.randomUID("SmileClassificationModel"), k, model);
        copyValues(classificationModel, new ParamMap());
        return classificationModel;
    }

    @Override
    public MLWriter write() {
        return new SmileClassifierWriter(this);
    }

    /**
     * Returns an MLReader instance to load a persisted SmileClassifier.
     *
     * @return the reader.
     */
    public static MLReader<SmileClassifier> read() {
        return new SmileClassifierReader();
    }

    /**
     * Loads a persisted SmileClassifier from the given path.
     *
     * @param path the path.
     * @return the loaded estimator.
     */
    public static SmileClassifier load(String path) {
        return read().load(path);
    }

    private static class SmileClassifierWriter extends MLWriter {
        private final SmileClassifier instance;

        SmileClassifierWriter(SmileClassifier instance) {
            this.instance = instance;
        }

        @Override
        public void saveImpl(String path) {
            DefaultParamsWriter.saveMetadata(instance, path, sparkSession());
        }
    }

    private static class SmileClassifierReader extends MLReader<SmileClassifier> {
        @Override
        public SmileClassifier load(String path) {
            DefaultParamsReader.Metadata metadata = DefaultParamsReader.loadMetadata(path, sparkSession(), SmileClassifier.class.getName());
            SmileClassifier instance = new SmileClassifier(metadata.uid());
            metadata.getAndSetParams(instance, metadata.getAndSetParams$default$2());
            return instance;
        }
    }
}
