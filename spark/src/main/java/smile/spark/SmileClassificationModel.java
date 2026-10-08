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
import org.apache.spark.ml.classification.ClassificationModel;
import org.apache.spark.ml.linalg.Vector;
import org.apache.spark.ml.linalg.Vectors;
import org.apache.spark.ml.param.ParamMap;
import org.apache.spark.ml.util.DefaultParamsReader;
import org.apache.spark.ml.util.DefaultParamsWriter;
import org.apache.spark.ml.util.Identifiable$;
import org.apache.spark.ml.util.MLReader;
import org.apache.spark.ml.util.MLWritable;
import org.apache.spark.ml.util.MLWriter;

/**
 * A Spark ML {@link org.apache.spark.ml.Model} produced by {@link SmileClassifier}.
 *
 * @author Haifeng Li
 */
public class SmileClassificationModel
        extends ClassificationModel<Vector, SmileClassificationModel>
        implements MLWritable {

    private static final ThreadLocal<String> INIT_UID = new ThreadLocal<>();

    private final String uid;
    private final int numClasses;
    private final smile.classification.Classifier<double[]> model;

    /**
     * Constructor with a default random UID.
     *
     * @param numClasses the number of target classes.
     * @param model      the underlying SMILE classifier.
     */
    public SmileClassificationModel(int numClasses, smile.classification.Classifier<double[]> model) {
        this(Identifiable$.MODULE$.randomUID("SmileClassificationModel"), numClasses, model);
    }

    /**
     * Constructor with explicit UID.
     *
     * @param uid        the instance UID.
     * @param numClasses the number of target classes.
     * @param model      the underlying SMILE classifier.
     */
    public SmileClassificationModel(String uid, int numClasses, smile.classification.Classifier<double[]> model) {
        this(prepareUid(uid), numClasses, model, true);
    }

    private static String prepareUid(String uid) {
        Objects.requireNonNull(uid, "uid cannot be null");
        INIT_UID.set(uid);
        return uid;
    }

    private SmileClassificationModel(String uid, int numClasses, smile.classification.Classifier<double[]> model, boolean ignored) {
        super();
        this.uid = uid;
        this.numClasses = numClasses;
        this.model = Objects.requireNonNull(model, "model cannot be null");
        INIT_UID.remove();
    }

    @Override
    public String uid() {
        if (uid != null) {
            return uid;
        }
        String init = INIT_UID.get();
        return init != null ? init : Identifiable$.MODULE$.randomUID("SmileClassificationModel");
    }

    @Override
    public int numClasses() {
        return numClasses;
    }

    /**
     * Returns the underlying SMILE classifier.
     *
     * @return the SMILE classifier.
     */
    public smile.classification.Classifier<double[]> model() {
        return model;
    }

    @Override
    public Vector predictRaw(Vector features) {
        double[] x = features.toArray();
        double[] posteriori = new double[numClasses];
        if (model.isSoft()) {
            model.predict(x, posteriori);
        } else {
            int y = model.predict(x);
            if (y >= 0 && y < numClasses) {
                posteriori[y] = 1.0;
            }
        }
        return Vectors.dense(posteriori);
    }

    @Override
    public SmileClassificationModel copy(ParamMap extra) {
        SmileClassificationModel copy = new SmileClassificationModel(uid, numClasses, model);
        copyValues(copy, extra).setParent(parent());
        return copy;
    }

    @Override
    public MLWriter write() {
        return new SmileClassificationModelWriter(this);
    }

    /**
     * Returns an MLReader instance to load a persisted SmileClassificationModel.
     *
     * @return the reader.
     */
    public static MLReader<SmileClassificationModel> read() {
        return new SmileClassificationModelReader();
    }

    /**
     * Loads a persisted SmileClassificationModel from the given path.
     *
     * @param path the path.
     * @return the loaded model.
     */
    public static SmileClassificationModel load(String path) {
        return read().load(path);
    }

    private static class SmileClassificationModelWriter extends MLWriter {
        private final SmileClassificationModel instance;

        SmileClassificationModelWriter(SmileClassificationModel instance) {
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
                    oos.writeInt(instance.numClasses());
                    oos.writeObject(instance.model());
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to save model to " + path, e);
            }
        }
    }

    private static class SmileClassificationModelReader extends MLReader<SmileClassificationModel> {
        @Override
        public SmileClassificationModel load(String path) {
            DefaultParamsReader.Metadata metadata = DefaultParamsReader.loadMetadata(path, sparkSession(), SmileClassificationModel.class.getName());
            Path modelPath = new Path(path, "model");
            int numClasses;
            smile.classification.Classifier<double[]> model;
            try {
                FileSystem fs = modelPath.getFileSystem(sparkSession().sparkContext().hadoopConfiguration());
                try (FSDataInputStream in = fs.open(modelPath);
                     ObjectInputStream ois = new ObjectInputStream(in)) {
                    numClasses = ois.readInt();
                    @SuppressWarnings("unchecked")
                    var loaded = (smile.classification.Classifier<double[]>) ois.readObject();
                    model = loaded;
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to load model from " + path, e);
            }

            SmileClassificationModel modelInstance = new SmileClassificationModel(metadata.uid(), numClasses, model);
            metadata.getAndSetParams(modelInstance, metadata.getAndSetParams$default$2());
            return modelInstance;
        }
    }
}
