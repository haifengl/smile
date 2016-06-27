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

package smile.stat.distribution;

import java.util.List;
import java.util.ArrayList;
import smile.math.Math;

/**
 * Finite univariate Gaussian mixture. The EM algorithm is provide to learned
 * the mixture model from data. BIC score is employed to estimate the number
 * of components.
 *
 * @author Haifeng Li
 */
public class GaussianMixture extends ExponentialFamilyMixture {
    /**
     * Constructor.
     * @param mixture a list of multivariate Gaussian distributions.
     */
    public GaussianMixture(List<Component> mixture) {
        super(mixture);
    }

    /**
     * Constructor. The Gaussian mixture model will be learned from the given data
     * with the EM algorithm.
     * @param data the training data.
     * @param k the number of components.
     */
    public GaussianMixture(double[] data, int k) {
        if (k < 2)
            throw new IllegalArgumentException("Invalid number of components in the mixture.");

        double min = Math.min(data);
        double max = Math.max(data);
        double step = (max - min) / (k+1);
        
        for (int i = 0; i < k; i++) {
            Component c = new Component();
            c.priori = 1.0 / k;
            c.distribution = new GaussianDistribution(min+=step, step);
            components.add(c);
        }

        EM(components, data);
    }

    /**
     * Constructor. The Gaussian mixture model will be learned from the given data
     * with the EM algorithm. The number of components will be selected by BIC.
     * @param data the training data.
     */
    @SuppressWarnings("unchecked")
    public GaussianMixture(double[] data) {
        if (data.length < 20)
            throw new IllegalArgumentException("Too few samples.");
        
        ArrayList<Component> mixture = new ArrayList<>();
        Component c = new Component();
        c.priori = 1.0;
        c.distribution = new GaussianDistribution(data);
        mixture.add(c);

        int freedom = 0;
        for (int i = 0; i < mixture.size(); i++)
            freedom += mixture.get(i).distribution.npara();

        double bic = 0.0;
        for (double x : data) {
            double p = c.distribution.p(x);
            if (p > 0) bic += Math.log(p);
        }
        bic -= 0.5 * freedom * Math.log(data.length);

        double b = Double.NEGATIVE_INFINITY;
        while (bic > b) {
            b = bic;
            components = (ArrayList<Component>) mixture.clone();

            split(mixture);
            bic = EM(mixture, data);

            freedom = 0;
            for (int i = 0; i < mixture.size(); i++)
                freedom += mixture.get(i).distribution.npara();

            bic -= 0.5 * freedom * Math.log(data.length);
        }
    }

    /**
     * Split the most heterogeneous cluster along its main direction (eigenvector).
     */
    private void split(List<Component> mixture) {
        // Find most dispersive cluster (biggest sigma)
        Component componentToSplit = null;

        double maxSigma = 0.0;
        for (Component c : mixture) {
            if (c.distribution.sd() > maxSigma) {
                maxSigma = c.distribution.sd();
                componentToSplit = c;
            }
        }

        // Splits the component
        double delta = componentToSplit.distribution.sd();
        double mu = componentToSplit.distribution.mean();
        
        Component c = new Component();
        c.priori = componentToSplit.priori / 2;
        c.distribution = new GaussianDistribution(mu + delta/2, delta);
        mixture.add(c);
        
        c = new Component();
        c.priori = componentToSplit.priori / 2;
        c.distribution = new GaussianDistribution(mu - delta/2, delta);
        mixture.add(c);

        mixture.remove(componentToSplit);
    }
}
