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

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import smile.data.BitcoinPrice;
import smile.data.DailyFemaleBirth;
import smile.data.SunspotNumber;
import smile.math.MathEx;

/**
 *
 * @author rayeaster
 */
public class ARIMATest {

    public ARIMATest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testFemaleBirth() {

        double validataionRatio = 0.01;
        
        ARIMA.ModelSelectionChoice modelSelection = ARIMA.ModelSelectionChoice.RMSE;
        fitARIMA(0, 1, 0, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        double metric712 = fitARIMA(7, 1, 2, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        double metric012 = fitARIMA(0, 1, 2, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        double metric710 = fitARIMA(7, 1, 0, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        double metric101 = fitARIMA(1, 0, 1, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        double metric614 = fitARIMA(6, 0, 4, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        double[] allMetrics = new double[]{metric712, metric012, metric710, metric101, metric614};
        assertTrue(Math.abs(metric012 - MathEx.min(allMetrics)) < 1E-2);        

        modelSelection = ARIMA.ModelSelectionChoice.AIC;
        metric712 = fitARIMA(7, 1, 2, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        metric012 = fitARIMA(0, 1, 2, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        metric710 = fitARIMA(7, 1, 0, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        metric101 = fitARIMA(1, 0, 1, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        metric614 = fitARIMA(6, 0, 4, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        allMetrics = new double[]{metric712, metric012, metric710, metric101, metric614};
        assertTrue(Math.abs(metric012 - MathEx.min(allMetrics)) < 1E-2);        

        modelSelection = ARIMA.ModelSelectionChoice.BIC;
        metric712 = fitARIMA(7, 1, 2, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        metric012 = fitARIMA(0, 1, 2, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        metric710 = fitARIMA(7, 1, 0, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        metric101 = fitARIMA(1, 0, 1, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        metric614 = fitARIMA(6, 0, 4, validataionRatio, modelSelection, DailyFemaleBirth.timeseries);
        allMetrics = new double[]{metric712, metric012, metric710, metric101, metric614};
        assertTrue(Math.abs(metric012 - MathEx.min(allMetrics)) < 1E-2);
    }

    @Test
    public void testBitcoinPrice() {

        double validataionRatio = 0.01;
        
        ARIMA.ModelSelectionChoice modelSelection = ARIMA.ModelSelectionChoice.RMSE;
        double metric818 = fitARIMA(8, 1, 8, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        double metric413 = fitARIMA(4, 1, 3, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        double metric211 = fitARIMA(2, 1, 1, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        double metric110 = fitARIMA(1, 1, 0, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        double metric411 = fitARIMA(4, 1, 1, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        double[] allMetrics = new double[]{metric818, metric413, metric211, metric110, metric411};
        double min = MathEx.min(allMetrics);
        assertTrue(Math.abs(metric413 - min) < 1E-2);
        
        modelSelection = ARIMA.ModelSelectionChoice.AIC;
        metric818 = fitARIMA(8, 1, 8, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        metric413 = fitARIMA(4, 1, 3, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        metric211 = fitARIMA(2, 1, 1, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        metric110 = fitARIMA(1, 1, 0, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        metric411 = fitARIMA(4, 1, 1, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        allMetrics = new double[]{metric818, metric413, metric211, metric110, metric411};
        assertTrue(Math.abs(metric110 - MathEx.min(allMetrics)) < 1E-2);
        
        modelSelection = ARIMA.ModelSelectionChoice.BIC;
        metric818 = fitARIMA(8, 1, 8, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        metric413 = fitARIMA(4, 1, 3, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        metric211 = fitARIMA(2, 1, 1, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        metric110 = fitARIMA(1, 1, 0, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        metric411 = fitARIMA(4, 1, 1, validataionRatio, modelSelection, BitcoinPrice.timeseries);
        allMetrics = new double[]{metric818, metric413, metric211, metric110, metric411};
        assertTrue(Math.abs(metric110 - MathEx.min(allMetrics)) < 1E-2);
    }
    
    @Test
    public void testSunspot() {

        double validataionRatio = 0.01;
        
        ARIMA.ModelSelectionChoice modelSelection = ARIMA.ModelSelectionChoice.RMSE;
        double metric212 = fitARIMA(2, 1, 2, validataionRatio, modelSelection, SunspotNumber.timeseries);
        double metric514 = fitARIMA(5, 1, 4, validataionRatio, modelSelection, SunspotNumber.timeseries);
        double metric505 = fitARIMA(5, 0, 5, validataionRatio, modelSelection, SunspotNumber.timeseries);
        double[] allMetrics = new double[]{metric212, metric514, metric505};
        assertTrue(Math.abs(metric505 - MathEx.min(allMetrics)) < 1E-2);        

        modelSelection = ARIMA.ModelSelectionChoice.AIC;
        metric212 = fitARIMA(2, 1, 2, validataionRatio, modelSelection, SunspotNumber.timeseries);
        metric514 = fitARIMA(5, 1, 4, validataionRatio, modelSelection, SunspotNumber.timeseries);
        metric505 = fitARIMA(5, 0, 5, validataionRatio, modelSelection, SunspotNumber.timeseries);
        allMetrics = new double[]{metric212, metric514, metric505};
        assertTrue(Math.abs(metric212 - MathEx.min(allMetrics)) < 1E-2);              

        modelSelection = ARIMA.ModelSelectionChoice.BIC;
        metric212 = fitARIMA(2, 1, 2, validataionRatio, modelSelection, SunspotNumber.timeseries);
        metric514 = fitARIMA(5, 1, 4, validataionRatio, modelSelection, SunspotNumber.timeseries);
        metric505 = fitARIMA(5, 0, 5, validataionRatio, modelSelection, SunspotNumber.timeseries);
        allMetrics = new double[]{metric212, metric514, metric505};
        assertTrue(Math.abs(metric212 - MathEx.min(allMetrics)) < 1E-2);
    }

    private double fitARIMA(int p, int d, int q, double validataionRatio, ARIMA.ModelSelectionChoice modelSelection,
            double[] timeseries) {

        System.out.println("-----------------------------------------------------------");
        System.out.println("Start testing with (" + p + ", " + d + ", " + q + ")......");
        System.out.println("-----------------------------------------------------------");

        // do validations
        ARIMA.Builder ab = new ARIMA.Builder(timeseries, validataionRatio).modelIndicatorChoice(modelSelection);
        if (p > 0) {
            ab = ab.p(p);
        }
        if (d > 0) {
            ab = ab.d(d);
        }
        if (q > 0) {
            ab = ab.q(q);
        }
        ARIMA arima = ab.build();
        double ret = arima.fit(false);

        // finalized model fit
        ARIMA.Builder finalizedModelBuilder = new ARIMA.Builder(timeseries, validataionRatio)
                .modelIndicatorChoice(modelSelection);
        if (p > 0) {
            finalizedModelBuilder = finalizedModelBuilder.p(p);
        }
        if (d > 0) {
            finalizedModelBuilder = finalizedModelBuilder.d(d);
        }
        if (q > 0) {
            finalizedModelBuilder = finalizedModelBuilder.q(q);
        }
        ARIMA finalizedModel = finalizedModelBuilder.build();
        finalizedModel.fit(true);

        // prediction using finalized model
        int next = 3;
        double[] predictions = finalizedModel.predictNext(next);
        for (int i = 0; i < predictions.length; i++) {
            double pred = predictions[i];
            System.out.println("next " + (i + 1) + " prediction=" + pred);
        }

        return ret;
    }

}
