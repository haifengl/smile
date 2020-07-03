/*******************************************************************************
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
 ******************************************************************************/
package smile.timeseries;

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.IntStream;

import smile.math.MathEx;
import smile.math.matrix.Cholesky;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;
import smile.stat.distribution.GaussianDistribution;

/**
 * An Auto-Regressive Integrated Moving-Average (ARIMA) model is a
 * generalization of an Auto-Regressive Moving-Average (ARMA) model for
 * timeseries data. This implementation only support non-seasonal cases.
 * <p>
 * The AR part of ARIMA indicates that the evolving variable of interest is
 * regressed on its own lagged (i.e., prior) values.
 * <p>
 * The MA part indicates that the regression error is actually a linear
 * combination of error terms whose values occurred contemporaneously and at
 * various times in the past.
 * <p>
 * The I (for "integrated") indicates that the data values have been replaced
 * with the difference between their values and the previous values (and this
 * differencing process may have been performed more than once).
 * <p>
 * 
 * <h2>References</h2>
 * <ol>
 * <li>E. J. Hannan and J. Rissanen, Recursive Estimation of Mixed
 * Autoregressive-Moving Average Order, Biometrika Vol. 69, No. 1 (Apr., 1982),
 * pp. 81-94</li>
 * </ol>
 * 
 * @author rayeaster
 *
 */
public class ARIMA implements Serializable {

    private static final long serialVersionUID = 2L;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ARIMA.class);

    /** parameter for Auto-Regressive process (AR) */
    private int p;

    /** parameter for Difference process (I) */
    private int d;

    /** parameter for Moving-Average process (MA) */
    private int q;

    /** original timeseries data to fit the ARIMA model */
    private double[] data;

    /** fit errors for final ARIMA model */
    private double[] errors;

    /** differenced data, one for each difference order up to d */
    private double[][] differenced;

    /** index percentage into original timeseries data for validation */
    private double validationPercentage;

    /** start index within original timeseries data for validation */
    private int validationIndex;

    /** coefficients for Auto-Regressive process (AR) */
    private double[] coeffAR;

    /** coefficients for Moving-Average process (MA) */
    private double[] coeffMA;

    /** maximum iteration for Hannan-Rissanen algorithm */
    private int maxIteration;

    /** performance indicator algorithm for model validation */
    private ModelSelectionChoice modelIndicatorChoice = ModelSelectionChoice.RMSE;

    /** used to choose best model invalidation */
    public enum ModelSelectionChoice {
        /** plain metric using Root-Mean-Square-Error */
        RMSE,
        /** Akaike information criterion */
        AIC,
        /** Bayesian information criterion */
        BIC
    }

    /** @return coefficients for Auto-Regressive process (AR) */
    public double[] getCoeffAR() {
        return coeffAR;
    }

    /** @return coefficients for Moving-Average process (MA) */
    public double[] getCoeffMA() {
        return coeffMA;
    }

    /** @return original timeseries data */
    public double[] getData() {
        return data;
    }

    /** @return differenced data */
    public double[] getDifferenced() {
        assert (differenced != null && differenced.length > 0);
        return differenced[differenced.length - 1];
    }

    /**
     * Builder for ARIMA model
     * <p>
     * For example, random walk is ARIMA(p=0,d=1,q=0)
     */
    public static class Builder {
        private int p = 0;
        private int d = 0;
        private int q = 0;
        private double[] data;
        private double validationPercentage = 0.05;
        private int maxIteration = 5;
        private ModelSelectionChoice modelIndicatorChoice = ModelSelectionChoice.RMSE;

        /**
         * init the ARIMA model with given timeseries data and validation
         * portion
         * 
         * @param data
         *            time-series data for model fit
         * @param validationPercentage
         *            validation portion within original timeseries data
         */
        public Builder(double[] data, double validationPercentage) {
            this.data = data;
            this.validationPercentage = validationPercentage;
        }

        /**
         * init the ARIMA model with given timeseries data
         * 
         * @param data
         *            time-series data for model fit
         */
        public Builder(double[] data) {
            this(data, 0.05);
        }

        /**
         * set the Auto-Regressive process parameter (AR)
         * 
         * @param p
         *            Auto-Regressive maximum degree
         */
        public Builder p(int p) {
            assert (p > 0);
            this.p = p;
            return this;
        }

        /**
         * set the Difference process parameter (I)
         * 
         * @param d
         *            Difference process parameter
         */
        public Builder d(int d) {
            assert (d >= 1 && d <= 3);
            this.d = d;
            return this;
        }

        /**
         * set the Moving-Average process parameter (MA)
         * 
         * @param p
         *            Moving-Average process maximum degree
         */
        public Builder q(int q) {
            assert (q > 0);
            this.q = q;
            return this;
        }

        /**
         * set maximum iteration for Hannan-Rissanen algorithm
         * 
         * @param maxIteration
         *            maximum iteration before convergence
         */
        public Builder maxIteration(int maxIteration) {
            if (maxIteration > 0) {
                this.maxIteration = maxIteration;
            }
            return this;
        }

        /**
         * set performance indicator algorithm for model validation
         * 
         * @param indicatorChoice
         *            model performance selection metric, default is RMSE
         */
        public Builder modelIndicatorChoice(ModelSelectionChoice indicatorChoice) {
            this.modelIndicatorChoice = indicatorChoice;
            return this;
        }

        /** instantiate ARIMA model from populated parameters and data */
        public ARIMA build() {
            if (data == null || data.length < 2) {
                throw new IllegalArgumentException(
                        "Invalid parameters:data-length=" + (data == null ? 0 : data.length));
            }
            if (validationPercentage >= 1 || validationPercentage <= 0) {
                throw new IllegalArgumentException("Invalid parameters:validationPercentage=" + validationPercentage);
            }
            return new ARIMA(data, validationPercentage, p, d, q, maxIteration, modelIndicatorChoice);
        }
    }

    /**
     * instantiate ARIMA model with given parameters and data
     * 
     * @param data
     *            original time-series data for model fit
     * @param validationPercentage
     *            index percentage into original timeseries data for validation
     * @param p
     *            parameter for Auto-Regressive process (AR)
     * @param d
     *            parameter for Difference process (I)
     * @param q
     *            parameter for Moving-Average process (MA)
     * @param maxIteration
     *            maximum iteration for Hannan-Rissanen algorithm
     * @param modelSelectionChoice
     *            performance indicator algorithm for model validation, default
     *            is RMSE
     * 
     */
    private ARIMA(double[] data, double validationPercentage, int p, int d, int q, int maxIteration,
            ModelSelectionChoice modelIndicatorChoice) {
        this.data = data;
        this.validationPercentage = validationPercentage;
        this.validationIndex = (int) (this.data.length * (1 - this.validationPercentage));
        this.p = p;
        this.d = d;
        this.q = q;
        this.maxIteration = maxIteration;
        this.modelIndicatorChoice = modelIndicatorChoice;

        if (this.d > 0) {
            this.differenced = new double[this.d][];
        }
        if (this.p > 0) {
            this.coeffAR = new double[this.p];
        }
        if (this.q > 0) {
            this.coeffMA = new double[this.q];
        }

        logger.info("instantiate ARIMA (" + this.p + "," + this.d + "," + this.q + ") with data-size=" + data.length
                + ",metric=" + modelIndicatorChoice.name());
    }

    /**
     * apply difference operation on input data
     * 
     * @return differenced data after d-order difference
     */
    public double[] difference() {
        assert (d > 0);
        assert (data != null);
        assert (differenced != null && differenced.length == d);

        if (this.differenced[d - 1] == null) {
            if (data.length < (d + 1)) {
                throw new IllegalArgumentException("Invalid parameters:data-length=" + data.length + ", d=" + d);
            }

            for (int i = 1; i <= d; i++) {
                differenced[i - 1] = new double[data.length - i];
            }

            double[] current = data;
            for (int di = 0; di < d; di++) {
                for (int i = 0; i < differenced[di].length; i++) {
                    differenced[di][i] = current[i + 1] - current[i];
                }
                current = differenced[di];
            }
        }
        return differenced[d - 1];
    }

    /**
     * predict next n timeseries data using this ARIMA model
     * 
     * @param n
     *            number of predictions to make
     * @return value predicted from this ARIMA model
     */
    public double[] predictNext(int n) {
        if (p > 0) {
            assert (coeffAR != null && coeffAR.length == p);
        }
        if (q > 0) {
            assert (coeffMA != null && coeffMA.length == q);
        }

        // get appropriate timeseries on which the prediction will be made
        double[] baseData = d > 0 ? differenced[d - 1] : data;
        double mean = MathEx.mean(baseData);
        baseData = centering(baseData, mean, false);

        // initialize finalized errors
        int originalPredSize = baseData.length;
        if (errors == null && (p > 0 || q > 0)) {
            errors = new double[originalPredSize];
            int start = Math.max(p, q);
            for (int i = start; i < originalPredSize; i++) {
                double arval = p > 0 ? getLagCombination(coeffAR, baseData, p, i) : 0;
                double maval = q > 0 ? getLagCombination(coeffMA, errors, q, i) : 0;
                errors[i] = baseData[i] - arval - maval;
            }
        }

        // allocate storage for new predictions
        double[] ret = new double[n];
        double[] differencedPred = null;
        if (p > 0) {
            differencedPred = new double[originalPredSize + n];
            System.arraycopy(baseData, 0, differencedPred, 0, originalPredSize);
        }
        double[] errorPred = null;
        if (q > 0) {
            errorPred = new double[originalPredSize + n];
            System.arraycopy(errors, 0, errorPred, 0, originalPredSize);
        }

        // calculate the predictions using ARIMA model
        for (int i = 0; i < n; i++) {
            int idx = i + originalPredSize;
            double arval = p > 0 ? getLagCombination(coeffAR, differencedPred, p, idx) : 0;
            double maval = q > 0 ? getLagCombination(coeffMA, errorPred, q, idx) : 0;
            ret[i] = arval + maval + (p == 0 && q == 0 ? getRandomNoise() : 0);
            if (p > 0) {
                differencedPred[idx] = ret[i];
            }
        }

        // since we did centering during model training
        ret = centering(ret, mean, true);
        if (d > 0) {
            ret = reverseDifference(ret);
        }

        return ret;
    }

    /**
     * reverse difference operation on given predictions, usually used to
     * convert model output to original data.
     * 
     * @param predictions
     *            values from ARIMA model output
     * @return real timeseries data converted back from model predictions
     */
    private double[] reverseDifference(double[] predictions) {
        assert (d > 0);

        double[] ret = new double[predictions.length];

        // allocate storage for new predictions in middle differenced data array
        double[] dataPred = Arrays.copyOf(data, data.length + predictions.length);
        double[][] differencedPred = null;
        if (d > 1) {
            differencedPred = new double[d - 1][];
            for (int i = d - 2; i >= 0; i--) {
                differencedPred[i] = Arrays.copyOf(differenced[i], differenced[i].length + predictions.length);
            }
        }

        // recursively reverse difference to original timeseries
        for (int predi = 0; predi < predictions.length; predi++) {
            double modelOutput = predictions[predi];
            for (int i = d - 1; i >= 0; i--) {
                double[] lastSeries = dataPred;
                int lastSeriesValidLen = data.length;
                if (i > 0) {
                    lastSeries = differencedPred[i - 1];
                    lastSeriesValidLen = differenced[i - 1].length;
                }
                int len = predi + lastSeriesValidLen;
                modelOutput += lastSeries[len - 1];
                lastSeries[len] = modelOutput;
            }
            ret[predi] = modelOutput;
        }
        return ret;
    }

    /**
     * Perform Yule-Walker algorithm to estimate the corresponding coefficients
     * of AutoRegressive process (AR)
     * 
     * @param stationaryData
     *            training data to fit high-order linear model
     * @param pSize
     *            coefficients size to fit
     * @return array of AR coefficients estimates starting with lag 1
     */
    private double[] solveYuleWalkerEquation(double[] stationaryData, int p) {

        int dataSize = stationaryData.length;
        if (dataSize < p) {
            throw new IllegalArgumentException("Invalid Yule-Walker Parameters:data-length=" + dataSize + ", p=" + p);
        }

        // prepare data to construct training matrix
        double[] r = new double[p + 1];
        r[0] = Arrays.stream(stationaryData).map(v -> Math.pow(v, 2)).sum();
        r[0] /= dataSize;
        for (int j = 1; j < r.length; j++) {
            final int rj = j;
            r[j] = IntStream.range(0, dataSize - rj).mapToDouble(i -> stationaryData[i] * stationaryData[i + rj]).sum();
            r[j] /= dataSize;
        }

        // model fit for linear combination
        DenseMatrix toeplitz = Matrix.toeplitz(Arrays.copyOfRange(r, 0, p));
        double[] y = Arrays.copyOfRange(r, 1, r.length);

        Cholesky ols = toeplitz.cholesky();
        ols.solve(y);
        assert (y != null && y.length == p);

        y = restrictCoefficients(y);

        return y;
    }

    /**
     * calculate the Auto Correlation Function (ACF) for given data array and
     * lag
     * 
     * @param data
     *            timeseries to calculate the ACF
     * @param k
     *            lag within the given timeseries data
     * @return ACF value for given timeseries and lag
     */
    public static double acf(double[] data, int k) {

        if (k == 0) {
            return 1;
        }

        double ret = 0;
        int N = data.length;
        double mean = MathEx.mean(data);
        double selfSSE = Arrays.stream(data).map(v -> Math.pow(v - mean, 2)).sum();
        double lagSSE = IntStream.range(k, N - 1).mapToDouble(i -> (data[i] - mean) * (data[i - k] - mean)).sum();
        ret = (N / (N - k)) * (lagSSE / selfSSE);
        return ret;
    }

    /**
     * calculate the Partial Auto Correlation Function (PACF) for given data
     * array and lag
     * 
     * @param data
     *            timeseries to calculate the PACF
     * @param k
     *            lag within the given timeseries data
     * @return PACF value for given timeseries and lag
     */
    public static double pacf(double[] data, int k) {
        if (k == 0) {
            return 1;
        } else if (k == 1) {
            return acf(data, k);
        }

        double[] acfs = new double[k];
        for (int i = 0; i < acfs.length; i++) {
            acfs[i] = acf(data, i);
        }

        DenseMatrix toeplitz = Matrix.toeplitz(acfs);
        double[] y = new double[k];
        y[k - 1] = acf(data, k);
        System.arraycopy(acfs, 1, y, 0, k - 1);

        Cholesky ols = toeplitz.cholesky();
        ols.solve(y);
        assert (y != null && y.length == k);

        return y[k - 1];
    }

    /**
     * restrict given coefficient array within (-1,1) to ensure convergences
     * 
     * @param y
     *            coefficients from model fit
     * @return restricted coefficients
     */
    private double[] restrictCoefficients(double[] y) {

        double highThresh = 0.95;
        double lowThresh = 0 - highThresh;

        double positiveSum = 0;
        double negativeSum = 0;

        for (int i = 0; i < y.length; i++) {
            if (y[i] > 0) {
                positiveSum += y[i];
            } else if (y[i] < 0) {
                negativeSum += y[i];
            }
        }
        if (positiveSum >= highThresh) {
            double margin = positiveSum - highThresh;
            for (int i = 0; i < y.length; i++) {
                if (y[i] > 0) {
                    y[i] -= margin * (y[i] / positiveSum);
                }
            }
        }
        if (negativeSum <= lowThresh) {
            double margin = lowThresh - negativeSum;
            for (int i = 0; i < y.length; i++) {
                if (y[i] < 0) {
                    y[i] += margin * (y[i] / negativeSum);
                }
            }
        }

        return y;
    }

    /**
     * centering given data around its mean
     * 
     * @param data
     *            array to be centered around mean
     * @param add
     *            if true, add mean, otherwise deduct mean
     * @return centered data
     */
    private double[] centering(double[] data, final boolean add) {
        return centering(data, MathEx.mean(data), add);
    }

    /**
     * centering given data around its mean
     * 
     * @param data
     *            array to be centered around mean
     * @param meanVal
     *            the mean value to be applied to data array
     * @param add
     *            if true, add mean, otherwise deduct mean
     * @return centered data
     */
    private double[] centering(double[] data, double meanVal, final boolean add) {
        final double mean = add ? meanVal : (0 - meanVal);
        double[] ret = Arrays.stream(data).map(v -> v + mean).toArray();
        return ret;
    }

    /**
     * fit AR and MA coefficients using Hannan-Rissanen algorithm
     * 
     * @param fullTraining
     *            if true, use all data for training, otherwise use only portion
     *            up to validationIndex
     */
    public double fit(boolean fullTraining) {

        // get Difference operation first
        if (d > 0) {
            difference();
        }

        if (p == 0 && q == 0) {
            // just random walk, no need to train
            return 0;
        }

        // get training data ready
        double[] baseData = d > 0 ? differenced[d - 1] : data;
        int trainingEndIndex = fullTraining ? baseData.length : validationIndex - d;
        double[] trainingData = centering(Arrays.copyOfRange(baseData, 0, trainingEndIndex), false);
        double[] errors = new double[trainingEndIndex];

        int start = Math.max(p, q);

        // step 1: Estimate a high order linear model on data form Yule-Walker
        // equation
        // step 2: Use linear model to estimate unobserved values for MA
        // item(noise) w{t}
        double[] ywcoeff = solveYuleWalkerEquation(trainingData, start);
        int m = start;
        while (m < trainingEndIndex) {
            double arval = getLagCombination(ywcoeff, trainingData, start, m);
            errors[m] = trainingData[m] - arval;
            ++m;
        }

        // step 3: Regress data{t} on data{t-1...t-p} and w{t-1...t-q}
        // step 4: Update w{t-1...t-q} to get new estimate of noise w{t}
        // and repeat step 3 until convergence
        int trainingRowSize = trainingEndIndex - start;
        int colSize = p + q;

        // prepare data to construct training matrix
        double bestModelIndicator = Double.MAX_VALUE;
        double[][] iterationData = new double[trainingRowSize][colSize];
        for (int i = 0; i < trainingRowSize; i++) {
            iterationData[i] = new double[colSize];
            if (p > 0) {
                for (int ari = 1; ari <= p; ari++) {
                    iterationData[i][ari - 1] = trainingData[start + i - ari];
                }
            }
        }

        // repeat until convergence or iteration reach limit
        double lastModelIndicator = bestModelIndicator;
        int iterations = fullTraining ? 1 : maxIteration;
        for (int iti = 0; iti < iterations; iti++) {
            // update MA (noise) items
            if (q > 0) {
                for (int i = 0; i < trainingRowSize; i++) {
                    for (int mai = 1; mai <= q; mai++) {
                        iterationData[i][mai - 1 + p] = errors[start + i - mai];
                    }
                }
            }

            // model fit for linear combination
            double[] rVector = new double[colSize];
            DenseMatrix x = Matrix.of(iterationData);
            double[] y = Arrays.copyOfRange(trainingData, start, trainingEndIndex);
            x = x.transpose();
            DenseMatrix xtx = x.aat();
            rVector = x.ax(y, rVector);
            Cholesky ols = xtx.cholesky();
            ols.solve(rVector);

            assert (rVector != null && rVector.length == colSize);
            rVector = restrictCoefficients(rVector);

            double[] arCoeff = null;
            double[] maCoeff = null;
            if (p > 0) {
                arCoeff = Arrays.copyOfRange(rVector, 0, p);
                if (fullTraining) {
                    coeffAR = arCoeff;
                }
            }
            if (q > 0) {
                maCoeff = Arrays.copyOfRange(rVector, p, colSize);
                if (fullTraining) {
                    coeffMA = maCoeff;
                }
            }

            // validation: get model indicator for parameters (p,d,q)
            if (!fullTraining) {

                double fitIndicator = calModelIndicator(arCoeff, maCoeff, start, trainingEndIndex);
                logger.info("validation metric=" + fitIndicator + " at iteration " + (iti + 1));

                if (bestModelIndicator > fitIndicator) {
                    bestModelIndicator = fitIndicator;
                }

                // update errors using new prediction from model fit
                double[] newTrainingData = new double[trainingEndIndex];
                System.arraycopy(trainingData, 0, newTrainingData, 0, start);
                for (int i = start; i < trainingEndIndex; i++) {
                    double arval = p > 0 ? getLagCombination(arCoeff, newTrainingData, p, i) : 0;
                    newTrainingData[i] = arval;
                    errors[i] = trainingData[i] - arval;
                }

                // check if get convergence
                double diff = Math.abs(lastModelIndicator - fitIndicator);
                if (diff <= 1E-7) {
                    logger.info("ARIMA reach early convergence at iteration " + (iti + 1)
                            + ", absolute difference from last iteration=" + diff);
                    break;
                }

                lastModelIndicator = fitIndicator;
            }
        }

        if (fullTraining) {
            logger.info("ARIMA find best model coefficients: " + this.toString());
        } else {
            logger.info("ARIMA find best validation indicator of " + modelIndicatorChoice.name() + "="
                    + bestModelIndicator);
        }

        return bestModelIndicator;
    }

    /** @return random noise from Gaussian(0, 1) */
    private double getRandomNoise() {
        return GaussianDistribution.getInstance().rand();
    }

    /**
     * calculate model fit indicator using validation data with AR and MA
     * coefficients.
     * 
     * @param arCoeff
     *            coefficients for AR from model fit
     * @param maCoeff
     *            coefficients for MA from model fit
     * @param trainingEndIndex
     *            index start for training data
     * @param trainingEndIndex
     *            index start for validation data
     * @return model fit indicator for given coefficients, default is RMSE
     */
    private double calModelIndicator(double[] arCoeff, double[] maCoeff, int trainingStartIndex, int trainingEndIndex) {
        double ret = 0;

        double[] baseData = d > 0 ? differenced[d - 1] : data;
        int len = baseData.length;
        double[] trainingData = centering(Arrays.copyOfRange(baseData, 0, len), false);

        double[] valErrors = new double[len];

        for (int vi = trainingStartIndex; vi < trainingEndIndex; vi++) {
            double arval = p > 0 ? getLagCombination(arCoeff, trainingData, p, vi) : 0;
            double maval = q > 0 ? getLagCombination(maCoeff, valErrors, q, vi) : 0;
            valErrors[vi] = trainingData[vi] - arval - maval;
        }

        for (int vi = trainingEndIndex; vi < len; vi++) {
            double arval = p > 0 ? getLagCombination(arCoeff, trainingData, p, vi) : 0;
            double maval = q > 0 ? getLagCombination(maCoeff, valErrors, q, vi) : 0;
            valErrors[vi] = trainingData[vi] - arval - maval;
        }

        double sse = IntStream.range(trainingEndIndex, len).mapToDouble(i -> Math.pow(valErrors[i], 2)).sum();
        int N = len - trainingEndIndex;

        switch (modelIndicatorChoice) {
            default :
            case RMSE :
                ret = Math.sqrt(sse / (double) N);
                break;
            case AIC :
                ret = N * Math.log(sse / N) + 2 * (p + q + 2);
                break;
            case BIC :
                ret = N * Math.log(sse / N) + Math.log(N) * (p + q + 2);
                break;
        }
        return ret;
    }

    /**
     * predict current value with given coefficients and lag points
     * 
     * @param coeff
     *            coefficients from model fit for linear combination
     * @param lags
     *            combination components
     * @param lagSize
     *            the lag points to be used, i.e, p for AR and q for MA
     * @param index
     *            the data point index to be predicted
     * @return prediction from lag linear combination
     */
    private double getLagCombination(double[] coeff, double[] lags, int lagSize, int index) {
        assert (coeff.length >= lagSize);
        double val = 0;
        for (int i = 1; i <= lagSize; i++) {
            val += coeff[i - 1] * lags[index - i];
        }
        return val;
    }

    /**
     * return ARIMA in formula expression
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ARIMA(").append(p).append(",").append(d).append(",").append(q).append(")");
        if (coeffAR != null && coeffAR.length > 0) {
            sb.append(":AR Coefficients(");
            for (int i = 1; i <= p; i++) {
                sb.append(coeffAR[i - 1]).append("  ");
            }
            sb.append(")    ");
        }
        if (coeffMA != null && coeffMA.length > 0) {
            sb.append(":MA Coefficients(");
            for (int i = 1; i <= q; i++) {
                sb.append(coeffMA[i - 1]).append("  ");
            }
            sb.append(")    ");
        }

        return sb.toString();
    }

}