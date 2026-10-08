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
import java.util.List;
import java.util.Properties;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.sql.SparkSession;
import smile.classification.Classifier;
import smile.classification.DataFrameClassifier;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.regression.DataFrameRegression;
import smile.regression.Regression;
import smile.validation.ClassificationValidation;
import smile.validation.ClassificationValidations;
import smile.validation.CrossValidation;
import smile.validation.RegressionValidation;
import smile.validation.RegressionValidations;

/**
 * Distributed hyperparameter optimization (HPO) across an Apache Spark cluster.
 *
 * <p>The training data is broadcast once to executors, and hyperparameter configurations
 * are evaluated in parallel using either k-fold cross validation or a held-out test split.
 *
 * @author Haifeng Li
 */
public final class SparkHPO {

    private SparkHPO() {
    }

    /**
     * Functional interface for training a parameterized classifier on array samples.
     *
     * @param <T> the sample type.
     * @param <M> the model type.
     */
    @FunctionalInterface
    public interface ClassificationTrainer<T, M extends Classifier<T>> extends Serializable {
        /**
         * Trains a model for a given hyperparameter configuration.
         *
         * @param x     the training samples.
         * @param y     the training labels.
         * @param props the hyperparameter configuration.
         * @return the fitted model.
         */
        M fit(T[] x, int[] y, Properties props);
    }

    /**
     * Functional interface for training a parameterized DataFrame classifier.
     *
     * @param <M> the model type.
     */
    @FunctionalInterface
    public interface DataFrameClassificationTrainer<M extends DataFrameClassifier> extends Serializable {
        /**
         * Trains a model for a given hyperparameter configuration.
         *
         * @param formula the model formula.
         * @param data    the training DataFrame.
         * @param props   the hyperparameter configuration.
         * @return the fitted model.
         */
        M fit(Formula formula, DataFrame data, Properties props);
    }

    /**
     * Functional interface for training a parameterized regression model on array samples.
     *
     * @param <T> the sample type.
     * @param <M> the model type.
     */
    @FunctionalInterface
    public interface RegressionTrainer<T, M extends Regression<T>> extends Serializable {
        /**
         * Trains a model for a given hyperparameter configuration.
         *
         * @param x     the training samples.
         * @param y     the response values.
         * @param props the hyperparameter configuration.
         * @return the fitted model.
         */
        M fit(T[] x, double[] y, Properties props);
    }

    /**
     * Functional interface for training a parameterized DataFrame regression model.
     *
     * @param <M> the model type.
     */
    @FunctionalInterface
    public interface DataFrameRegressionTrainer<M extends DataFrameRegression> extends Serializable {
        /**
         * Trains a model for a given hyperparameter configuration.
         *
         * @param formula the model formula.
         * @param data    the training DataFrame.
         * @param props   the hyperparameter configuration.
         * @return the fitted model.
         */
        M fit(Formula formula, DataFrame data, Properties props);
    }

    private record ArrayData<T>(T[] x, int[] y) implements Serializable {}
    private record FormulaData(Formula formula, DataFrame data) implements Serializable {}
    private record ArraySplitData<T>(T[] x, int[] y, T[] testx, int[] testy) implements Serializable {}
    private record FormulaSplitData(Formula formula, DataFrame train, DataFrame test) implements Serializable {}
    private record ArrayRegData<T>(T[] x, double[] y) implements Serializable {}
    private record ArrayRegSplitData<T>(T[] x, double[] y, T[] testx, double[] testy) implements Serializable {}

    /**
     * Distributed cross-validation for classification on array data.
     *
     * @param spark          the Spark session.
     * @param k              the number of folds.
     * @param x              the sample array.
     * @param y              the label array.
     * @param configurations the list of hyperparameter configurations.
     * @param trainer        the trainer function.
     * @param <T>            the sample type.
     * @param <M>            the model type.
     * @return cross-validation results per configuration.
     */
    public static <T, M extends Classifier<T>> List<ClassificationValidations<M>> classification(
            SparkSession spark, int k, T[] x, int[] y,
            List<Properties> configurations,
            ClassificationTrainer<T, M> trainer) {
        JavaSparkContext jsc = new JavaSparkContext(spark.sparkContext());
        Broadcast<ArrayData<T>> bc = jsc.broadcast(new ArrayData<>(x, y));
        try {
            return jsc.parallelize(configurations).map(prop -> {
                ArrayData<T> d = bc.value();
                return CrossValidation.classification(k, d.x(), d.y(), (tx, ty) -> trainer.fit(tx, ty, prop));
            }).collect();
        } finally {
            bc.destroy();
        }
    }

    /**
     * Distributed cross-validation for classification on DataFrame.
     *
     * @param spark          the Spark session.
     * @param k              the number of folds.
     * @param formula        the model formula.
     * @param data           the DataFrame.
     * @param configurations the list of hyperparameter configurations.
     * @param trainer        the trainer function.
     * @param <M>            the model type.
     * @return cross-validation results per configuration.
     */
    public static <M extends DataFrameClassifier> List<ClassificationValidations<M>> classification(
            SparkSession spark, int k, Formula formula, DataFrame data,
            List<Properties> configurations,
            DataFrameClassificationTrainer<M> trainer) {
        JavaSparkContext jsc = new JavaSparkContext(spark.sparkContext());
        Broadcast<FormulaData> bc = jsc.broadcast(new FormulaData(formula, data));
        try {
            return jsc.parallelize(configurations).map(prop -> {
                FormulaData d = bc.value();
                return CrossValidation.classification(k, d.formula(), d.data(), (f, df) -> trainer.fit(f, df, prop));
            }).collect();
        } finally {
            bc.destroy();
        }
    }

    /**
     * Distributed train/test validation for classification on array data.
     *
     * @param spark          the Spark session.
     * @param x              training samples.
     * @param y              training labels.
     * @param testx          test samples.
     * @param testy          test labels.
     * @param configurations the list of hyperparameter configurations.
     * @param trainer        the trainer function.
     * @param <T>            the sample type.
     * @param <M>            the model type.
     * @return validation results per configuration.
     */
    public static <T, M extends Classifier<T>> List<ClassificationValidation<M>> classification(
            SparkSession spark, T[] x, int[] y, T[] testx, int[] testy,
            List<Properties> configurations,
            ClassificationTrainer<T, M> trainer) {
        JavaSparkContext jsc = new JavaSparkContext(spark.sparkContext());
        Broadcast<ArraySplitData<T>> bc = jsc.broadcast(new ArraySplitData<>(x, y, testx, testy));
        try {
            return jsc.parallelize(configurations).map(prop -> {
                ArraySplitData<T> d = bc.value();
                return ClassificationValidation.of(d.x(), d.y(), d.testx(), d.testy(), (tx, ty) -> trainer.fit(tx, ty, prop));
            }).collect();
        } finally {
            bc.destroy();
        }
    }

    /**
     * Distributed train/test validation for classification on DataFrame.
     *
     * @param spark          the Spark session.
     * @param formula        the model formula.
     * @param train          the training DataFrame.
     * @param test           the validation DataFrame.
     * @param configurations the list of hyperparameter configurations.
     * @param trainer        the trainer function.
     * @param <M>            the model type.
     * @return validation results per configuration.
     */
    public static <M extends DataFrameClassifier> List<ClassificationValidation<M>> classification(
            SparkSession spark, Formula formula, DataFrame train, DataFrame test,
            List<Properties> configurations,
            DataFrameClassificationTrainer<M> trainer) {
        JavaSparkContext jsc = new JavaSparkContext(spark.sparkContext());
        Broadcast<FormulaSplitData> bc = jsc.broadcast(new FormulaSplitData(formula, train, test));
        try {
            return jsc.parallelize(configurations).map(prop -> {
                FormulaSplitData d = bc.value();
                return ClassificationValidation.of(d.formula(), d.train(), d.test(), (f, df) -> trainer.fit(f, df, prop));
            }).collect();
        } finally {
            bc.destroy();
        }
    }

    /**
     * Distributed cross-validation for regression on array data.
     *
     * @param spark          the Spark session.
     * @param k              the number of folds.
     * @param x              the sample array.
     * @param y              the continuous target values.
     * @param configurations the list of hyperparameter configurations.
     * @param trainer        the trainer function.
     * @param <T>            the sample type.
     * @param <M>            the model type.
     * @return cross-validation results per configuration.
     */
    public static <T, M extends Regression<T>> List<RegressionValidations<M>> regression(
            SparkSession spark, int k, T[] x, double[] y,
            List<Properties> configurations,
            RegressionTrainer<T, M> trainer) {
        JavaSparkContext jsc = new JavaSparkContext(spark.sparkContext());
        Broadcast<ArrayRegData<T>> bc = jsc.broadcast(new ArrayRegData<>(x, y));
        try {
            return jsc.parallelize(configurations).map(prop -> {
                ArrayRegData<T> d = bc.value();
                return CrossValidation.regression(k, d.x(), d.y(), (tx, ty) -> trainer.fit(tx, ty, prop));
            }).collect();
        } finally {
            bc.destroy();
        }
    }

    /**
     * Distributed cross-validation for regression on DataFrame.
     *
     * @param spark          the Spark session.
     * @param k              the number of folds.
     * @param formula        the model formula.
     * @param data           the DataFrame.
     * @param configurations the list of hyperparameter configurations.
     * @param trainer        the trainer function.
     * @param <M>            the model type.
     * @return cross-validation results per configuration.
     */
    public static <M extends DataFrameRegression> List<RegressionValidations<M>> regression(
            SparkSession spark, int k, Formula formula, DataFrame data,
            List<Properties> configurations,
            DataFrameRegressionTrainer<M> trainer) {
        JavaSparkContext jsc = new JavaSparkContext(spark.sparkContext());
        Broadcast<FormulaData> bc = jsc.broadcast(new FormulaData(formula, data));
        try {
            return jsc.parallelize(configurations).map(prop -> {
                FormulaData d = bc.value();
                return CrossValidation.regression(k, d.formula(), d.data(), (f, df) -> trainer.fit(f, df, prop));
            }).collect();
        } finally {
            bc.destroy();
        }
    }

    /**
     * Distributed train/test validation for regression on array data.
     *
     * @param spark          the Spark session.
     * @param x              training samples.
     * @param y              training continuous target values.
     * @param testx          test samples.
     * @param testy          test targets.
     * @param configurations the list of hyperparameter configurations.
     * @param trainer        the trainer function.
     * @param <T>            the sample type.
     * @param <M>            the model type.
     * @return validation results per configuration.
     */
    public static <T, M extends Regression<T>> List<RegressionValidation<M>> regression(
            SparkSession spark, T[] x, double[] y, T[] testx, double[] testy,
            List<Properties> configurations,
            RegressionTrainer<T, M> trainer) {
        JavaSparkContext jsc = new JavaSparkContext(spark.sparkContext());
        Broadcast<ArrayRegSplitData<T>> bc = jsc.broadcast(new ArrayRegSplitData<>(x, y, testx, testy));
        try {
            return jsc.parallelize(configurations).map(prop -> {
                ArrayRegSplitData<T> d = bc.value();
                return RegressionValidation.of(d.x(), d.y(), d.testx(), d.testy(), (tx, ty) -> trainer.fit(tx, ty, prop));
            }).collect();
        } finally {
            bc.destroy();
        }
    }

    /**
     * Distributed train/test validation for regression on DataFrame.
     *
     * @param spark          the Spark session.
     * @param formula        the model formula.
     * @param train          the training DataFrame.
     * @param test           the validation DataFrame.
     * @param configurations the list of hyperparameter configurations.
     * @param trainer        the trainer function.
     * @param <M>            the model type.
     * @return validation results per configuration.
     */
    public static <M extends DataFrameRegression> List<RegressionValidation<M>> regression(
            SparkSession spark, Formula formula, DataFrame train, DataFrame test,
            List<Properties> configurations,
            DataFrameRegressionTrainer<M> trainer) {
        JavaSparkContext jsc = new JavaSparkContext(spark.sparkContext());
        Broadcast<FormulaSplitData> bc = jsc.broadcast(new FormulaSplitData(formula, train, test));
        try {
            return jsc.parallelize(configurations).map(prop -> {
                FormulaSplitData d = bc.value();
                return RegressionValidation.of(d.formula(), d.train(), d.test(), (f, df) -> trainer.fit(f, df, prop));
            }).collect();
        } finally {
            bc.destroy();
        }
    }
}
