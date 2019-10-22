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

import smile.data.measure.DiscreteMeasure;
import smile.math.MathEx;
import smile.util.SparseArray;
import smile.stat.distribution.Distribution;

/**
 * Naive Bayes classifier for document classification in NLP.
 * A naive Bayes classifier is a simple probabilistic
 * classifier based on applying Bayes' theorem with strong (naive) independence
 * assumptions. Depending on the precise nature of the probability model, naive
 * Bayes classifiers can be trained very efficiently in a supervised learning
 * setting.
 * <p>
 * In spite of their naive design and apparently over-simplified assumptions,
 * naive Bayes classifiers have worked quite well in many complex real-world
 * situations and are very popular in Natural Language Processing (NLP).
 * <p>
 * For document classification in NLP, there are two major different ways we can set
 * up an naive Bayes classifier: multinomial model and Bernoulli model. The
 * multinomial model generates one term from the vocabulary in each position
 * of the document. The multivariate Bernoulli model or Bernoulli model
 * generates an indicator for each term of the vocabulary, either indicating
 * presence of the term in the document or indicating absence.
 * Of the two models, the Bernoulli model is particularly sensitive to noise
 * features. A Bernoulli naive Bayes classifier requires some form of feature
 * selection or else its accuracy will be low.
 * <p>
 * The different generation models imply different estimation strategies and
 * different classification rules. The Bernoulli model estimates as the
 * fraction of documents of class that contain term. In contrast, the
 * multinomial model estimates as the fraction of tokens or fraction of
 * positions in documents of class that contain term. When classifying a
 * test document, the Bernoulli model uses binary occurrence information,
 * ignoring the number of occurrences, whereas the multinomial model keeps
 * track of multiple occurrences. As a result, the Bernoulli model typically
 * makes many mistakes when classifying long documents. However, it was reported
 * that the Bernoulli model works better in sentiment analysis.
 * <p>
 * The models also differ in how non-occurring terms are used in classification.
 * They do not affect the classification decision in the multinomial model;
 * but in the Bernoulli model the probability of nonoccurrence is factored
 * in when computing. This is because only the Bernoulli model models
 * absence of terms explicitly.
 * <p>
 * A third setting is Polya Urn model which simply
 * add twice for what is seen in training data instead of one time.
 * See reference for more detail.
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
public class DiscreteNaiveBayes implements OnlineClassifier<int[]>, SoftClassifier<int[]> {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DiscreteMeasure.class);

    /**
     * The generation models of naive Bayes classifier.
     * For document classification in NLP, there are two different ways we can set
     * up an naive Bayes classifier: multinomial model and Bernoulli model. The
     * multinomial model generates one term from the vocabulary in each position
     * of the document. The multivariate Bernoulli model or Bernoulli model
     * generates an indicator for each term of the vocabulary, either indicating
     * presence of the term in the document or indicating absence.
     */
    public enum Model {
        /**
         * The document multinomial model generates one term from the vocabulary
         * in each position of the document.
         */
        MULTINOMIAL,

        /**
         * The document Bernoulli model generates an indicator for each term of
         * the vocabulary, either indicating presence of the term in the
         * document or indicating absence.
         */
        BERNOULLI,

        /**
         * The document Polya Urn model is similar to MULTINOMIAL but different
         * in the conditional probability update during learning.
         * It simply add twice for what is seen in training data instead of one time.
         */
        POLYAURN
    }

    /**
     * Fudge to keep nonzero.
     */
    private static final double EPSILON = 1E-20;
    /**
     * The generation model of naive Bayes.
     */
    private Model model;
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
     * Amount of add-k smoothing of evidence. By default, we use add-one or
     * Laplace smoothing, which simply adds one to each count to eliminate zeros.
     * Add-one smoothing can be interpreted as a uniform prior (each term occurs
     * once for each class) that is then updated as evidence from the training
     * data comes in.
     */
    private double sigma;
    /**
     * If true, don't update the priori during learning.
     */
    private boolean fixedPriori;
    /**
     * The total number of instances.
     */
    private int n;
    /**
     * The number of instances in each class.
     */
    private int[] nc;
    /**
     * The number of terms of each class.
     */
    private int[] nt;
    /**
     * The number of each term in each class.
     */
    private int[][] ntc;
    /**
     * The log conditional probabilities for document classification.
     */
    private double[][] condprob;
    /**
     * The class label encoder.
     */
    private ClassLabel labels;

    /**
     * Constructor of naive Bayes classifier for document classification.
     * The priori probability of each class will be learned from data.
     * By default, we use add-one/Laplace smoothing, which simply adds one
     * to each count to eliminate zeros. Add-one smoothing can be interpreted
     * as a uniform prior (each term occurs once for each class) that is
     * then updated as evidence from the training data comes in.
     *
     * @param model the generation model of naive Bayes classifier.
     * @param k the number of classes.
     * @param p the dimensionality of input space.
     */
    public DiscreteNaiveBayes(Model model, int k, int p) {
        this(model, k, p, 1.0, ClassLabel.of(k));
    }

    /**
     * Constructor of naive Bayes classifier for document classification.
     * The priori probability of each class will be learned from data.
     * Add-k smoothing.
     *
     * @param model the generation model of naive Bayes classifier.
     * @param k the number of classes.
     * @param p the dimensionality of input space.
     * @param sigma the prior count of add-k smoothing of evidence.
     * @param labels class labels
     */
    public DiscreteNaiveBayes(Model model, int k, int p, double sigma, ClassLabel labels) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of classes: " + k);
        }

        if (p <= 0) {
            throw new IllegalArgumentException("Invalid dimension: " + p);
        }

        if (sigma < 0) {
            throw new IllegalArgumentException("Invalid add-k smoothing parameter: " + sigma);
        }

        this.model = model;
        this.k = k;
        this.p = p;
        this.sigma = sigma;
        this.labels = labels;

        fixedPriori = false;
        priori = new double[k];

        n = 0;
        nc = new int[k];
        nt = new int[k];
        ntc = new int[k][p];
        condprob = new double[k][p];
    }

    /**
     * Constructor of naive Bayes classifier for document classification.
     * By default, we use add-one/Laplace smoothing, which simply adds one
     * to each count to eliminate zeros. Add-one smoothing can be interpreted
     * as a uniform prior (each term occurs once for each class) that is
     * then updated as evidence from the training data comes in.
     *
     * @param model the generation model of naive Bayes classifier.
     * @param priori the priori probability of each class.
     * @param p the dimensionality of input space.
     */
    public DiscreteNaiveBayes(Model model, double[] priori, int p) {
        this(model, priori, p, 1.0, ClassLabel.of(priori.length));
    }

    /**
     * Constructor of naive Bayes classifier for document classification.
     * Add-k smoothing.
     *
     * @param model the generation model of naive Bayes classifier.
     * @param priori the priori probability of each class.
     * @param p the dimensionality of input space.
     * @param sigma the prior count of add-k smoothing of evidence.
     */
    public DiscreteNaiveBayes(Model model, double[] priori, int p, double sigma, ClassLabel labels) {
        if (p <= 0) {
            throw new IllegalArgumentException("Invalid dimension: " + p);
        }

        if (sigma < 0) {
            throw new IllegalArgumentException("Invalid add-k smoothing parameter: " + sigma);
        }

        if (priori.length < 2) {
            throw new IllegalArgumentException("Invalid number of classes: " + priori.length);
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

        this.model = model;
        this.k = priori.length;
        this.p = p;
        this.sigma = sigma;
        this.labels = labels;

        this.priori = priori;
        fixedPriori = true;

        n = 0;
        nc = new int[k];
        nt = new int[k];
        ntc = new int[k][p];
        condprob = new double[k][p];
    }

    /**
     * Returns a priori probabilities.
     */
    public double[] priori() {
        return priori;
    }

    /**
     * Online learning of naive Bayes classifier on a sequence,
     * which is modeled as a bag of words. Note that this method is NOT
     * applicable for naive Bayes classifier with general generation model.
     *
     * @param x training instance.
     * @param y training label.
     */
    @Override
    public void update(int[] x, int y) {
        if (!isGoodInstance(x)) {
            logger.info("Skip updating the model with a sample without any feature word");
            return;
        }

        y = labels.id(y);
        switch (model) {
            case MULTINOMIAL:
                for (int i = 0; i < p; i++) {
                    ntc[y][i] += x[i];
                    nt[y] += x[i];
                }
                break;

            case POLYAURN:
                for (int i = 0; i < p; i++) {
                    ntc[y][i] += x[i] * 2;
                    nt[y] += x[i] * 2;
                }
                break;

            case BERNOULLI:
                for (int i = 0; i < p; i++) {
                    if (x[i] > 0) {
                        ntc[y][i]++;
                    }
                }
                break;
        }

        n++;
        nc[y]++;
        update();
    }

    /**
     * Online learning of naive Bayes classifier on a sequence,
     * which is modeled as a bag of words. Note that this method is NOT
     * applicable for naive Bayes classifier with general generation model.
     *
     * @param x training instance in sparse format.
     * @param y training label.
     */
    public void update(SparseArray x, int y) {
        if (!isGoodInstance(x)) {
            logger.info("Skip updating the model with a sample without any feature word");
            return;
        }

        y = labels.id(y);
        switch (model) {
            case MULTINOMIAL:
                for (SparseArray.Entry e : x) {
                    ntc[y][e.i] += e.x;
                    nt[y] += e.x;
                }
                break;

            case POLYAURN:
                for (SparseArray.Entry e : x) {
                    ntc[y][e.i] += e.x * 2;
                    nt[y] += e.x * 2;
                }
                break;

            case BERNOULLI:
                for (SparseArray.Entry e : x) {
                    if (e.x > 0) {
                        ntc[y][e.i]++;
                    }
                }
                break;
        }

        n++;
        nc[y]++;
        update();
    }

    /**
     * Online learning of naive Bayes classifier on sequences,
     * which are modeled as a bag of words. Note that this method is NOT
     * applicable for naive Bayes classifier with general generation model.
     *
     * @param x training instances.
     * @param y training labels.
     */
    @Override
    public void update(int[][] x, int[] y) {
        switch (model) {
            case MULTINOMIAL:
                for (int i = 0; i < x.length; i++) {
                    if (!isGoodInstance(x[i])) {
                        logger.info("Skip updating the model with a sample without any feature word");
                        continue;
                    }

                    int yi = labels.id(y[i]);
                    for (int j = 0; j < p; j++) {
                        ntc[yi][j] += x[i][j];
                        nt[yi] += x[i][j];
                    }

                    n++;
                    nc[yi]++;
                }
                break;

            case POLYAURN:
                for (int i = 0; i < x.length; i++) {
                    if (!isGoodInstance(x[i])) {
                        logger.info("Skip updating the model with a sample without any feature word");
                        continue;
                    }

                    int yi = labels.id(y[i]);
                    for (int j = 0; j < p; j++) {
                        ntc[yi][j] += x[i][j] * 2;
                        nt[yi] += x[i][j] * 2;
                    }

                    n++;
                    nc[yi]++;
                }
                break;

            case BERNOULLI:
                for (int i = 0; i < x.length; i++) {
                    if (!isGoodInstance(x[i])) {
                        logger.info("Skip updating the model with a sample without any feature word");
                        continue;
                    }

                    int yi = labels.id(y[i]);
                    for (int j = 0; j < p; j++) {
                        if (x[i][j] > 0) {
                            ntc[yi][j]++;
                        }
                    }

                    n++;
                    nc[yi]++;
                }
                break;
        }

        update();
    }

    /**
     * Update conditional probabilities.
     */
    private void update() {
        if (!fixedPriori) {
            for (int c = 0; c < k; c++) {
                priori[c] = (nc[c] + EPSILON) / (n + k * EPSILON);
            }
        }

        switch (model) {
            case MULTINOMIAL:
            case POLYAURN:
                for (int c = 0; c < k; c++) {
                    for (int t = 0; t < p; t++) {
                        condprob[c][t] = (ntc[c][t] + sigma) / (nt[c] + sigma * p);
                    }
                }
                break;

            case BERNOULLI:
                for (int c = 0; c < k; c++) {
                    for (int t = 0; t < p; t++) {
                        condprob[c][t] = (ntc[c][t] + sigma) / (nc[c] + sigma * 2);
                    }
                }
                break;
        }
    }

    /**
     * Predict the class of an instance.
     *
     * @param x the instance to be classified.
     * @return the predicted class label.
     */
    @Override
    public int predict(int[] x) {
        return predict(x, null);
    }

    /**
     * Predict the class of an instance.
     *
     * @param x the instance to be classified.
     * @return the predicted class label. If the instance is of all zeros, return
     * returns Integer.MIN_VALUE.
     */
    @Override
    public int predict(int[] x, double[] posteriori) {
        if (!isGoodInstance(x)) {
            return Integer.MIN_VALUE;
        }

        if (posteriori == null) {
            posteriori = new double[k];
        }

        for (int i = 0; i < k; i++) {
            double logprob = Math.log(priori[i]);

            switch (model) {
                case MULTINOMIAL:
                case POLYAURN:
                    for (int j = 0; j < p; j++) {
                        if (x[j] > 0) {
                            logprob += x[j] * Math.log(condprob[i][j]);
                        }
                    }
                    break;

                case BERNOULLI:
                    for (int j = 0; j < p; j++) {
                        if (x[j] > 0) {
                            logprob += Math.log(condprob[i][j]);
                        } else {
                            logprob += Math.log(1.0 - condprob[i][j]);
                        }
                    }
                    break;
            }

            posteriori[i] = logprob;
        }

        return label(posteriori);
    }

    /** Returns the class label with maximal posteriori. */
    private int label(double[] posteriori) {
        double Z = 0.0;
        double max = MathEx.max(posteriori);
        for (int i = 0; i < k; i++) {
            posteriori[i] = Math.exp(posteriori[i] - max);
            Z += posteriori[i];
        }

        for (int i = 0; i < k; i++) {
            posteriori[i] /= Z;
        }

        return labels.label(MathEx.whichMax(posteriori));
    }

    /**
     * Return true if the instance has any positive features.
     */
    private boolean isGoodInstance(int[] x) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid vector size: %d", x.length));
        }

        boolean any = false;
        for (int xi : x) {
            if (xi > 0) {
                any = true;
                break;
            }
        }

        return any;
    }

    /**
     * In case of MULTINOMIAL and BERNOULLI, check if the instance
     * has any positive values of features.
     */
    private boolean isGoodInstance(SparseArray x) {
        return !x.isEmpty();
    }

    /**
     * Predict the class of an instance.
     *
     * @param x the instance to be classified.
     * @return the predicted class label. For MULTINOMIAL and BERNOULLI models,
     * returns -1 if the instance does not contain any feature words.
     */
    public int predict(SparseArray x) {
        return predict(x, null);
    }

    /**
     * Predict the class of an instance.
     *
     * @param x the instance to be classified.
     * @param posteriori the array to store a posteriori probabilities on output.
     * @return the predicted class label. If the instance is of all zeros, return
     * returns Integer.MIN_VALUE.
     */
    public int predict(SparseArray x, double[] posteriori) {
        if (!isGoodInstance(x)) {
            return Integer.MIN_VALUE;
        }

        if (posteriori == null) {
            posteriori = new double[k];
        }

        for (int i = 0; i < k; i++) {
            double logprob = Math.log(priori[i]);

            switch (model) {
                case MULTINOMIAL:
                case POLYAURN:
                    for (SparseArray.Entry e : x) {
                        if (e.x > 0) {
                            logprob += e.x * Math.log(condprob[i][e.i]);
                        }
                    }
                    break;

                case BERNOULLI:
                    for (SparseArray.Entry e : x) {
                        if (e.x > 0) {
                            logprob += Math.log(condprob[i][e.i]);
                        } else {
                            logprob += Math.log(1.0 - condprob[i][e.i]);
                        }
                    }
                    break;
            }

            posteriori[i] = logprob;
        }

        return label(posteriori);
    }
}
