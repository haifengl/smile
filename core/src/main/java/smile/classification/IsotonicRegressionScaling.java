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

import java.io.Serializable;
import java.util.Arrays;

import smile.sort.QuickSort;

/**
 * An alternative approach to probability calibration is to fit an isotonic
 * regression model to an ill-calibrated probability model. This has been shown
 * to work better than Platt scaling, in particular when enough training data is
 * available.
 * 
 * <h2>References</h2>
 * <ol>
 * <li>Niculescu-Mizil, Alexandru; Caruana, Rich: Predicting Good Probabilities
 * With Supervised Learning, 2005.</li>
 * </ol>
 *
 * @author rayeaster
 */
public class IsotonicRegressionScaling implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private double trainMixScore = Double.NaN;
    private double trainMaxScore = Double.NaN;

    /** setep-wise constant functions */
    private static class StepwiseConsFunc {
        double lowScore;
        double highScore;
        double val;
        int weight = 1;

        StepwiseConsFunc next;
        StepwiseConsFunc previous;

        StepwiseConsFunc(double lowScore, double highScore, double val) {
            this.lowScore = lowScore;
            this.highScore = highScore;
            this.val = val;
        }

        void setNext(StepwiseConsFunc next) {
            this.next = next;
        }

        void setPrevious(StepwiseConsFunc previous) {
            this.previous = previous;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        boolean matchValue(double score) {
            if ((score - lowScore) > 0.0 && (highScore - score) >= 0.0) {
                return true;
            } else {
                return false;
            }
        }

        double getVal() {
            return this.val;
        }
    }

    private StepwiseConsFunc startConstantFuncs;

    /**
     * Trains the Isotonic Regression scaling.
     * 
     * @param scores
     *            The predicted scores.
     * @param y
     *            The training labels.
     */
    public IsotonicRegressionScaling(double[] scores, int[] y) {
        initStepwiseConstFuncs(scores, y);

        StepwiseConsFunc current = startConstantFuncs.next;
        StepwiseConsFunc last = startConstantFuncs;
        
        while (current != null) {
            StepwiseConsFunc previous = current.previous;
            while (previous != null) {
                double pv = previous.getVal();
                double cv = current.getVal();
                double diff = pv - cv;
                if (diff >= 0.0) {
                    int weight = previous.getWeight() + current.getWeight();
                    double val = (previous.getWeight() * previous.getVal() + current.getWeight() * current.getVal())
                            / weight;
                    StepwiseConsFunc newFunc = new StepwiseConsFunc(previous.lowScore, current.highScore, val);
                    newFunc.setWeight(weight);

                    newFunc.next = current.next;
                    if (current.next != null) {
                        newFunc.next.previous = newFunc;
                        current.next = null;
                    }

                    newFunc.previous = previous.previous;
                    if (previous.previous != null) {
                        newFunc.previous.next = newFunc;
                        previous.previous = null;
                    }
                    
                    current = newFunc;
                    previous = current.previous;
                    if (previous == null) {
                        startConstantFuncs = current;
                    }
                } else {
                    previous = previous.previous;
                }
            }
            last = current;
            current = current.next;
        }
        
        double minScore = startConstantFuncs.lowScore;
        assert((minScore - trainMixScore) == 0.0);
        
        double maxScore = last.highScore;
        assert((maxScore - trainMaxScore) == 0.0);
    }

    private void initStepwiseConstFuncs(double[] scores, int[] y) {
        double[] sortedScores = Arrays.copyOf(scores, scores.length);
        int[] sortedY = Arrays.copyOf(y, y.length);

        QuickSort.sort(sortedScores, sortedY, sortedScores.length);
        trainMixScore = sortedScores[0];
        trainMaxScore = sortedScores[sortedScores.length - 1];

        StepwiseConsFunc previous = new StepwiseConsFunc(sortedScores[0], sortedScores[0], (double) sortedY[0]);
        startConstantFuncs = previous;

        for (int i = 1; i < sortedScores.length; i++) {
            StepwiseConsFunc init = new StepwiseConsFunc(sortedScores[i], sortedScores[i], (double) sortedY[i]);
            init.setPrevious(previous);
            previous.setNext(init);
            previous = init;
        }
    }

    /**
     * Returns the posterior probability estimate P(y = 1 | x).
     *
     * @param y
     *            the binary classifier output score.
     * @return the estimated probability.
     */
    public double predict(double y) {
        double ret = Double.NaN;
        StepwiseConsFunc func = startConstantFuncs;
        
        if(startConstantFuncs != null && (y - func.lowScore) == 0.0) {
            return func.getVal();
        }
        
        while (func != null) {
            if (func.matchValue(y)) {
                ret = func.getVal();
                break;
            }
            func = func.next;
        }
        
        if (ret == Double.NaN) {
            throw new IllegalArgumentException("fail to get the posteriori probability for given prediction: " + y);
        }
        
        return ret;
    }

    /**
     * Estimates the multiclass probabilies.
     */
    public static void multiclass(int k, double[][] r, double[] p) {
        PlattScaling.multiclass(k, r, p);
    }
    
    @Override
    public String toString() {        
        StringBuilder sb = new StringBuilder();
        StepwiseConsFunc iter = startConstantFuncs;
        while(iter != null) {
            sb.append("[" + iter.lowScore + "," + iter.highScore + "]=" + iter.val + ",");            
            iter = iter.next;
        }
        if(sb.length() > 0) {
           sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

}
