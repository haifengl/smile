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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.ml.linalg.Vector;
import org.apache.spark.ml.param.ParamMap;
import org.apache.spark.ml.regression.RegressionModel;
import org.apache.spark.ml.util.DefaultParamsReader;
import org.apache.spark.ml.util.DefaultParamsWriter;
import org.apache.spark.ml.util.Identifiable$;
import org.apache.spark.ml.util.MLReader;
import org.apache.spark.ml.util.MLWritable;
import org.apache.spark.ml.util.MLWriter;

/**
 * A Spark ML {@link org.apache.spark.ml.Model} produced by {@link SmileRegressor}.
 *
 * @author Haifeng Li
 */
public class SmileRegressionModel
        extends RegressionModel<Vector, SmileRegressionModel>
        implements MLWritable {

    private static final ThreadLocal<String> INIT_UID = new ThreadLocal<>();

    private final String uid;
    private final smile.regression.Regression<double[]> model;

    /**
     * Constructor with a default random UID.
     *
     * @param model the underlying SMILE regression model.
     */
    public SmileRegressionModel(smile.regression.Regression<double[]> model) {
        this(Identifiable$.MODULE$.randomUID("SmileRegressionModel"), model);
    }

    /**
     * Constructor with explicit UID.
     *
     * @param uid   the instance UID.
     * @param model the underlying SMILE regression model.
     */
    public SmileRegressionModel(String uid, smile.regression.Regression<double[]> model) {
        this(prepareUid(uid), model, true);
    }

    private static String prepareUid(String uid) {
        Objects.requireNonNull(uid, "uid cannot be null");
        INIT_UID.set(uid);
        return uid;
    }

    private SmileRegressionModel(String uid, smile.regression.Regression<double[]> model, boolean ignored) {
        super();
        this.uid = uid;
        this.model = Objects.requireNonNull(model, "model cannot be null");
        INIT_UID.remove();
    }

    @Override
    public String uid() {
        if (uid != null) {
            return uid;
        }
        String init = INIT_UID.get();
        return init != null ? init : Identifiable$.MODULE$.randomUID("SmileRegressionModel");
    }

    /**
     * Returns the underlying SMILE regression model.
     *
     * @return the SMILE regression model.
     */
    public smile.regression.Regression<double[]> model() {
        return model;
    }

    @Override
    public double predict(Vector features) {
        return model.predict(features.toArray());
    }

    @Override
    public SmileRegressionModel copy(ParamMap extra) {
        SmileRegressionModel copy = new SmileRegressionModel(uid, model);
        copyValues(copy, extra).setParent(parent());
        return copy;
    }

    @Override
    public MLWriter write() {
        return new SmileRegressionModelWriter(this);
    }

    /**
     * Returns an MLReader instance to load a persisted SmileRegressionModel.
     *
     * @return the reader.
     */
    public static MLReader<SmileRegressionModel> read() {
        return new SmileRegressionModelReader();
    }

    /**
     * Loads a persisted SmileRegressionModel from the given path.
     *
     * @param path the path.
     * @return the loaded model.
     */
    public static SmileRegressionModel load(String path) {
        return read().load(path);
    }

    private static class SmileRegressionModelWriter extends MLWriter {
        private final SmileRegressionModel instance;

        SmileRegressionModelWriter(SmileRegressionModel instance) {
            this.instance = instance;
        }

        @Override
        public void saveImpl(String path) {
            DefaultParamsWriter.saveMetadata(instance, path, sparkSession());
            Path modelPath = new Path(path, "model");
            try {
                FileSystem fs = modelPath.getFileSystem(sparkSession().sparkContext().hadoopConfiguration());
                try (FSDataOutputStream out = fs.create(modelPath);
                     ObjectOutputStream oos = new ObjectOutputStream(out)) {
                    oos.writeObject(instance.model());
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to save model to " + path, e);
            }
        }
    }

    private static class SmileRegressionModelReader extends MLReader<SmileRegressionModel> {
        @Override
        public SmileRegressionModel load(String path) {
            DefaultParamsReader.Metadata metadata = DefaultParamsReader.loadMetadata(path, sparkSession(), SmileRegressionModel.class.getName());
            Path modelPath = new Path(path, "model");
            smile.regression.Regression<double[]> model;
            try {
                FileSystem fs = modelPath.getFileSystem(sparkSession().sparkContext().hadoopConfiguration());
                try (FSDataInputStream in = fs.open(modelPath);
                     ObjectInputStream ois = new ObjectInputStream(in)) {
                    @SuppressWarnings("unchecked")
                    var loaded = (smile.regression.Regression<double[]>) ois.readObject();
                    model = loaded;
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to load model from " + path, e);
            }

            SmileRegressionModel modelInstance = new SmileRegressionModel(metadata.uid(), model);
            metadata.getAndSetParams(modelInstance, metadata.getAndSetParams$default$2());
            return modelInstance;
        }
    }
}
