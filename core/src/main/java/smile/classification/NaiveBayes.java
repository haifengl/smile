/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.classification;

import smile.stat.distribution.Distribution;

import java.io.Serializable;
import smile.math.SparseArray;

/**
 * Naive Bayes classifier. A naive Bayes classifier is a simple probabilistic
 * classifier based on applying Bayes' theorem with strong (naive) independence
 * assumptions. Depending on the precise nature of the probability model, naive
 * Bayes classifiers can be trained very efficiently in a supervised learning
 * setting.
 * <p>
 * In spite of their naive design and apparently over-simplified assumptions,
 * naive Bayes classifiers have worked quite well in many complex real-world
 * situations and are very popular in Natural Language Processing (NLP).
 * <p>
 * For a general purpose naive Bayes classifier without any assumptions
 * about the underlying distribution of each variable, we don't provide
 * a learning method to infer the variable distributions from the training data.
 * Instead, the users can fit any appropriate distributions on the data by
 * themselves with various {@link Distribution} classes. Although the {@link #predict}
 * method takes an array of double values as a general form of independent variables,
 * the users are free to use any discrete distributions to model categorical or
 * ordinal random variables.
 * <p>
 * For document classification in NLP, there are two different ways we can set
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
 *
 * @see Distribution
 * @see LDA
 * @see QDA
 * @see RDA
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Christopher D. Manning, Prabhakar Raghavan, and Hinrich Schutze. Introduction to Information Retrieval, Chapter 13, 2009.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class NaiveBayes implements OnlineClassifier<double[]>, SoftClassifier<double[]>, Serializable {
    private static final long serialVersionUID = 1L;

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
         * The general model is for a set of independent variables
         * with any kind of underlying distributions. The users have to provide
         * estimated distribution for each independent variables.
         */
        GENERAL,
        
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
        BERNOULLI
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
     * The conditional distribution for general purpose naive Bayes classifier.
     */
    private Distribution[][] prob;
    /**
     * Amount of add-k smoothing of evidence. By default, we use add-one or
     * Laplace smoothing, which simply adds one to each count to eliminate zeros.
     * Add-one smoothing can be interpreted as a uniform prior (each term occurs
     * once for each class) that is then updated as evidence from the training
     * data comes in.
     */
    private double sigma;
    /**
     * True if the priori probabilities are pre defined by user.
     */
    private boolean predefinedPriori;
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
     * Trainer for naive Bayes classifier for document classification.
     */
    public static class Trainer extends ClassifierTrainer<double[]> {

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
         * A priori probabilities of each class.
         */
        private double[] priori;
        /**
         * Amount of add-k smoothing of evidence.
         */
        private double sigma = 1.0;

        /**
         * Constructor.
         * 
         * @param model the generation model of naive Bayes classifier.
         * @param k the number of classes.
         * @param p the dimensionality of input space.
         */
        public Trainer(Model model, int k, int p) {
            if (k < 2) {
                throw new IllegalArgumentException("Invalid number of classes: " + k);
            }

            if (p <= 0) {
                throw new IllegalArgumentException("Invalid dimension: " + p);
            }

            this.model = model;
            this.k = k;
            this.p = p;
        }
        
        /**
         * Constructor.
         * 
         * @param model the generation model of naive Bayes classifier.
         * @param priori a priori probabilities of each class.
         * @param p the dimensionality of input space.
         */
        public Trainer(Model model, double[] priori, int p) {
            if (p <= 0) {
                throw new IllegalArgumentException("Invalid dimension: " + p);
            }

            if (priori.length < 2) {
                throw new IllegalArgumentException("Invalid number of classes: " + priori.length);
            }

            double sum = 0.0;
            for (double prob : priori) {
                if (prob <= 0.0 || prob >= 1.0) {
                    throw new IllegalArgumentException("Invalid priori probability: " + prob);
                }
                sum += prob;
            }
            
            if (Math.abs(sum - 1.0) > 1E-10) {
                throw new IllegalArgumentException("The sum of priori probabilities is not one: " + sum);                
            }

            this.model = model;
            this.priori = priori;
            this.k = priori.length;
            this.p = p;
        }
        
        /**
         * Sets a priori probabilities of each class.
         * 
         * @param priori a priori probabilities of each class.
         */
        public Trainer setPriori(double[] priori) {
            this.priori = priori;
            return this;
        }
        
        /**
         * Sets add-k prior count of terms for smoothing.
         * 
         * @param sigma the prior count of add-k smoothing of evidence.
         */
        public Trainer setSmooth(double sigma) {
            if (sigma < 0) {
                throw new IllegalArgumentException("Invalid add-k smoothing parameter: " + sigma);
            }

            this.sigma = sigma;
            return this;
        }
        
        @Override
        public NaiveBayes train(double[][] x, int[] y) {
            NaiveBayes bayes = priori == null ? new NaiveBayes(model, k, p, sigma) : new NaiveBayes(model, priori, p, sigma);
            bayes.learn(x, y);
            return bayes;
        }
    }
    
    /**
     * Constructor of general naive Bayes classifier.
     * 
     * @param priori the priori probability of each class.
     * @param condprob the conditional distribution of each variable in
     * each class. In particular, condprob[i][j] is the conditional
     * distribution P(x<sub>j</sub> | class i).
     */
    public NaiveBayes(double[] priori, Distribution[][] condprob) {
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

        if (Math.abs(sum - 1.0) > 1E-10) {
            throw new IllegalArgumentException("The sum of priori probabilities is not one: " + sum);
        }

        this.model = Model.GENERAL;
        this.k = priori.length;
        this.p = condprob[0].length;
        this.priori = priori;
        this.prob = condprob;
        predefinedPriori = true;
    }

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
    public NaiveBayes(Model model, int k, int p) {
        this(model, k, p, 1.0);
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
     */
    public NaiveBayes(Model model, int k, int p, double sigma) {
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

        predefinedPriori = false;
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
    public NaiveBayes(Model model, double[] priori, int p) {
        this(model, priori, p, 1.0);
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
    public NaiveBayes(Model model, double[] priori, int p, double sigma) {
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

        if (Math.abs(sum - 1.0) > 1E-10) {
            throw new IllegalArgumentException("The sum of priori probabilities is not one: " + sum);
        }

        this.model = model;
        this.k = priori.length;
        this.p = p;
        this.sigma = sigma;

        this.priori = priori;
        predefinedPriori = true;

        sum = 0.0;
        for (int i = 0; i < k; i++) {
            if (priori[i] <= 0.0 || priori[i] >= 1.0) {
                throw new IllegalArgumentException("Invalid priori probability: " + priori[i]);
            }

            sum += priori[i];
        }

        if (Math.abs(1.0 - sum) > 1E-5) {
            throw new IllegalArgumentException("Priori probabilities don't sum to 1.");
        }

        n = 0;
        nc = new int[k];
        nt = new int[k];
        ntc = new int[k][p];
        condprob = new double[k][p];
    }

    /**
     * Returns a priori probabilities.
     */
    public double[] getPriori() {
        return priori;
    }

    /**
     * Online learning of naive Bayes classifier on a sequence,
     * which is modeled as a bag of words. Note that this method is NOT
     * applicable for naive Bayes classifier with general generation model.
     * 
     * @param x training instance.
     * @param y training label in [0, k), where k is the number of classes.
     */
    @Override
    public void learn(double[] x, int y) {
        if (model == Model.GENERAL) {
            throw new UnsupportedOperationException("General-mode Naive Bayes classifier doesn't support online learning.");
        }

        if (x.length != p) {
            throw new IllegalArgumentException("Invalid input vector size: " + x.length);
        }
        
        if (model == Model.MULTINOMIAL) {
            for (int i = 0; i < p; i++) {
                ntc[y][i] += x[i];
                nt[y] += x[i];
            }
        } else {
            for (int i = 0; i < p; i++) {
                if (x[i] > 0) {
                    ntc[y][i]++;
                }
            }
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
     * @param y training label in [0, k), where k is the number of classes.
     */
    public void learn(SparseArray x, int y) {
        if (model == Model.GENERAL) {
            throw new UnsupportedOperationException("General-mode Naive Bayes classifier doesn't support online learning.");
        }

        if (model == Model.MULTINOMIAL) {
            for (SparseArray.Entry e : x) {
                ntc[y][e.i] += e.x;
                nt[y] += e.x;
            }
        } else {
            for (SparseArray.Entry e : x) {
                if (e.x > 0) {
                    ntc[y][e.i]++;
                }
            }
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
     * @param y training labels in [0, k), where k is the number of classes.
     */
    public void learn(double[][] x, int[] y) {
        if (model == Model.GENERAL) {
            throw new UnsupportedOperationException("General-mode Naive Bayes classifier doesn't support online learning.");
        }

        if (model == Model.MULTINOMIAL) {
            for (int i = 0; i < x.length; i++) {
                if (x[i].length != p) {
                    throw new IllegalArgumentException("Invalid input vector size: " + x[i].length);
                }

                for (int j = 0; j < p; j++) {
                    ntc[y[i]][j] += x[i][j];
                    nt[y[i]] += x[i][j];
                }

                n++;
                nc[y[i]]++;
            }
        } else {
            for (int i = 0; i < x.length; i++) {
                if (x[i].length != p) {
                    throw new IllegalArgumentException("Invalid input vector size: " + x[i].length);
                }

                for (int j = 0; j < p; j++) {
                    if (x[i][j] > 0) {
                        ntc[y[i]][j]++;
                    }
                }

                n++;
                nc[y[i]]++;
            }
        }

        update();
    }

    /**
     * Update conditional probabilities.
     */
    private void update() {
        if (!predefinedPriori) {
            for (int c = 0; c < k; c++) {
                priori[c] = (nc[c] + EPSILON) / (n + k * EPSILON);
            }
        }

        if (model == Model.MULTINOMIAL) {
            for (int c = 0; c < k; c++) {
                for (int t = 0; t < p; t++) {
                    condprob[c][t] = (ntc[c][t] + sigma) / (nt[c] + sigma * p);
                }
            }
        } else {
            for (int c = 0; c < k; c++) {
                for (int t = 0; t < p; t++) {
                    condprob[c][t] = (ntc[c][t] + sigma) / (nc[c] + sigma * 2);
                }
            }
        }
    }

    /**
     * Predict the class of an instance.
     * 
     * @param x the instance to be classified.
     * @return the predicted class label. For MULTINOMIAL and BERNOULLI models,
     * returns -1 if the instance does not contain any feature words.
     */
    @Override
    public int predict(double[] x) {
        return predict(x, null);
    }

    /**
     * Predict the class of an instance.
     * 
     * @param x the instance to be classified.
     * @param posteriori the array to store a posteriori probabilities on output.
     * @return the predicted class label. For MULTINOMIAL and BERNOULLI models,
     * returns -1 if the instance does not contain any feature words.
     */
    @Override
    public int predict(double[] x, double[] posteriori) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d", x.length));
        }

        if (posteriori != null && posteriori.length != k) {
            throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, k));
        }

        int label = -1;
        double max = Double.NEGATIVE_INFINITY;
        boolean any = model == Model.GENERAL ? true : false;
        for (int i = 0; i < k; i++) {
            double logprob = Math.log(priori[i]);

            for (int j = 0; j < p; j++) {
                switch (model) {
                    case GENERAL:
                        logprob += prob[i][j].logp(x[j]);
                        break;
                    case MULTINOMIAL:
                        if (x[j] > 0) {
                            logprob += x[j] * Math.log(condprob[i][j]);
                            any = true;
                        }
                        break;
                    case BERNOULLI:
                        if (x[j] > 0) {
                            logprob += Math.log(condprob[i][j]);
                            any = true;
                        } else {
                            logprob += Math.log(1.0 - condprob[i][j]);
                        }
                        break;
                }
            }

            if (logprob > max && any) {
                max = logprob;
                label = i;
            }

            if (posteriori != null) {
                posteriori[i] = logprob;
            }
        }

        if (posteriori != null && any) {
            double Z = 0.0;
            for (int i = 0; i < k; i++) {
                posteriori[i] = Math.exp(posteriori[i] - max);
                Z += posteriori[i];
            }

            for (int i = 0; i < k; i++) {
                posteriori[i] /= Z;
            }
        }

        return label;
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
     * @return the predicted class label. For MULTINOMIAL and BERNOULLI models,
     * returns -1 if the instance does not contain any feature words.
     */
    public int predict(SparseArray x, double[] posteriori) {
        if (posteriori != null && posteriori.length != k) {
            throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, k));
        }

        int label = -1;
        double max = Double.NEGATIVE_INFINITY;
        boolean any = model == Model.GENERAL ? true : false;
        for (int i = 0; i < k; i++) {
            double logprob = Math.log(priori[i]);

            for (SparseArray.Entry e : x) {
                switch (model) {
                    case GENERAL:
                        logprob += prob[i][e.i].logp(e.x);
                        break;
                    case MULTINOMIAL:
                        if (e.x > 0) {
                            logprob += e.x * Math.log(condprob[i][e.i]);
                            any = true;
                        }
                        break;
                    case BERNOULLI:
                        if (e.x > 0) {
                            logprob += Math.log(condprob[i][e.i]);
                            any = true;
                        } else {
                            logprob += Math.log(1.0 - condprob[i][e.i]);
                        }
                        break;
                }
            }

            if (logprob > max && any) {
                max = logprob;
                label = i;
            }

            if (posteriori != null) {
                posteriori[i] = logprob;
            }
        }

        if (posteriori != null && any) {
            double Z = 0.0;
            for (int i = 0; i < k; i++) {
                posteriori[i] = Math.exp(posteriori[i] - max);
                Z += posteriori[i];
            }

            for (int i = 0; i < k; i++) {
                posteriori[i] /= Z;
            }
        }

        return label;
    }
}
