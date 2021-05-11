/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.classification;

import java.util.List;
import java.util.function.BiFunction;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;

/**
 * Classification trait on DataFrame.
 *
 * @author Haifeng Li
 */
public interface DataFrameClassifier extends Classifier<Tuple> {
    /**
     * Returns the formula associated with the model.
     * @return the formula associated with the model.
     */
    Formula formula();

    /**
     * Returns the predictor schema.
     * @return the predictor schema.
     */
    StructType schema();

    /**
     * Predicts the class labels of a data frame.
     *
     * @param data the data frame.
     * @return the predicted class labels.
     */
    default int[] predict(DataFrame data) {
        // Binds the formula to the data frame's schema in case that
        // it is different from that of training data.
        formula().bind(data.schema());
        return data.stream().mapToInt(this::predict).toArray();
    }

    /**
     * Predicts the class labels of a dataset.
     *
     * @param data the data frame.
     * @param posteriori an empty list to store a posteriori probabilities on output.
     * @return the predicted class labels.
     */
    default int[] predict(DataFrame data, List<double[]> posteriori) {
        // Binds the formula to the data frame's schema in case that
        // it is different from that of training data.
        formula().bind(data.schema());

        int k = numClasses();
        return data.stream().mapToInt(xi -> {
            double[] prob = new double[k];
            posteriori.add(prob);
            return predict(xi, prob);
        }).toArray();
    }

    /**
     * Fits a vector classifier on data frame.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @return the model.
     */
    static DataFrameClassifier of(Formula formula, DataFrame data, BiFunction<double[][], int[], Classifier<double[]>> trainer) {
        DataFrame X = formula.x(data);
        StructType schema = X.schema();
        double[][] x = X.toArray(false, CategoricalEncoder.DUMMY);
        int[] y = formula.y(data).toIntArray();
        Classifier<double[]> model = trainer.apply(x, y);

        return new DataFrameClassifier() {
            @Override
            public Formula formula() {
                return formula;
            }

            @Override
            public StructType schema() {
                return schema;
            }

            @Override
            public int numClasses() {
                return model.numClasses();
            }

            @Override
            public int[] classes() {
                return model.classes();
            }

            @Override
            public int predict(Tuple x) {
                return model.predict(formula.x(x).toArray());
            }
        };
    }
}
