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
package smile.hpo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import smile.math.MathEx;

/**
 * Hyperparameter configuration. A hyperparameter is a parameter whose value is set
 * before the learning process begins. By contrast, the values of other
 * parameters are derived via training.
 * <p>
 * Hyperparameters can be classified as model hyperparameters, that cannot
 * be inferred while fitting the machine to the training set because they
 * refer to the model selection task, or algorithm hyperparameters, that
 * in principle have no influence on the performance of the model but
 * affect the speed and quality of the learning process. For example,
 * the topology and size of a neural network are model hyperparameters,
 * while learning rate and mini-batch size are algorithm hyperparameters.
 * <p>
 * The below example shows how to tune the hyperparameters of random forest.
 * <pre>
 * {@code
 *    import smile.io.*;
 *    import smile.data.formula.Formula;
 *    import smile.validation.*;
 *    import smile.classification.RandomForest;
 *
 *    var hp = new Hyperparameters()
 *        .add("smile.random.forest.trees", 100) // a fixed value
 *        .add("smile.random.forest.mtry", new int[] {2, 3, 4}) // an array of values to choose
 *        .add("smile.random.forest.max.nodes", 100, 500, 50); // range [100, 500] with step 50
 *
 *    var train = Read.arff("data/weka/segment-challenge.arff");
 *    var test = Read.arff("data/weka/segment-test.arff");
 *    var formula = Formula.lhs("class");
 *    var testy = formula.y(test).toIntArray();
 *
 *    hp.grid().forEach(prop -&gt; {
 *        var model = RandomForest.fit(formula, train, prop);
 *        var pred = model.predict(test);
 *        System.out.println(prop);
 *        System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
 *        System.out.println(ConfusionMatrix.of(testy, pred));
 *    });
 * }
 * </pre>
 * @author Haifeng Li
 */
public class Hyperparameters {
    /** The set of parameters. */
    private final HashMap<String, Object> parameters = new HashMap<>();

    record KeyValue(String key, String value) {
    }

    record IntRange(int start, int end, int step) {
        public IntRange {
            if (start >= end) {
                throw new IllegalArgumentException(String.format("start = %d, end = %d", start, end));
            }

            if (step <= 0) {
                throw new IllegalArgumentException("step = " + step);
            }
        }

        public IntRange(int start, int end) {
            this(start, end, Math.max(1, (end-start)/10));
        }

        int[] toArray() {
            int n = (end - start) / step + 1;
            int[] a = new int[n];
            a[0] = start;
            for (int i = 1; i < n; i++) {
                a[i] = a[i-1] + step;
            }
            return a;
        }
    }

    record DoubleRange(double start, double end, double step) {
        public DoubleRange {
            if (start >= end) {
                throw new IllegalArgumentException(String.format("start = %f, end = %f", start, end));
            }

            if (step <= 0.0) {
                throw new IllegalArgumentException("step = " + step);
            }
        }

        public DoubleRange(double start, double end) {
            this(start, end, (end-start)/10);
        }

        double[] toArray() {
            double intervals = (end - start) / step;
            int n = (int) Math.ceil(intervals);
            if (intervals == n) n++;

            double[] a = new double[n];
            a[0] = start;
            for (int i = 1; i < n; i++) {
                a[i] = a[i-1] + step;
            }
            return a;
        }
    }

    /** Constructor. */
    public Hyperparameters() {

    }

    /**
     * Adds a parameter.
     *
     * @param name the parameter name.
     * @param value a fixed value of parameter.
     * @return this object.
     */
    public Hyperparameters add(String name, int value) {
        return add(name, new int[] {value});
    }

    /**
     * Adds a parameter.
     *
     * @param name the parameter name.
     * @param value a fixed value of parameter.
     * @return this object.
     */
    public Hyperparameters add(String name, double value) {
        return add(name, new double[] {value});
    }

    /**
     * Adds a parameter.
     *
     * @param name the parameter name.
     * @param value a fixed value of parameter.
     * @return this object.
     */
    public Hyperparameters add(String name, String value) {
        return add(name, new String[] {value});
    }

    /**
     * Adds a parameter.
     *
     * @param name the parameter name.
     * @param values an array of parameter values.
     * @return this object.
     */
    public Hyperparameters add(String name, int[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Empty array");
        }
        parameters.put(name, values);
        return this;
    }

    /**
     * Adds a parameter.
     *
     * @param name the parameter name.
     * @param values an array of parameter values.
     * @return this object.
     */
    public Hyperparameters add(String name, double[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Empty array");
        }
        parameters.put(name, values);
        return this;
    }

    /**
     * Adds a parameter.
     *
     * @param name the parameter name.
     * @param values an array of parameter values.
     * @return this object.
     */
    public Hyperparameters add(String name, String[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Empty array");
        }
        parameters.put(name, values);
        return this;
    }

    /**
     * Adds a parameter.
     *
     * @param name the parameter name.
     * @param start the start of value range (inclusive).
     * @param end the end of value range (inclusive).
     * @return this object.
     */
    public Hyperparameters add(String name, int start, int end) {
        parameters.put(name, new IntRange(start, end));
        return this;
    }

    /**
     * Adds a parameter.
     *
     * @param name the parameter name.
     * @param start the start of value range (inclusive).
     * @param end the end of value range (inclusive).
     * @param step the step size.
     * @return this object.
     */
    public Hyperparameters add(String name, int start, int end, int step) {
        parameters.put(name, new IntRange(start, end, step));
        return this;
    }

    /**
     * Adds a parameter.
     *
     * @param name the parameter name.
     * @param start the start of value range (inclusive).
     * @param end the end of value range (inclusive).
     * @return this object.
     */
    public Hyperparameters add(String name, double start, double end) {
        parameters.put(name, new DoubleRange(start, end));
        return this;
    }

    /**
     * Adds a parameter.
     *
     * @param name the parameter name.
     * @param start the start of value range (inclusive).
     * @param end the end of value range (inclusive).
     * @param step the step size.
     * @return this object.
     */
    public Hyperparameters add(String name, double start, double end, double step) {
        parameters.put(name, new DoubleRange(start, end, step));
        return this;
    }

    /**
     * Generates a stream of hyperparameters for random search.
     * @return the stream of hyperparameters for random search.
     */
    public Stream<Properties> random() {
        return Stream.generate(() -> {
            Properties params = new Properties();
            parameters.forEach((name, values) -> {
                switch (values) {
                    case int[] a -> {
                        int v = a.length == 1 ? a[0] : a[MathEx.randomInt(a.length)];
                        params.setProperty(name, String.valueOf(v));
                    }
                    case double[] a -> {
                        double v = a.length == 1 ? a[0] : a[MathEx.randomInt(a.length)];
                        params.setProperty(name, String.valueOf(v));
                    }
                    case String[] a -> {
                        String v = a.length == 1 ? a[0] : a[MathEx.randomInt(a.length)];
                        params.setProperty(name, v);
                    }
                    case IntRange range ->
                        params.setProperty(name, String.valueOf(MathEx.randomInt(range.start, range.end)));
                    case DoubleRange range ->
                        params.setProperty(name, String.valueOf(MathEx.random(range.start, range.end)));
                    case null, default -> throw new IllegalStateException("Unknown parameter type: " + values);
                }
            });
            return params;
        });
    }

    /**
     * Generates a stream of hyperparameters for grid search.
     * @return the stream of hyperparameters for grid search.
     */
    public Stream<Properties> grid() {
        ArrayList<Map.Entry<String, Object>> lists = new ArrayList<>(parameters.entrySet());

        // Extract each value of first parameter and add each to a new Properties.
        ArrayList<ArrayList<KeyValue>> combinations = new ArrayList<>();
        for(var pair : values(lists.getFirst())) {
            ArrayList<KeyValue> newList = new ArrayList<>();
            newList.add(pair);
            combinations.add(newList);
        }

        for(int i = 1; i < lists.size(); i++) {
            ArrayList<KeyValue> nextList = values(lists.get(i));
            ArrayList<ArrayList<KeyValue>> newCombinations = new ArrayList<>();
            for(var first : combinations) {
                for(var second: nextList) {
                    ArrayList<KeyValue> newList = new ArrayList<>(first);
                    newList.add(second);
                    newCombinations.add(newList);
                }
            }
            combinations = newCombinations;
        }

        return combinations.stream().map(list -> {
            Properties params = new Properties();
            list.forEach(p -> params.setProperty(p.key(), p.value()));
            return params;
        });
    }

    /** Returns the list of parameter values. */
    private ArrayList<KeyValue> values(Map.Entry<String, Object> parameter) {
        ArrayList<KeyValue> list = new ArrayList<>();
        String name = parameter.getKey();
        Object values = parameter.getValue();
        switch (values) {
            case int[] array -> {
                for (int value : array) {
                    list.add(new KeyValue(name, String.valueOf(value)));
                }
            }
            case double[] array -> {
                for (double value : array) {
                    list.add(new KeyValue(name, String.valueOf(value)));
                }
            }
            case String[] array -> {
                for (String value : array) {
                    list.add(new KeyValue(name, String.valueOf(value)));
                }
            }
            case IntRange range -> {
                for (int value : range.toArray()) {
                    list.add(new KeyValue(name, String.valueOf(value)));
                }
            }
            case DoubleRange range -> {
                for (double value : range.toArray()) {
                    list.add(new KeyValue(name, String.valueOf(value)));
                }
            }
            case null, default -> throw new IllegalStateException("Unknown parameter type: " + values);
        }

        return list;
    }
}
