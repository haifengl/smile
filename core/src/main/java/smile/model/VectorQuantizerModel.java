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
package smile.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Properties;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.transform.Transform;
import smile.data.type.StructType;
import smile.feature.importance.KernelSHAP;
import smile.math.MathEx;
import smile.vq.BIRCH;
import smile.vq.GrowingNeuralGas;
import smile.vq.NeuralGas;
import smile.vq.NeuralMap;
import smile.vq.SOM;
import smile.vq.VectorQuantizer;

/**
 * The vector quantization model.
 *
 * @param algorithm the algorithm name.
 * @param schema the schema of input data (without response variable).
 * @param formula the model formula.
 * @param quantizer the vector quantizer model.
 * @param transform the optional feature transformation applied before quantization.
 * @param tags the model metadata tags.
 *
 * @author Haifeng Li
 */
public record VectorQuantizerModel(String algorithm,
                                   StructType schema,
                                   Formula formula,
                                   VectorQuantizer quantizer,
                                   Transform transform,
                                   Properties tags) implements Model, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Converts an input tuple into a feature vector.
     */
    private double[] vectorize(Tuple x) {
        Tuple xt = x.schema().equals(schema) ? x : formula.x(x);
        if (transform != null) {
            xt = transform.apply(xt);
        }
        return xt.toArray(false, CategoricalEncoder.DUMMY);
    }

    /**
     * Quantizes an input tuple to its nearest prototype/code vector.
     *
     * @param x the input tuple.
     * @return the quantized code vector.
     */
    public double[] quantize(Tuple x) {
        return quantizer.quantize(vectorize(x));
    }

    /**
     * Quantizes a data frame of observations.
     *
     * @param data the input data frame.
     * @return the quantized code vectors.
     */
    public double[][] quantize(DataFrame data) {
        formula.bind(data.schema());
        return data.stream().map(this::quantize).toArray(double[][]::new);
    }

    @Override
    public boolean isOnline() {
        return true;
    }

    @Override
    public synchronized void update(Tuple x) {
        quantizer.update(vectorize(x));
    }

    @Override
    public synchronized void update(DataFrame data) {
        formula.bind(data.schema());
        int n = data.size();
        for (int i = 0; i < n; i++) {
            quantizer.update(vectorize(data.get(i)));
        }
    }

    @Override
    public Prediction infer(Tuple x, boolean probability, boolean explain) {
        return infer(x, probability, explain, null);
    }

    @Override
    public synchronized Prediction infer(Tuple x, boolean probability, boolean explain, KernelSHAP.Options options) {
        double[] v = vectorize(x);
        Object output;
        double[] centroid;

        switch (quantizer) {
            case SOM som -> {
                SOM.Neuron bmu = som.bmu(v);
                output = new int[]{bmu.i(), bmu.j()};
                centroid = bmu.w().clone();
            }
            case NeuralGas ng -> {
                NeuralGas.Neuron bmu = ng.bmu(v);
                output = bmu.i();
                centroid = bmu.w().clone();
            }
            case GrowingNeuralGas gng -> {
                int idx = gng.indexOf(v);
                output = idx;
                centroid = gng.neurons()[idx].w.clone();
            }
            case NeuralMap nm -> {
                int idx = nm.indexOf(v);
                output = idx;
                centroid = nm.neurons()[idx].w.clone();
            }
            case BIRCH birch -> {
                int idx = birch.indexOf(v);
                output = idx;
                centroid = birch.quantize(v);
            }
            default -> {
                output = 0;
                centroid = quantizer.quantize(v);
            }
        }

        double error = MathEx.distance(v, centroid);
        Explanations explanations = explain ? new Explanations("Not supported") : null;
        return new Prediction(output, null, centroid, error, explanations);
    }

    @Override
    public Prediction[] infer(DataFrame data, boolean probability, boolean explain) {
        return infer(data, probability, explain, null);
    }

    @Override
    public synchronized Prediction[] infer(DataFrame data, boolean probability, boolean explain, KernelSHAP.Options options) {
        formula.bind(data.schema());
        int n = data.size();
        Prediction[] responses = new Prediction[n];
        for (int i = 0; i < n; i++) {
            responses[i] = infer(data.get(i), probability, explain, options);
        }
        return responses;
    }
}
