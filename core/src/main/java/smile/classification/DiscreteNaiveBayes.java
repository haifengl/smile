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
import smile.util.IntSet;
import smile.util.SparseArray;
import smile.stat.distribution.Distribution;

import java.util.Arrays;

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
 * <li> Rennie, Jason D., et al. Tackling the poor assumptions of naive Bayes text classifiers. ICML, 2003.</li>
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
         * The document multinomial model generates one term from the
         * vocabulary in each position of the document.
         */
        MULTINOMIAL,

        /**
         * The document Bernoulli model generates an indicator for each
         * term of the vocabulary, either indicating presence of the term
         * in the document or indicating absence.
         */
        BERNOULLI,

        /**
         * The document Polya Urn model is similar to MULTINOMIAL but
         * different in the conditional probability update during learning.
         * It simply add twice for what is seen in training data instead of
         * one time.
         */
        POLYAURN,

        /**
         * Complement Naive Bayes.
         * To deal with skewed training data, CNB estimates parameters
         * of a class c using data from all classes except c. CNB’s estimates
         * may be more effective because each uses a more even amount of
         * training data per class, which will lessen the bias in the weight
         * estimates.
         */
        CNB,

        /**
         * Weight-normalized Complement Naive Bayes.
         * In practice, it is often the case that weights tend to lean toward
         * one class or the other. When the magnitude of Naive Bayes’ weight
         * vector is larger in one class than the others, the larger magnitude
         * class may be preferred. To correct for the fact that some classes
         * have greater dependencies, WCNB normalizes the weight vectors.
         */
        WCNB,

        /**
         * Transformed Weight-normalized Complement Naive Bayes. Before feeding
         * into WCNB, TWCNB transforms term frequencies including TF transform,
         * IDF transform, and length normalization. Because of IDF, TWCNB
         * supports only batch mode.
         */
        TWCNB
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
     * The total number of documents.
     */
    private int n;
    /**
     * The number of documents in each class.
     */
    private int[] nc;
    /**
     * The number of terms per class.
     */
    private int[] nt;
    /**
     * The number of each term per class.
     */
    private int[][] ntc;
    /**
     * The log conditional probabilities for document classification.
     */
    private double[][] logcondprob;
    /**
     * The class label encoder.
     */
    private IntSet labels;

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
        this(model, k, p, 1.0, IntSet.of(k));
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
    public DiscreteNaiveBayes(Model model, int k, int p, double sigma, IntSet labels) {
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
        logcondprob = new double[k][p];
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
        this(model, priori, p, 1.0, IntSet.of(priori.length));
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
    public DiscreteNaiveBayes(Model model, double[] priori, int p, double sigma, IntSet labels) {
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
        logcondprob = new double[k][p];
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

        if (model == Model.TWCNB) {
            throw new UnsupportedOperationException("TWCNB supports only batch learning");
        }

        y = labels.indexOf(y);
        switch (model) {
            case MULTINOMIAL:
            case CNB:
            case WCNB:
            case TWCNB:
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

            default:
                // we should never reach here
                throw new IllegalStateException("Unknown model: " + model);
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

        if (model == Model.TWCNB) {
            throw new UnsupportedOperationException("TWCNB supports only batch learning");
        }

        y = labels.indexOf(y);
        switch (model) {
            case MULTINOMIAL:
            case CNB:
            case WCNB:
            case TWCNB:
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

            default:
                // we should never reach here
                throw new IllegalStateException("Unknown model: " + model);
        }

        n++;
        nc[y]++;

        update();
    }

    /**
     * Batch learning of naive Bayes classifier on sequences,
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
            case CNB:
            case WCNB:
                for (int i = 0; i < x.length; i++) {
                    if (!isGoodInstance(x[i])) {
                        logger.info("Skip updating the model with a sample without any feature word");
                        continue;
                    }

                    int yi = labels.indexOf(y[i]);
                    for (int j = 0; j < p; j++) {
                        ntc[yi][j] += x[i][j];
                        nt[yi] += x[i][j];
                    }

                    n++;
                    nc[yi]++;
                }
                break;

            case TWCNB:
                // The number of documents where the term occurs.
                int[] ni = new int[p];
                // The transformed term frequency in a document.
                double[] d = new double[p];

                for (int[] doc : x) {
                    for (int i = 0; i < p; i++) {
                        if (doc[i] > 0) ni[i]++;
                    }
                }

                // The number of (good) documents.
                double N = 0;
                for (int[] xi : x) {
                    if (isGoodInstance(xi)) N++;
                }

                for (int i = 0; i < x.length; i++) {
                    int[] xi = x[i];
                    if (!isGoodInstance(xi)) continue;

                    Arrays.fill(d, 0.0);
                    for (int t = 0; t < p; t++) {
                        if (xi[t] > 0) {
                            d[t] = Math.log(1 + xi[t]) * Math.log(N / ni[t]);
                        }
                    }

                    MathEx.unitize2(d);

                    int yi = y[i];
                    for (int t = 0; t < p; t++) {
                        logcondprob[yi][t] += d[t];
                    }
                }

                double[] rsum = MathEx.rowSums(logcondprob);
                double[] csum = MathEx.colSums(logcondprob);
                double sum = MathEx.sum(csum);

                for (int c = 0; c < k; c++) {
                    for (int t = 0; t < p; t++) {
                        logcondprob[c][t] = Math.log((csum[t] - logcondprob[c][t] + sigma) / (sum - rsum[c] + sigma * p));
                    }
                }

                for (int c = 0; c < k; c++) {
                    MathEx.unitize1(logcondprob[c]);
                }
                break;

            case POLYAURN:
                for (int i = 0; i < x.length; i++) {
                    if (!isGoodInstance(x[i])) {
                        logger.info("Skip updating the model with a sample without any feature word");
                        continue;
                    }

                    int yi = labels.indexOf(y[i]);
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

                    int yi = labels.indexOf(y[i]);
                    for (int j = 0; j < p; j++) {
                        if (x[i][j] > 0) {
                            ntc[yi][j]++;
                        }
                    }

                    n++;
                    nc[yi]++;
                }
                break;

            default:
                // we should never reach here
                throw new IllegalStateException("Unknown model: " + model);
        }

        update();
    }

    /**
     * Batch learning of naive Bayes classifier on sequences,
     * which are modeled as a bag of words. Note that this method is NOT
     * applicable for naive Bayes classifier with general generation model.
     *
     * @param x training instances.
     * @param y training labels.
     */
    public void update(SparseArray[] x, int[] y) {
        switch (model) {
            case MULTINOMIAL:
            case CNB:
            case WCNB:
                for (int i = 0; i < x.length; i++) {
                    if (!isGoodInstance(x[i])) {
                        logger.info("Skip updating the model with a sample without any feature word");
                        continue;
                    }

                    int yi = labels.indexOf(y[i]);
                    for (SparseArray.Entry e : x[i]) {
                        ntc[yi][e.i] += e.x;
                        nt[yi] += e.x;
                    }

                    n++;
                    nc[yi]++;
                }
                break;

            case TWCNB:
                // The number of documents where the term occurs.
                int[] ni = new int[p];
                // The transformed term frequency in a document.
                double[] d = new double[p];

                for (SparseArray doc : x) {
                    for (SparseArray.Entry e : doc) {
                        if (e.x > 0) ni[e.i]++;
                    }
                }

                // The number of (good) documents.
                double N = 0;
                for (SparseArray xi : x) {
                    if (isGoodInstance(xi)) N++;
                }

                for (int i = 0; i < x.length; i++) {
                    SparseArray xi = x[i];
                    if (!isGoodInstance(xi)) continue;

                    Arrays.fill(d, 0.0);
                    for (SparseArray.Entry e : xi) {
                        if (e.x > 0) {
                            d[e.i] = Math.log(1 + e.x) * Math.log(N / ni[e.i]);
                        }
                    }

                    MathEx.unitize2(d);

                    int yi = y[i];
                    for (int t = 0; t < p; t++) {
                        logcondprob[yi][t] += d[t];
                    }
                }

                double[] rsum = MathEx.rowSums(logcondprob);
                double[] csum = MathEx.colSums(logcondprob);
                double sum = MathEx.sum(csum);

                for (int c = 0; c < k; c++) {
                    for (int t = 0; t < p; t++) {
                        logcondprob[c][t] = Math.log((csum[t] - logcondprob[c][t] + sigma) / (sum - rsum[c] + sigma * p));
                    }
                }

                for (int c = 0; c < k; c++) {
                    MathEx.unitize1(logcondprob[c]);
                }
                break;

            case POLYAURN:
                for (int i = 0; i < x.length; i++) {
                    if (!isGoodInstance(x[i])) {
                        logger.info("Skip updating the model with a sample without any feature word");
                        continue;
                    }

                    int yi = labels.indexOf(y[i]);
                    for (SparseArray.Entry e : x[i]) {
                        ntc[yi][e.i] += e.x * 2;
                        nt[yi] += e.x * 2;
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

                    int yi = labels.indexOf(y[i]);
                    for (SparseArray.Entry e : x[i]) {
                        if (e.x > 0) {
                            ntc[yi][e.i]++;
                        }
                    }

                    n++;
                    nc[yi]++;
                }
                break;

            default:
                // we should never reach here
                throw new IllegalStateException("Unknown model: " + model);
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
                        logcondprob[c][t] = Math.log((ntc[c][t] + sigma) / (nt[c] + sigma * p));
                    }
                }
                break;

            case BERNOULLI:
                for (int c = 0; c < k; c++) {
                    for (int t = 0; t < p; t++) {
                        logcondprob[c][t] = Math.log((ntc[c][t] + sigma) / (nc[c] + sigma * 2));
                    }
                }
                break;

            case CNB:
            case WCNB:
                long ntsum = MathEx.sum(nt);
                long[] ntcsum = MathEx.colSums(ntc);

                for (int c = 0; c < k; c++) {
                    for (int t = 0; t < p; t++) {
                        logcondprob[c][t] = Math.log((ntcsum[t] - ntc[c][t] + sigma) / (ntsum - nt[c] + sigma * p));
                    }
                }

                if (model == Model.WCNB) {
                    for (int c = 0; c < k; c++) {
                        MathEx.unitize1(logcondprob[c]);
                    }
                }
                break;


            case TWCNB:
                // nop
                break;

            default:
                // we should never reach here
                throw new IllegalStateException("Unknown model: " + model);
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
        return predict(x, new double[k]);
    }

    /**
     * Predict the class of an instance.
     *
     * @param x the instance to be classified.
     * @return the predicted class label. If the instance is of all zeros,
     *         returns Integer.MIN_VALUE.
     */
    @Override
    public int predict(int[] x, double[] posteriori) {
        if (!isGoodInstance(x)) {
            return Integer.MIN_VALUE;
        }

        for (int i = 0; i < k; i++) {
            double logprob;

            switch (model) {
                case MULTINOMIAL:
                case POLYAURN:
                    logprob = Math.log(priori[i]);
                    for (int j = 0; j < p; j++) {
                        if (x[j] > 0) {
                            logprob += x[j] * logcondprob[i][j];
                        }
                    }
                    break;

                case BERNOULLI:
                    logprob = Math.log(priori[i]);
                    for (int j = 0; j < p; j++) {
                        if (x[j] > 0) {
                            logprob += logcondprob[i][j];
                        } else {
                            logprob += Math.log(1.0 - Math.exp(logcondprob[i][j]));
                        }
                    }
                    break;

                case CNB:
                case WCNB:
                case TWCNB:
                    logprob = 0.0;
                    for (int j = 0; j < p; j++) {
                        if (x[j] > 0) {
                            logprob -= x[j] * logcondprob[i][j];
                        }
                    }
                    break;

                default:
                    // we should never reach here
                    throw new IllegalStateException("Unknown model: " + model);
            }

            posteriori[i] = logprob;
        }

        MathEx.softmax(posteriori);
        return MathEx.whichMax(posteriori);
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
        return predict(x, new double[k]);
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

        for (int i = 0; i < k; i++) {
            double logprob;

            switch (model) {
                case MULTINOMIAL:
                case POLYAURN:
                    logprob = Math.log(priori[i]);
                    for (SparseArray.Entry e : x) {
                        if (e.x > 0) {
                            logprob += e.x * logcondprob[i][e.i];
                        }
                    }
                    break;

                case BERNOULLI:
                    logprob = Math.log(priori[i]);
                    for (SparseArray.Entry e : x) {
                        if (e.x > 0) {
                            logprob += logcondprob[i][e.i];
                        } else {
                            logprob += Math.log(1.0 - Math.exp(logcondprob[i][e.i]));
                        }
                    }
                    break;

                case CNB:
                case WCNB:
                case TWCNB:
                    logprob = 0.0;
                    for (SparseArray.Entry e : x) {
                        if (e.x > 0) {
                            logprob -= e.x * logcondprob[i][e.i];
                        }
                    }
                    break;

                default:
                    // we should never reach here
                    throw new IllegalStateException("Unknown model: " + model);
            }

            posteriori[i] = logprob;
        }

        MathEx.softmax(posteriori);
        return labels.valueOf(MathEx.whichMax(posteriori));
    }
}
