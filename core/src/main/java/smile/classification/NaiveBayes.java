/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.classification;

import smile.math.MathEx;
import smile.stat.distribution.Distribution;
import smile.util.IntSet;

/**
 * Naive Bayes classifier. A naive Bayes classifier is a simple probabilistic
 * classifier based on applying Bayes' theorem with strong (naive) independence
 * assumptions. Depending on the precise nature of the probability model, naive
 * Bayes classifiers can be trained very efficiently in a supervised learning
 * setting.
 * <p>
 * For a general purpose naive Bayes classifier without any assumptions
 * about the underlying distribution of each variable, we don't provide
 * a learning method to infer the variable distributions from the training data.
 * Instead, the users can fit any appropriate distributions on the data by
 * themselves with various {@link Distribution} classes. Although the {@link #predict}
 * method takes an array of double values as a general form of independent variables,
 * the users are free to use any discrete distributions to model categorical or
 * ordinal random variables.
 *
 * @see Distribution
 * @see LDA
 * @see QDA
 * @see RDA
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Christopher D. Manning, Prabhakar Raghavan, and Hinrich Schutze. Introduction to Information Retrieval, Chapter 13, 2009.</li>
 * <li> Kevin P. Murphy. Machina Learning A Probability Perspective, Chapter 3, 2012.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class NaiveBayes implements SoftClassifier<double[]> {
    private static final long serialVersionUID = 2L;

    /**
     * The number of classes.
     */
    private int k;
    /**
     * The number of independent variables.
     */
    private int p;
    /**
     * The priori probability of each class.
     */
    private double[] priori;
    /**
     * The conditional distribution for general purpose naive Bayes classifier.
     */
    private Distribution[][] prob;
    /**
     * The class label encoder.
     */
    private IntSet labels;

    /**
     * Constructor of general naive Bayes classifier.
     *
     * @param priori the priori probability of each class.
     * @param condprob the conditional distribution of each variable in
     * each class. In particular, condprob[i][j] is the conditional
     * distribution P(x<sub>j</sub> | class i).
     */
    public NaiveBayes(double[] priori, Distribution[][] condprob) {
        this(priori, condprob, IntSet.of(priori.length));
    }

    /**
     * Constructor of general naive Bayes classifier.
     * 
     * @param priori the priori probability of each class.
     * @param condprob the conditional distribution of each variable in
     * each class. In particular, condprob[i][j] is the conditional
     * distribution P(x<sub>j</sub> | class i).
     * @param labels class labels
     */
    public NaiveBayes(double[] priori, Distribution[][] condprob, IntSet labels) {
        if (priori.length != condprob.length) {
            throw new IllegalArgumentException("The number of priori probabilities and that of the classes are not same.");
        }

        double sum = 0.0;
        for (double pr : priori) {
            if (pr <= 0.0 || pr >= 1.0) {
                throw new IllegalArgumentException("Invalid priori probability: " + pr);
            }
            sum += pr;
        }

        if (Math.abs(sum - 1.0) > 1E-5) {
            throw new IllegalArgumentException("The sum of priori probabilities is not one: " + sum);
        }

        this.k = priori.length;
        this.p = condprob[0].length;
        this.priori = priori;
        this.prob = condprob;
        this.labels = labels;
    }

    /**
     * Returns a priori probabilities.
     */
    public double[] priori() {
        return priori;
    }

    /**
     * Predict the class of an instance.
     * 
     * @param x the instance to be classified.
     * @return the predicted class label.
     */
    @Override
    public int predict(double[] x) {
        return predict(x, new double[k]);
    }

    /**
     * Predict the class of an instance.
     * 
     * @param x the instance to be classified.
     * @param posteriori the array to store a posteriori probabilities on output.
     * @return the predicted class label.
     */
    @Override
    public int predict(double[] x, double[] posteriori) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d", x.length));
        }

        for (int i = 0; i < k; i++) {
            double logprob = Math.log(priori[i]);

            for (int j = 0; j < p; j++) {
                logprob += prob[i][j].logp(x[j]);
            }

            posteriori[i] = logprob;
        }

        double Z = 0.0;
        double max = MathEx.max(posteriori);
        for (int i = 0; i < k; i++) {
            posteriori[i] = Math.exp(posteriori[i] - max);
            Z += posteriori[i];
        }

        for (int i = 0; i < k; i++) {
            posteriori[i] /= Z;
        }

        return labels.valueOf(MathEx.whichMax(posteriori));
    }
}
